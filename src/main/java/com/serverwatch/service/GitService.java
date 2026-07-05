package com.serverwatch.service;

import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.dto.*;
import com.serverwatch.service.RepoRegistry.RepoConfig;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Core service for Git repository management using Eclipse JGit.
 *
 * <p>Safety guarantees:
 * <ul>
 *   <li>All repo paths are validated against {@code serverwatch.git.base-path}
 *       to prevent path-traversal attacks.</li>
 *   <li>Directory names are sanitized (alphanumeric + hyphen + underscore).</li>
 *   <li>Per-repo {@link ReentrantLock}s serialise write operations (pull, push,
 *       checkout, branch creation/deletion) so concurrent requests cannot
 *       corrupt a repository.</li>
 *   <li>Diff output is capped at 50 KB per file and 500 KB total.</li>
 * </ul>
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    /** Maximum unified-diff bytes per individual file (50 KB). */
    private static final int MAX_FILE_DIFF_BYTES = 50 * 1024;

    /** Maximum total diff bytes across all files in one commit (500 KB). */
    private static final int MAX_TOTAL_DIFF_BYTES = 500 * 1024;

    /**
     * Only calculate per-commit diff stats (insertions/deletions) for commits
     * up to this position in a log request — the operation is O(n) per commit.
     */
    private static final int STATS_COMMIT_LIMIT = 50;

    /** Allowed characters for sanitized directory names. */
    private static final Pattern SAFE_NAME = Pattern.compile("[^a-zA-Z0-9_\\-]");

    private final RepoRegistry repoRegistry;
    private final Path basePath;

    /** Per-repo write locks. Keys are repository IDs. */
    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();

    public GitService(RepoRegistry repoRegistry, ServerWatchProperties properties) {
        this.repoRegistry = repoRegistry;
        this.basePath     = Path.of(properties.getGit().getBasePath()).toAbsolutePath().normalize();
    }

    // ── Repo Management ───────────────────────────────────────────────────────

    /**
     * Clones a remote repository into {@code {base-path}/{sanitized-name}}.
     *
     * @param remoteUrl remote Git URL (HTTPS or SSH)
     * @param name      human-readable display name; also used for the directory
     * @param branch    branch to clone (defaults to {@code "main"})
     * @return DTO with the newly cloned repository information
     * @throws IllegalArgumentException if the target directory already exists
     * @throws IllegalStateException    if the clone fails
     */
    public GitRepoDTO cloneRepository(String remoteUrl, String name, String branch) {
        String safeName  = sanitizeName(name);
        Path   targetDir = basePath.resolve(safeName);

        if (Files.exists(targetDir)) {
            throw new IllegalArgumentException(
                    "Directory already exists: " + targetDir + ". Choose a different name.");
        }

        try {
            log.info("Cloning {} into {}", remoteUrl, targetDir);
            Git.cloneRepository()
               .setURI(remoteUrl)
               .setDirectory(targetDir.toFile())
               .setBranch(branch != null ? branch : "main")
               .setCloneAllBranches(true)
               .setCredentialsProvider(getGlobalCredentials())
               .call()
               .close();
        } catch (GitAPIException e) {
            // Clean up partial clone directory
            deleteDirectoryQuietly(targetDir);
            throw new IllegalStateException("Clone failed: " + e.getMessage(), e);
        }

        RepoConfig config = repoRegistry.register(name, targetDir.toString(), remoteUrl);
        return buildRepoDTO(config);
    }

    /**
     * Registers an already-existing local repository (with a {@code .git} directory).
     *
     * @param localPath absolute path to the repository root
     * @param name      human-readable display name
     * @return DTO with repository information
     * @throws IllegalArgumentException if the path is invalid or not a Git repo
     */
    public GitRepoDTO addExistingRepo(String localPath, String name) {
        Path resolved = validateAndResolvePath(localPath);
        if (!Files.exists(resolved.resolve(".git"))) {
            throw new IllegalArgumentException("No .git directory found at: " + localPath);
        }

        RepoConfig config = repoRegistry.register(name, resolved.toString(), detectRemoteUrl(resolved));
        return buildRepoDTO(config);
    }

    /**
     * Returns summary information for all registered repositories.
     * Repos whose local path no longer exists are returned with minimal info.
     *
     * @return list of {@link GitRepoDTO}
     */
    public List<GitRepoDTO> listRepos() {
        return repoRegistry.getAll().stream()
                .map(this::buildRepoDTO)
                .toList();
    }

    /**
     * Returns detailed information for a single repository.
     *
     * @param repoId repository UUID
     * @return {@link GitRepoDTO}
     * @throws IllegalArgumentException if the repo ID is not found
     */
    public GitRepoDTO getRepoInfo(String repoId) {
        RepoConfig config = requireRepo(repoId);
        return buildRepoDTO(config);
    }

    /**
     * Unregisters a repository. The local files are NOT deleted.
     *
     * @param repoId repository UUID
     */
    public void removeRepo(String repoId) {
        repoRegistry.unregister(repoId);
        repoLocks.remove(repoId);
    }

    // ── Status & Log ──────────────────────────────────────────────────────────

    /**
     * Returns the working-tree and index status for a repository.
     *
     * @param repoId repository UUID
     * @return {@link GitStatusDTO}
     */
    public GitStatusDTO getStatus(String repoId) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config)) {
            Status status = git.status().call();
            String branch = git.getRepository().getBranch();
            return new GitStatusDTO(
                    branch,
                    status.isClean(),
                    List.copyOf(status.getAdded()),
                    List.copyOf(status.getChanged()),
                    List.copyOf(status.getRemoved()),
                    List.copyOf(status.getUntracked()),
                    List.copyOf(status.getModified()),
                    List.copyOf(status.getMissing()),
                    List.copyOf(status.getConflicting())
            );
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to read status: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a page of commits from the specified branch's log.
     *
     * <p>Diff statistics (insertions, deletions, filesChanged) are only
     * calculated for the first {@value #STATS_COMMIT_LIMIT} commits to
     * avoid expensive tree-diffing for large histories.
     *
     * @param repoId   repository UUID
     * @param branch   branch/ref to log (defaults to HEAD)
     * @param maxCount maximum number of commits to return (capped at 200)
     * @param skip     number of commits to skip (for pagination)
     * @return list of {@link GitCommitDTO}
     */
    public List<GitCommitDTO> getCommitLog(String repoId, String branch, int maxCount, int skip) {
        RepoConfig config  = requireRepo(repoId);
        int        bounded = Math.min(maxCount, 200);

        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Repository repo = git.getRepository();
            String     ref  = branch != null ? branch : "HEAD";
            ObjectId   head = repo.resolve(ref);
            if (head == null) {
                throw new IllegalArgumentException("Branch or ref not found: " + ref);
            }

            Iterable<RevCommit> log = git.log()
                    .add(head)
                    .setMaxCount(bounded)
                    .setSkip(skip)
                    .call();

            List<GitCommitDTO> result = new ArrayList<>();
            int index = 0;
            for (RevCommit commit : log) {
                int filesChanged = 0, insertions = 0, deletions = 0;
                if (index < STATS_COMMIT_LIMIT) {
                    int[] stats = calcDiffStats(git, walk, commit);
                    filesChanged = stats[0];
                    insertions   = stats[1];
                    deletions    = stats[2];
                }
                result.add(toCommitDTO(commit, filesChanged, insertions, deletions));
                index++;
            }
            return result;

        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to read commit log: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a single commit's metadata with full diff statistics.
     *
     * @param repoId     repository UUID
     * @param commitHash full or abbreviated commit SHA
     * @return {@link GitCommitDTO}
     */
    public GitCommitDTO getCommitInfo(String repoId, String commitHash) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            RevCommit commit = resolveCommit(git.getRepository(), walk, commitHash);
            int[] stats = calcDiffStats(git, walk, commit);
            return toCommitDTO(commit, stats[0], stats[1], stats[2]);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read commit: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a file-by-file diff for a single commit.
     *
     * <p>Individual file patches are capped at {@value #MAX_FILE_DIFF_BYTES} bytes;
     * the total diff is capped at {@value #MAX_TOTAL_DIFF_BYTES} bytes.
     *
     * @param repoId     repository UUID
     * @param commitHash full or abbreviated commit SHA
     * @return {@link GitDiffDTO}
     */
    public GitDiffDTO getCommitDiff(String repoId, String commitHash) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Repository repo   = git.getRepository();
            RevCommit  commit = resolveCommit(repo, walk, commitHash);

            AbstractTreeIterator newTree    = treeIterator(repo, walk, commit);
            AbstractTreeIterator parentTree = commit.getParentCount() > 0
                    ? treeIterator(repo, walk, commit.getParent(0))
                    : new EmptyTreeIterator();

            List<DiffEntry> entries;
            try (DiffFormatter statsFormatter = new DiffFormatter(NullOutputStream.INSTANCE)) {
                statsFormatter.setRepository(repo);
                statsFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                entries = statsFormatter.scan(parentTree, newTree);
            }

            // Re-open tree iterators — they are consumed by the first scan
            newTree    = treeIterator(repo, walk, commit);
            parentTree = commit.getParentCount() > 0
                    ? treeIterator(repo, walk, commit.getParent(0))
                    : new EmptyTreeIterator();

            List<GitDiffEntryDTO> dtoEntries = new ArrayList<>();
            int totalBytes = 0;

            ByteArrayOutputStream patchBuffer = new ByteArrayOutputStream();
            try (DiffFormatter patchFormatter = new DiffFormatter(patchBuffer)) {
                patchFormatter.setRepository(repo);
                patchFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                // Re-scan with patch formatter for the patch text
                List<DiffEntry> patchEntries = patchFormatter.scan(parentTree, newTree);

                for (DiffEntry entry : patchEntries) {
                    if (totalBytes >= MAX_TOTAL_DIFF_BYTES) break;

                    patchBuffer.reset();
                    patchFormatter.format(entry);
                    byte[] raw = patchBuffer.toByteArray();

                    String patch;
                    if (raw.length > MAX_FILE_DIFF_BYTES) {
                        patch = new String(raw, 0, MAX_FILE_DIFF_BYTES, StandardCharsets.UTF_8)
                                + "\n[diff truncated — file exceeds 50 KB limit]";
                    } else {
                        patch = new String(raw, StandardCharsets.UTF_8);
                    }

                    totalBytes += raw.length;
                    dtoEntries.add(new GitDiffEntryDTO(
                            entry.getChangeType().name(),
                            entry.getOldPath(),
                            entry.getNewPath(),
                            patch
                    ));
                }
            }

            return new GitDiffDTO(commitHash, dtoEntries);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read diff: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the raw content of a file at a specific commit.
     * Returns {@code null} for binary files (detected via byte inspection).
     *
     * @param repoId     repository UUID
     * @param commitHash commit SHA (or branch name)
     * @param filePath   relative path within the repo
     * @return file content as a String, or {@code null} for binary files
     */
    public String getFileContent(String repoId, String commitHash, String filePath) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Repository repo   = git.getRepository();
            RevCommit  commit = resolveCommit(repo, walk, commitHash);
            RevTree    tree   = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    throw new IllegalArgumentException("File not found: " + filePath);
                }

                ObjectId   objectId = treeWalk.getObjectId(0);
                ObjectLoader loader  = repo.open(objectId);
                byte[] bytes = loader.getBytes();

                if (isBinary(bytes)) {
                    return null;
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file content: " + e.getMessage(), e);
        }
    }

    // ── Branch Operations ─────────────────────────────────────────────────────

    /**
     * Lists all local and remote branches for the given repository.
     *
     * @param repoId repository UUID
     * @return list of {@link GitBranchDTO}
     */
    public List<GitBranchDTO> listBranches(String repoId) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Repository repo    = git.getRepository();
            String     current = repo.getBranch();

            List<Ref> refs = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call();

            List<GitBranchDTO> result = new ArrayList<>();
            for (Ref ref : refs) {
                result.add(toBranchDTO(git, repo, walk, ref, current));
            }
            return result;

        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to list branches: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new local branch.
     *
     * @param repoId     repository UUID
     * @param branchName new branch name
     * @param startPoint commit or branch to branch from (defaults to HEAD)
     * @return the newly created {@link GitBranchDTO}
     */
    public GitBranchDTO createBranch(String repoId, String branchName, String startPoint) {
        RepoConfig config = requireRepo(repoId);
        ReentrantLock lock = lockFor(repoId);
        lock.lock();
        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Ref ref = git.branchCreate()
                    .setName(branchName)
                    .setStartPoint(startPoint != null ? startPoint : "HEAD")
                    .call();

            String current = git.getRepository().getBranch();
            return toBranchDTO(git, git.getRepository(), walk, ref, current);

        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to create branch: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes a local branch (non-forced — refuses to delete unmerged branches).
     *
     * @param repoId     repository UUID
     * @param branchName branch name to delete
     */
    public void deleteBranch(String repoId, String branchName) {
        RepoConfig config = requireRepo(repoId);
        ReentrantLock lock = lockFor(repoId);
        lock.lock();
        try (Git git = openGit(config)) {
            git.branchDelete()
               .setBranchNames(branchName)
               .setForce(false)
               .call();
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to delete branch: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks out a branch (optionally creating it).
     *
     * @param repoId     repository UUID
     * @param branchName branch to check out
     * @param createNew  if {@code true}, creates the branch before checking out
     * @return updated {@link GitRepoDTO}
     */
    public GitRepoDTO checkout(String repoId, String branchName, boolean createNew) {
        RepoConfig config = requireRepo(repoId);
        ReentrantLock lock = lockFor(repoId);
        lock.lock();
        try (Git git = openGit(config)) {
            git.checkout()
               .setName(branchName)
               .setCreateBranch(createNew)
               .call();
            return buildRepoDTO(config);
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Checkout failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    // ── Remote Operations ─────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from a remote.
     *
     * @param repoId     repository UUID
     * @param remoteName remote name (defaults to {@code "origin"})
     * @return updated {@link GitRepoDTO}
     */
    public GitRepoDTO pull(String repoId, String remoteName) {
        RepoConfig config = requireRepo(repoId);
        ReentrantLock lock = lockFor(repoId);
        lock.lock();
        try (Git git = openGit(config)) {
            PullResult result = git.pull()
                    .setRemote(remoteName != null ? remoteName : "origin")
                    .setCredentialsProvider(getGlobalCredentials())
                    .call();

            if (!result.isSuccessful()) {
                String mergeMsg = result.getMergeResult() != null
                        ? result.getMergeResult().getMergeStatus().toString()
                        : "unknown";
                throw new IllegalStateException("Pull failed: " + mergeMsg);
            }
            return buildRepoDTO(config);
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Pull failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pushes the current branch to the remote.
     *
     * @param repoId     repository UUID
     * @param remoteName remote name (defaults to {@code "origin"})
     * @param branch     branch to push (defaults to current branch)
     */
    public void push(String repoId, String remoteName, String branch) {
        RepoConfig config = requireRepo(repoId);
        ReentrantLock lock = lockFor(repoId);
        lock.lock();
        try (Git git = openGit(config)) {
            Iterable<org.eclipse.jgit.transport.PushResult> results = git.push()
                    .setRemote(remoteName != null ? remoteName : "origin")
                    .setCredentialsProvider(getGlobalCredentials())
                    .call();

            for (org.eclipse.jgit.transport.PushResult pr : results) {
                for (org.eclipse.jgit.transport.RemoteRefUpdate rru : pr.getRemoteUpdates()) {
                    if (rru.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                        throw new IllegalStateException(
                                "Push rejected (non-fast-forward). Pull first.");
                    }
                    if (rru.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                        throw new IllegalStateException(
                                "Push rejected: " + rru.getMessage());
                    }
                }
            }
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Push failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetches from a remote without merging.
     *
     * @param repoId     repository UUID
     * @param remoteName remote name (defaults to {@code "origin"})
     */
    public void fetch(String repoId, String remoteName) {
        RepoConfig config = requireRepo(repoId);
        try (Git git = openGit(config)) {
            git.fetch()
               .setRemote(remoteName != null ? remoteName : "origin")
               .setCredentialsProvider(getGlobalCredentials())
               .call();
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Fetch failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Opens a JGit {@link Git} handle for a registered repo.
     * Callers are responsible for closing the returned handle.
     */
    private Git openGit(RepoConfig config) throws IOException {
        return Git.open(Path.of(config.localPath()).toFile());
    }

    /**
     * Looks up a repo or throws {@link IllegalArgumentException} if not found.
     */
    private RepoConfig requireRepo(String repoId) {
        return repoRegistry.get(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));
    }

    /**
     * Returns (creating if absent) the write lock for a given repo.
     */
    private ReentrantLock lockFor(String repoId) {
        return repoLocks.computeIfAbsent(repoId, id -> new ReentrantLock(true));
    }

    /**
     * Validates that {@code rawPath} is under {@code basePath}, normalizing
     * the path to resist traversal attacks.
     *
     * @throws IllegalArgumentException if the path escapes {@code basePath}
     */
    private Path validateAndResolvePath(String rawPath) {
        Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException(
                    "Path is outside the configured git base directory: " + rawPath);
        }
        return resolved;
    }

    /**
     * Replaces any characters that are not alphanumeric, {@code -}, or {@code _}
     * with an underscore to produce a safe directory name.
     */
    private static String sanitizeName(String name) {
        return SAFE_NAME.matcher(name).replaceAll("_");
    }

    /**
     * Returns a global {@link CredentialsProvider} backed by the
     * {@code GIT_TOKEN} environment variable, or {@code null} for public repos.
     */
    private CredentialsProvider getGlobalCredentials() {
        String token = System.getenv("GIT_TOKEN");
        if (token != null && !token.isBlank()) {
            return new UsernamePasswordCredentialsProvider("", token);
        }
        return null;
    }

    /**
     * Builds a complete {@link GitRepoDTO} from a registry entry.
     * If the repo directory no longer exists, returns a minimal DTO.
     */
    private GitRepoDTO buildRepoDTO(RepoConfig config) {
        Path repoPath = Path.of(config.localPath());
        if (!Files.exists(repoPath)) {
            return new GitRepoDTO(
                    config.id(), config.name(), config.localPath(),
                    config.remoteUrl(), "unavailable", false,
                    null, null, null, List.of(), List.of()
            );
        }

        try (Git git = openGit(config);
             RevWalk walk = new RevWalk(git.getRepository())) {

            Repository repo    = git.getRepository();
            String     current = repo.getBranch();

            // Last commit on HEAD
            ObjectId headId = repo.resolve("HEAD");
            RevCommit head  = headId != null ? walk.parseCommit(headId) : null;

            // Collect local branches
            List<String> localBranches = git.branchList().call().stream()
                    .map(r -> stripRefPrefix(r.getName(), "refs/heads/"))
                    .toList();

            // Collect remote branches
            List<String> remoteBranches = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call().stream()
                    .map(r -> stripRefPrefix(r.getName(), "refs/remotes/"))
                    .toList();

            // Working-tree clean check
            boolean isClean = git.status().call().isClean();

            return new GitRepoDTO(
                    config.id(),
                    config.name(),
                    config.localPath(),
                    config.remoteUrl(),
                    current,
                    isClean,
                    head != null ? head.getName().substring(0, 7) : null,
                    head != null ? head.getShortMessage() : null,
                    head != null ? Instant.ofEpochSecond(head.getCommitTime()) : null,
                    localBranches,
                    remoteBranches
            );

        } catch (IOException | GitAPIException e) {
            log.warn("Could not read repo info for '{}': {}", config.name(), e.getMessage());
            return new GitRepoDTO(
                    config.id(), config.name(), config.localPath(),
                    config.remoteUrl(), "error", false,
                    null, null, null, List.of(), List.of()
            );
        }
    }

    /** Converts a {@link RevCommit} and pre-computed stats into a {@link GitCommitDTO}. */
    private static GitCommitDTO toCommitDTO(RevCommit c, int filesChanged, int insertions, int deletions) {
        PersonIdent author = c.getAuthorIdent();
        List<String> parents = Arrays.stream(c.getParents())
                .map(p -> p.getName())
                .toList();
        return new GitCommitDTO(
                c.getName(),
                c.getName().substring(0, 7),
                c.getFullMessage().trim(),
                author.getName(),
                author.getEmailAddress(),
                Instant.ofEpochSecond(c.getCommitTime()),
                parents,
                filesChanged,
                insertions,
                deletions
        );
    }

    /**
     * Computes diff stats (filesChanged, insertions, deletions) for a commit
     * by comparing its tree against its parent (or empty tree for root commits).
     *
     * @return {@code int[3]} — [filesChanged, insertions, deletions]
     */
    private int[] calcDiffStats(Git git, RevWalk walk, RevCommit commit) {
        try {
            Repository repo       = git.getRepository();
            AbstractTreeIterator newTree    = treeIterator(repo, walk, commit);
            AbstractTreeIterator parentTree = commit.getParentCount() > 0
                    ? treeIterator(repo, walk, commit.getParent(0))
                    : new EmptyTreeIterator();

            try (DiffFormatter fmt = new DiffFormatter(NullOutputStream.INSTANCE)) {
                fmt.setRepository(repo);
                fmt.setDetectRenames(true);
                List<DiffEntry> diffs = fmt.scan(parentTree, newTree);

                int files = diffs.size();
                int ins = 0, del = 0;
                for (DiffEntry entry : diffs) {
                    org.eclipse.jgit.diff.EditList edits = fmt.toFileHeader(entry).toEditList();
                    for (org.eclipse.jgit.diff.Edit edit : edits) {
                        ins += edit.getLengthB();
                        del += edit.getLengthA();
                    }
                }
                return new int[]{files, ins, del};
            }
        } catch (IOException e) {
            log.debug("Could not compute diff stats for commit {}: {}", commit.getName(), e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    /**
     * Creates a {@link CanonicalTreeParser} for the given commit's tree.
     * The parser buffers tree data synchronously so the caller can close the
     * {@link ObjectReader} immediately after this method returns.
     */
    private AbstractTreeIterator treeIterator(Repository repo, RevWalk walk, RevCommit commit)
            throws IOException {
        walk.parseHeaders(commit);
        RevTree tree = commit.getTree();
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, tree.getId());
            return parser;
        }
    }

    /** Converts a {@link Ref} to a {@link GitBranchDTO}. */
    private GitBranchDTO toBranchDTO(Git git, Repository repo, RevWalk walk,
                                     Ref ref, String currentBranch) {
        String fullName = ref.getName();
        boolean isRemote = fullName.startsWith("refs/remotes/");
        String  shortName = isRemote
                ? stripRefPrefix(fullName, "refs/remotes/")
                : stripRefPrefix(fullName, "refs/heads/");
        boolean isCurrent = shortName.equals(currentBranch);

        // Resolve the commit the branch points to
        RevCommit tip = null;
        try {
            ObjectId id = ref.getObjectId();
            if (id != null) {
                tip = walk.parseCommit(id);
            }
        } catch (IOException e) {
            log.debug("Cannot resolve tip for {}: {}", fullName, e.getMessage());
        }

        // Ahead / behind for local branches with a tracking upstream
        int ahead = 0, behind = 0;
        String trackingBranch = null;
        if (!isRemote) {
            try {
                org.eclipse.jgit.lib.BranchTrackingStatus bts =
                        org.eclipse.jgit.lib.BranchTrackingStatus.of(repo, shortName);
                if (bts != null) {
                    trackingBranch = bts.getRemoteTrackingBranch();
                    ahead          = bts.getAheadCount();
                    behind         = bts.getBehindCount();
                }
            } catch (IOException e) {
                log.debug("Cannot compute tracking status for {}: {}", shortName, e.getMessage());
            }
        }

        return new GitBranchDTO(
                shortName,
                isRemote,
                isCurrent,
                tip != null ? tip.getName().substring(0, 7) : null,
                tip != null ? tip.getShortMessage() : null,
                tip != null ? Instant.ofEpochSecond(tip.getCommitTime()) : null,
                trackingBranch,
                ahead,
                behind
        );
    }

    /** Resolves a full or abbreviated commit hash to a {@link RevCommit}. */
    private RevCommit resolveCommit(Repository repo, RevWalk walk, String hash)
            throws IOException {
        ObjectId id = repo.resolve(hash);
        if (id == null) {
            throw new IllegalArgumentException("Commit not found: " + hash);
        }
        return walk.parseCommit(id);
    }

    private static String stripRefPrefix(String ref, String prefix) {
        return ref.startsWith(prefix) ? ref.substring(prefix.length()) : ref;
    }

    /**
     * Detects the remote origin URL from an existing local repository.
     * Returns {@code null} if no remote is configured.
     */
    private String detectRemoteUrl(Path repoPath) {
        try (Git git = Git.open(repoPath.toFile())) {
            org.eclipse.jgit.lib.StoredConfig cfg = git.getRepository().getConfig();
            return cfg.getString("remote", "origin", "url");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Heuristic binary detection: checks the first 8000 bytes for null bytes.
     * Returns {@code true} if any null byte is found.
     */
    private static boolean isBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private static void deleteDirectoryQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                     .sorted(Comparator.reverseOrder())
                     .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }
}

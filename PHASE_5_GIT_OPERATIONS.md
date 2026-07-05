# Phase 5 — Git Operations (JGit)

## Objective
Implement a Git management layer using Eclipse JGit that provides GitKraken-like functionality through REST APIs: clone repositories, pull/push, browse branches, view commit history with diffs, and manage repos — all without touching the terminal. Users register their repos in ServerWatch, and the dashboard becomes their Git GUI.

## Prerequisites
- Phase 4 completed — Docker management operational
- JGit dependency in pom.xml (added in Phase 1)
- `serverwatch.git.base-path` configured (default: `/opt/repos`)

## Context
JGit is Eclipse's pure Java Git implementation. It does NOT shell out to `git` CLI — it implements the Git protocol natively. Key classes:
- `Git.open(dir)` — opens an existing repo
- `Git.cloneRepository()` — clones a remote repo
- `Repository` — the low-level repo handle
- `RevWalk`, `RevCommit` — commit traversal
- `DiffFormatter` — generates diffs between commits
- `CredentialsProvider` — authentication for remote operations

## Step 1: DTOs

### GitRepoDTO.java
```java
// - repoId (String) — UUID, auto-generated
// - name (String) — human-readable name
// - localPath (String) — absolute path on the VPS
// - remoteUrl (String) — origin URL (if any)
// - currentBranch (String) — checked-out branch
// - isClean (boolean) — no uncommitted changes
// - lastCommitHash (String) — short hash
// - lastCommitMessage (String)
// - lastCommitDate (Instant)
// - branches (List<String>) — all local branches
// - remoteBranches (List<String>) — all remote branches
```

### GitCommitDTO.java
```java
// - hash (String) — full SHA
// - shortHash (String) — 7-char
// - message (String) — full commit message
// - author (String)
// - authorEmail (String)
// - date (Instant)
// - parentHashes (List<String>) — for merge detection
// - filesChanged (int) — number of files changed
// - insertions (int)
// - deletions (int)
```

### GitDiffDTO.java
```java
// - commitHash (String) — the commit this diff belongs to
// - entries (List<DiffEntry>)
```

### GitDiffEntryDTO.java
```java
// - changeType (String) — "ADD", "MODIFY", "DELETE", "RENAME", "COPY"
// - oldPath (String)
// - newPath (String)
// - patch (String) — unified diff text
```

### GitBranchDTO.java
```java
// - name (String) — branch name without refs/heads/ prefix
// - isRemote (boolean)
// - isCurrent (boolean)
// - lastCommitHash (String)
// - lastCommitMessage (String)
// - lastCommitDate (Instant)
// - trackingBranch (String) — remote tracking branch, if any
// - ahead (int) — commits ahead of remote
// - behind (int) — commits behind remote
```

### GitStatusDTO.java
```java
// - branch (String) — current branch
// - isClean (boolean)
// - added (List<String>) — staged new files
// - changed (List<String>) — staged modified files
// - removed (List<String>) — staged deleted files
// - untracked (List<String>) — untracked files
// - modified (List<String>) — unstaged modified files
// - missing (List<String>) — deleted but not staged
// - conflicting (List<String>) — merge conflicts
```

### GitOperationRequest.java (request bodies)
```java
// For clone:
// - remoteUrl (String, required)
// - name (String, required) — repo display name
// - branch (String, optional) — branch to clone, default "main"
// - credentialId (String, optional) — stored credential reference
//
// For pull/push:
// - repoId (String, required)
// - remoteName (String, default "origin")
// - branch (String, optional)
//
// For checkout:
// - repoId (String, required)
// - branch (String, required)
// - createNew (boolean, default false)
```

## Step 2: Repo Registry

Repos are tracked in a simple JSON file or a DB table. For simplicity, use a JSON file at `{base-path}/repos.json`. This avoids adding another DB table for something that rarely changes.

### RepoRegistry.java
```java
@Component
public class RepoRegistry {

    private final Path registryFile; // {base-path}/repos.json
    private final Map<String, RepoConfig> repos = new ConcurrentHashMap<>();

    // RepoConfig: id, name, localPath, remoteUrl, credentialType

    @PostConstruct
    public void load() {
        // Read repos.json if it exists, populate the map
        // Also scan base-path for any .git directories not yet registered
    }

    public RepoConfig register(String name, String localPath, String remoteUrl) {
        // Add to map, write to repos.json
        // Return the new RepoConfig with generated UUID
    }

    public void unregister(String repoId) {
        // Remove from map and repos.json
        // Do NOT delete the actual repo directory
    }

    public Optional<RepoConfig> get(String repoId) { ... }
    public List<RepoConfig> getAll() { ... }

    private void persist() {
        // Write the map to repos.json atomically
        // Write to .tmp first, then rename
    }
}
```

## Step 3: GitService

### GitService.java

```java
@Service
public class GitService {

    private final RepoRegistry repoRegistry;
    private final ServerWatchProperties properties;

    // ====== REPO MANAGEMENT ======

    public GitRepoDTO cloneRepository(String remoteUrl, String name, String branch) {
        // 1. Create target directory: {base-path}/{sanitized-name}
        // 2. Git.cloneRepository()
        //        .setURI(remoteUrl)
        //        .setDirectory(targetDir)
        //        .setBranch(branch != null ? branch : "main")
        //        .setCloneAllBranches(true)
        //        .call();
        // 3. Register in RepoRegistry
        // 4. Return GitRepoDTO with repo info
    }

    public GitRepoDTO addExistingRepo(String localPath, String name) {
        // Validate that localPath contains a .git directory
        // Register in RepoRegistry
        // Return repo info
    }

    public List<GitRepoDTO> listRepos() {
        // Get all from RepoRegistry, open each with Git.open()
        // Build GitRepoDTO for each: current branch, last commit, clean status
        // Handle errors gracefully — if a repo path no longer exists, mark as "unavailable"
    }

    // ====== GIT OPERATIONS ======

    public GitRepoDTO getRepoInfo(String repoId) {
        // Open repo, read all branch info, last commit, status
    }

    public GitStatusDTO getStatus(String repoId) {
        // Git.open(path).status().call()
        // Map Status to GitStatusDTO
    }

    public List<GitCommitDTO> getCommitLog(String repoId, String branch, int maxCount, int skip) {
        // Git.open(path).log()
        //     .add(repo.resolve(branch != null ? branch : "HEAD"))
        //     .setMaxCount(maxCount)
        //     .setSkip(skip)
        //     .call()
        //
        // For each RevCommit:
        //   hash, message, author, date, parents
        //   To get filesChanged/insertions/deletions:
        //     use DiffFormatter with a RevWalk to compare commit vs parent
        //     This is expensive — only calculate for the first 50 commits
    }

    public GitDiffDTO getCommitDiff(String repoId, String commitHash) {
        // RevWalk to get the commit
        // DiffFormatter with ByteArrayOutputStream
        // Compare commit tree vs parent tree (or empty tree if initial commit)
        // For each DiffEntry: changeType, oldPath, newPath
        // Format the patch text (unified diff)
        //
        // IMPORTANT: limit patch size per file to 50KB to prevent
        // huge binary diffs from consuming memory
    }

    public String getFileContent(String repoId, String commitHash, String filePath) {
        // TreeWalk to find the file in the commit tree
        // Read and return content as String
        // Return null for binary files (detect via byte inspection)
    }

    // ====== BRANCH OPERATIONS ======

    public List<GitBranchDTO> listBranches(String repoId) {
        // Git.open(path).branchList()
        //     .setListMode(ListBranchCommand.ListMode.ALL)
        //     .call()
        //
        // For each Ref: extract name, check if remote, check if current
        // Calculate ahead/behind using BranchTrackingStatus
    }

    public GitBranchDTO createBranch(String repoId, String branchName, String startPoint) {
        // Git.open(path).branchCreate()
        //     .setName(branchName)
        //     .setStartPoint(startPoint != null ? startPoint : "HEAD")
        //     .call()
    }

    public void deleteBranch(String repoId, String branchName) {
        // Git.open(path).branchDelete()
        //     .setBranchNames(branchName)
        //     .setForce(false) // don't force-delete unmerged branches
        //     .call()
    }

    public GitRepoDTO checkout(String repoId, String branchName, boolean createNew) {
        // Git.open(path).checkout()
        //     .setName(branchName)
        //     .setCreateBranch(createNew)
        //     .call()
        // Return updated repo info
    }

    // ====== REMOTE OPERATIONS ======

    public GitRepoDTO pull(String repoId, String remoteName) {
        // Git.open(path).pull()
        //     .setRemote(remoteName != null ? remoteName : "origin")
        //     .setCredentialsProvider(getCredentials(repoId))
        //     .call()
        //
        // Return PullResult info: fetched, merge result, conflicts
        // Return updated GitRepoDTO
    }

    public void push(String repoId, String remoteName, String branch) {
        // Git.open(path).push()
        //     .setRemote(remoteName != null ? remoteName : "origin")
        //     .setCredentialsProvider(getCredentials(repoId))
        //     .call()
        //
        // Check PushResult for errors (rejected, auth failure)
        // Throw descriptive exception on failure
    }

    public void fetch(String repoId, String remoteName) {
        // Git.open(path).fetch()
        //     .setRemote(remoteName != null ? remoteName : "origin")
        //     .setCredentialsProvider(getCredentials(repoId))
        //     .call()
    }

    // ====== CREDENTIAL MANAGEMENT ======

    private CredentialsProvider getCredentials(String repoId) {
        // For HTTPS repos: UsernamePasswordCredentialsProvider(username, token)
        // For SSH repos: SshSessionFactory with custom JSch config
        //
        // For Phase 5, support HTTPS with personal access tokens only
        // SSH support can be added later
        //
        // Store credentials in application config or environment variables
        // NEVER log credentials
        return null; // returns null for public repos
    }
}
```

## Step 4: REST Controller

### GitController.java
```
# Repo Management
GET    /api/git/repos                      → List<GitRepoDTO>
GET    /api/git/repos/{id}                 → GitRepoDTO (detailed)
POST   /api/git/repos/clone                → GitRepoDTO (body: GitOperationRequest)
POST   /api/git/repos/add                  → GitRepoDTO (body: {localPath, name})
DELETE /api/git/repos/{id}                 → 200 OK (unregister, don't delete files)

# Status & History
GET    /api/git/repos/{id}/status          → GitStatusDTO
GET    /api/git/repos/{id}/log             → List<GitCommitDTO>
       Query: branch=main, limit=50, skip=0
GET    /api/git/repos/{id}/commits/{hash}  → GitCommitDTO (detailed)
GET    /api/git/repos/{id}/commits/{hash}/diff → GitDiffDTO
GET    /api/git/repos/{id}/file            → String (file content)
       Query: commit=abc123, path=src/Main.java

# Branch Operations
GET    /api/git/repos/{id}/branches        → List<GitBranchDTO>
POST   /api/git/repos/{id}/branches        → GitBranchDTO (body: {name, startPoint})
DELETE /api/git/repos/{id}/branches/{name} → 200 OK
POST   /api/git/repos/{id}/checkout        → GitRepoDTO (body: {branch, createNew})

# Remote Operations
POST   /api/git/repos/{id}/pull            → GitRepoDTO (body: {remoteName})
POST   /api/git/repos/{id}/push            → 200 OK (body: {remoteName, branch})
POST   /api/git/repos/{id}/fetch           → 200 OK (body: {remoteName})
```

## Step 5: Safety & Validation

1. **Path traversal prevention** — all repo paths must be under `serverwatch.git.base-path`. Reject any path containing `..` or symbolic links pointing outside.
2. **Sanitize repo names** — for clone operations, sanitize the directory name (alphanumeric, hyphens, underscores only).
3. **Diff size limits** — cap individual file diffs at 50KB, total diff at 500KB.
4. **Operation timeouts** — clone and fetch operations timeout after 120 seconds.
5. **Credential security** — credentials are never included in API responses. CredentialsProvider returns `***` placeholders in any serialized output.
6. **Concurrent access** — use a `ReentrantLock` per repository to prevent concurrent write operations (pull, push, checkout) on the same repo.

## Acceptance Criteria
- [ ] `POST /api/git/repos/clone` successfully clones a public GitHub repo
- [ ] `GET /api/git/repos` lists all registered repos with branch info and last commit
- [ ] `GET /api/git/repos/{id}/log?limit=20` returns last 20 commits with hash, message, author, date
- [ ] `GET /api/git/repos/{id}/commits/{hash}/diff` returns file-by-file unified diff
- [ ] `GET /api/git/repos/{id}/branches` lists local and remote branches with ahead/behind counts
- [ ] `POST /api/git/repos/{id}/checkout` switches branches successfully
- [ ] `POST /api/git/repos/{id}/pull` pulls latest changes from remote
- [ ] `GET /api/git/repos/{id}/status` correctly shows modified/untracked/staged files
- [ ] Path traversal attempts return 400 Bad Request
- [ ] Concurrent operations on the same repo are serialized (no corruption)
- [ ] Large diffs are truncated with a clear message

## Files to Create
```
src/main/java/com/serverwatch/
├── model/dto/
│   ├── GitRepoDTO.java
│   ├── GitCommitDTO.java
│   ├── GitDiffDTO.java
│   ├── GitDiffEntryDTO.java
│   ├── GitBranchDTO.java
│   ├── GitStatusDTO.java
│   └── GitOperationRequest.java
├── service/
│   ├── GitService.java
│   └── RepoRegistry.java
└── controller/
    └── GitController.java
```

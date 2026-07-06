# Phase F4 — File Manager

## Objective
Build a WinSCP/VS Code-inspired file manager with directory browsing, file preview, text editor (Monaco), upload/download, and basic file operations. Split-pane layout with tree on the left and content on the right.

## Prerequisites
- Phase F3 completed — Docker panel working

## Step 1: Additional Dependencies

```bash
npm install @monaco-editor/react
```

## Step 2: API Layer

### src/api/files.ts
```typescript
// listDirectory(path, showHidden?) → DirectoryListing
//   GET /api/files/list?path={path}&showHidden={showHidden}
//
// readFile(path) → FileContent
//   GET /api/files/read?path={path}
//
// writeFile(path, content, encoding?) → FileEntry
//   POST /api/files/write {path, content, encoding}
//
// createFile(parentPath, name, type, content?) → FileEntry
//   POST /api/files/create {path, name, type, content}
//
// deleteEntry(path, recursive?) → void
//   DELETE /api/files?path={path}&recursive={recursive}
//
// moveEntry(sourcePath, targetPath) → FileEntry
//   POST /api/files/move {sourcePath, targetPath}
//
// copyEntry(sourcePath, targetPath) → FileEntry
//   POST /api/files/copy {sourcePath, targetPath}
//
// uploadFile(targetDir, file) → UploadResponse
//   POST /api/files/upload (multipart: file + targetPath field)
//
// downloadFile(path) → Blob
//   GET /api/files/download?path={path} (responseType: 'blob')
//
// chmod(path, permissions) → FileEntry
//   POST /api/files/chmod {path, permissions}
//
// searchFiles(root, query, maxResults?) → FileEntry[]
//   GET /api/files/search?root={root}&query={query}&maxResults={maxResults}
//
// getRoots() → string[]
//   GET /api/files/roots
```

## Step 3: Page Layout

### src/pages/FilesPage.tsx

```
┌─── Toolbar ─────────────────────────────────────────────────────┐
│ [🏠 Home] [⬆ Upload] [📁 New Folder] [📄 New File] [🔍 Search] │
└──────────────────────────────────────────────────────────────────┘

┌──── Breadcrumb ─────────────────────────────────────────────────┐
│  / home / user / projects / serverwatch /                       │
└──────────────────────────────────────────────────────────────────┘

┌──── File List ──────────────────────────────────────────────────┐
│ Type │ Name              │ Size     │ Modified        │ Perms   │
│──────┼───────────────────┼──────────┼─────────────────┼─────────│
│  📁  │ ..                │          │                 │         │
│  📁  │ src               │ 4 items  │ 2h ago          │ rwxr-x  │
│  📁  │ public            │ 2 items  │ 3d ago          │ rwxr-x  │
│  📄  │ package.json      │ 1.2 KB   │ 1h ago          │ rw-r--  │
│  📄  │ README.md         │ 3.4 KB   │ 5d ago          │ rw-r--  │
│  📄  │ .env              │ 256 B    │ 1d ago          │ rw----  │
│  🔗  │ logs → /var/log   │          │ 10d ago         │ rwxrwx  │
└──────────────────────────────────────────────────────────────────┘

┌──── Status Bar ─────────────────────────────────────────────────┐
│ 3 directories, 5 files │ Total: 12.4 KB │ /home/user/projects   │
└──────────────────────────────────────────────────────────────────┘
```

## Step 4: Components

### src/components/files/FileBrowser.tsx
Main component orchestrating the file manager.

```typescript
// State:
// - currentPath: string
// - listing: DirectoryListing | null
// - selectedEntries: Set<string> (multi-select with Ctrl/Shift)
// - viewMode: 'list' | 'grid'
// - sortBy: 'name' | 'size' | 'modified' | 'type'
// - sortOrder: 'asc' | 'desc'
// - showHidden: boolean
//
// Features:
// - Double-click directory → navigate into
// - Double-click file → open in editor (if editable) or download
// - Right-click → context menu (Open, Edit, Download, Copy, Move, Rename, Delete, Properties)
// - Keyboard: Enter=open, Delete=delete, F2=rename, Ctrl+C=copy path
// - Drag & drop files from OS into browser → upload
```

### src/components/files/FileTable.tsx
```typescript
// Table view of directory entries
// - Icon per file type (folder, code file, image, document, archive, etc.)
// - File icons based on extension:
//   .js/.ts/.jsx/.tsx → code icon (yellow/blue)
//   .py → python icon
//   .json → json icon (green)
//   .md → markdown icon
//   .yml/.yaml → config icon
//   .png/.jpg/.svg → image icon
//   .zip/.tar/.gz → archive icon
//   folder → folder icon
// - Sortable columns: Name, Size, Modified, Permissions
// - Selected row: bg-accent-blue/10, border-l-2 accent-blue
// - Multi-select: Ctrl+Click, Shift+Click
```

### src/components/files/Breadcrumb.tsx
```typescript
// Clickable path segments: / → home → user → projects
// Each segment is a link that navigates to that directory
// Current directory is text-primary, parents are text-secondary with hover underline
// Root selector dropdown showing allowed roots
```

### src/components/files/FileEditor.tsx
```typescript
// Monaco Editor integration for text files
// Opens in a modal or split view
//
// Features:
// - Syntax highlighting based on file extension
// - Line numbers
// - Search & replace (Ctrl+F, Ctrl+H)
// - Save: Ctrl+S calls writeFile API
// - Unsaved changes indicator (dot on tab/title)
// - File info bar at bottom: encoding, line ending, line count, file size
// - Theme: vs-dark (matches our dark theme)
//
// Language detection from extension:
// .js/.jsx → javascript, .ts/.tsx → typescript
// .py → python, .json → json, .yml/.yaml → yaml
// .sh/.bash → shell, .sql → sql, .md → markdown
// .html → html, .css → css, .xml → xml
// .java → java, .go → go, .rs → rust
// default → plaintext
```

### src/components/files/UploadModal.tsx
```typescript
// Drag & drop zone + file picker
// - Large dashed border area: "Drag files here or click to browse"
// - File list showing upload queue with progress bars
// - Upload progress per file (use XMLHttpRequest for progress events)
// - Cancel button per file
// - Target directory shown (current directory)
// - Multiple file upload support
```

### src/components/files/FileContextMenu.tsx
```typescript
// Right-click context menu using Radix DropdownMenu
// Items vary based on selection:
//
// File selected:
// - Open (if editable)
// - Download
// - ──────────
// - Copy
// - Move
// - Rename
// - ──────────
// - Delete
// - ──────────
// - Properties (shows chmod, size, etc.)
//
// Directory selected:
// - Open
// - Download as ZIP
// - ──────────
// - New File inside
// - New Folder inside
// - ──────────
// - Rename
// - Delete
// - ──────────
// - Properties
//
// Empty area (no selection):
// - New File
// - New Folder
// - ──────────
// - Upload Files
// - ──────────
// - Refresh
```

### src/components/files/CreateDialog.tsx
```typescript
// Dialog for creating new file/folder
// - Radio: File / Folder
// - Name input with validation (no slashes, .., etc.)
// - For files: optional initial content textarea
// - Create button
```

### src/components/files/RenameDialog.tsx
```typescript
// Simple dialog with name input
// Pre-filled with current name, auto-selects filename without extension
```

### src/components/files/PropertiesPanel.tsx
```typescript
// Side panel or modal showing file details:
// - Full path
// - Type, MIME type
// - Size (bytes + human readable)
// - Created, Modified dates
// - Owner, Group
// - Permissions (display + chmod input for admin)
// - Symlink target (if symlink)
```

## Step 5: Search

### src/components/files/SearchModal.tsx
```typescript
// Search dialog:
// - Search input with debounce (300ms)
// - Root directory selector
// - Results list showing matching files with path
// - Click result → navigate to that directory and highlight the file
// - Show file icon, name, full path, size
```

## Acceptance Criteria
- [ ] Directory listing loads and displays files/folders correctly
- [ ] Breadcrumb navigation works — clicking segments navigates
- [ ] Double-click directory enters it, double-click file opens editor
- [ ] File editor opens with correct syntax highlighting
- [ ] Saving file in editor (Ctrl+S) writes back to server
- [ ] Upload files via drag & drop and file picker
- [ ] Download files and directories (as ZIP)
- [ ] Create new files and folders
- [ ] Rename files and folders
- [ ] Delete with confirmation dialog
- [ ] Right-click context menu works with correct options
- [ ] Multi-select with Ctrl/Shift+Click
- [ ] Sort by name, size, modified date
- [ ] Show/hide hidden files toggle
- [ ] Search finds files by name
- [ ] File icons match file types
- [ ] Status bar shows counts and total size
- [ ] Read-only directories show lock icon, write operations blocked

## Files to Create
```
src/
├── api/
│   └── files.ts
├── components/
│   └── files/
│       ├── FileBrowser.tsx
│       ├── FileTable.tsx
│       ├── Breadcrumb.tsx
│       ├── FileEditor.tsx
│       ├── UploadModal.tsx
│       ├── FileContextMenu.tsx
│       ├── CreateDialog.tsx
│       ├── RenameDialog.tsx
│       ├── PropertiesPanel.tsx
│       └── SearchModal.tsx
└── pages/
    └── FilesPage.tsx            (replace placeholder)
```

// ==================== AUTH ====================
export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface User {
  id: number
  username: string
  email: string
  displayName: string
  role: 'ADMIN' | 'USER'
  enabled: boolean
  lastLoginAt: string
  createdAt: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
  timestamp: string
}

// ==================== METRICS ====================
export interface SystemMetric {
  cpuUsagePercent: number
  cpuCoreCount: number
  cpuModelName: string
  memoryTotalBytes: number
  memoryUsedBytes: number
  memoryFreeBytes: number
  memoryUsagePercent: number
  swapTotalBytes: number
  swapUsedBytes: number
  diskInfos: DiskInfo[]
  timestamp: string
}

export interface DiskInfo {
  name: string
  mountPoint: string
  totalBytes: number
  usableBytes: number
  usagePercent: number
  type: string
}

export interface NetworkMetric {
  interfaceName: string
  displayName: string
  macAddress: string
  ipv4Addresses: string[]
  bytesReceived: number
  bytesSent: number
  receivedPerSecond: number
  sentPerSecond: number
  packetsReceived: number
  packetsSent: number
  speed: number
  timestamp: string
}

export interface ProcessInfo {
  pid: number
  name: string
  cpuPercent: number
  memoryBytes: number
  memoryPercent: number
  user: string
  state: string
  startTime: string
  commandLine: string
}

export interface UptimeInfo {
  uptimeSeconds: number
  bootTime: string
  formattedUptime: string
  osName: string
  osVersion: string
  hostname: string
}

// ==================== DOCKER ====================
export interface ContainerInfo {
  containerId: string
  containerIdFull: string
  name: string
  image: string
  state: 'running' | 'stopped' | 'paused' | 'restarting' | 'exited'
  status: string
  created: string
  ports: PortMapping[]
  networks: string[]
  volumes: string[]
  labels: Record<string, string>
  envVars: string[]
}

export interface PortMapping {
  privatePort: number
  publicPort: number
  type: string
  ip: string
}

export interface ContainerStats {
  containerId: string
  containerName: string
  cpuPercent: number
  memoryUsageBytes: number
  memoryLimitBytes: number
  memoryPercent: number
  networkRxBytes: number
  networkTxBytes: number
  blockReadBytes: number
  blockWriteBytes: number
  pidCount: number
  timestamp: string
}

export interface DockerInfoDTO {
  dockerVersion: string
  apiVersion: string
  runningContainers: number
  pausedContainers: number
  stoppedContainers: number
  totalImages: number
  storageDriver: string
  operatingSystem: string
  architecture: string
  totalMemory: number
  hostname: string
}

export interface ImageDTO {
  id: string
  repoTags: string[]
  size: number
  created: string
}

export interface ContainerLogDTO {
  containerId: string
  lines: string[]
  since: string
  stdout: boolean
  stderr: boolean
}

// Minimal subset of Docker inspect response
export interface InspectResponse {
  Id: string
  Name: string
  State: {
    Status: string
    Running: boolean
    Paused: boolean
    Restarting: boolean
    ExitCode: number
    StartedAt: string
    FinishedAt: string
  }
  Config: {
    Image: string
    Cmd: string[] | null
    Entrypoint: string[] | null
    Env: string[] | null
    Labels: Record<string, string>
  }
  HostConfig: {
    RestartPolicy: { Name: string; MaximumRetryCount: number }
  }
  NetworkSettings: {
    IPAddress: string
    Ports: Record<string, Array<{ HostIp: string; HostPort: string }> | null>
    Networks: Record<string, { IPAddress: string; Gateway: string; MacAddress: string }>
  }
  Mounts: Array<{ Type: string; Source: string; Destination: string; Mode: string }>
  Created: string
}

// ==================== FILES ====================
export interface FileEntry {
  name: string
  path: string
  relativePath: string
  type: 'FILE' | 'DIRECTORY' | 'SYMLINK'
  size: number
  permissions: string
  permissionsNumeric: string
  owner: string
  group: string
  modifiedAt: string
  createdAt: string
  isHidden: boolean
  isReadable: boolean
  isWritable: boolean
  isExecutable: boolean
  mimeType: string
  isEditable: boolean
  symlinkTarget: string | null
}

export interface DirectoryListing {
  path: string
  parentPath: string | null
  breadcrumbs: PathBreadcrumb[]
  entries: FileEntry[]
  totalCount: number
  directoryCount: number
  fileCount: number
  totalSize: number
  isReadOnly: boolean
}

export interface PathBreadcrumb {
  name: string
  path: string
}

export interface FileContent {
  path: string
  content: string
  encoding: string
  lineEnding: string
  size: number
  lineCount: number
  isBinary: boolean
}

// ==================== GIT ====================
export interface GitRepo {
  repoId: string
  name: string
  localPath: string
  remoteUrl: string
  currentBranch: string
  isClean: boolean
  lastCommitHash: string
  lastCommitMessage: string
  lastCommitDate: string
  branches: string[]
  remoteBranches: string[]
}

export interface GitCommit {
  hash: string
  shortHash: string
  message: string
  author: string
  authorEmail: string
  date: string
  parentHashes: string[]
  filesChanged: number
  insertions: number
  deletions: number
}

export interface GitDiff {
  commitHash: string
  entries: GitDiffEntry[]
}

export interface GitDiffEntry {
  changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME' | 'COPY'
  oldPath: string
  newPath: string
  patch: string
}

export interface GitBranch {
  name: string
  isRemote: boolean
  isCurrent: boolean
  lastCommitHash: string
  lastCommitMessage: string
  lastCommitDate: string
  trackingBranch: string | null
  ahead: number
  behind: number
}

export interface GitStatus {
  branch: string
  isClean: boolean
  added: string[]
  changed: string[]
  removed: string[]
  untracked: string[]
  modified: string[]
  missing: string[]
  conflicting: string[]
}

// ==================== ALERTS ====================
export interface AlertRule {
  id: number
  name: string
  metricType: string
  operator: string
  threshold: number
  containerName?: string
  networkInterface?: string
  cooldownMinutes: number
  notifyEmail: boolean
  notifyWebhook: boolean
  webhookUrl?: string
  emailRecipients?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface AlertEvent {
  id: number
  ruleId: number
  ruleName: string
  metricType: string
  currentValue: number
  threshold: number
  operator: string
  message: string
  severity: 'WARNING' | 'CRITICAL'
  notified: boolean
  notificationChannels: string[]
  triggeredAt: string
}

// ==================== TERMINAL ====================
export interface TerminalSession {
  sessionId: string
  shell: string
  cwd: string
  createdAt: string
  lastActivityAt: string
  pid: number
  dimensions: TerminalDimensions
}

export interface TerminalDimensions {
  cols: number
  rows: number
}

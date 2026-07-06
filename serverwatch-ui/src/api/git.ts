import { apiClient } from './axios'
import type {
  ApiResponse, GitRepo, GitCommit, GitDiff, GitBranch, GitStatus,
} from '../types'

export async function listRepos(): Promise<GitRepo[]> {
  const { data } = await apiClient.get<ApiResponse<GitRepo[]>>('/api/git/repos')
  return data.data
}

export async function getRepo(id: string): Promise<GitRepo> {
  const { data } = await apiClient.get<ApiResponse<GitRepo>>(`/api/git/repos/${id}`)
  return data.data
}

export async function cloneRepo(url: string, name: string, branch?: string): Promise<GitRepo> {
  const { data } = await apiClient.post<ApiResponse<GitRepo>>('/api/git/repos/clone', {
    url, name, branch,
  })
  return data.data
}

export async function addExistingRepo(path: string, name: string): Promise<GitRepo> {
  const { data } = await apiClient.post<ApiResponse<GitRepo>>('/api/git/repos/add', {
    path, name,
  })
  return data.data
}

export async function removeRepo(id: string): Promise<void> {
  await apiClient.delete(`/api/git/repos/${id}`)
}

export async function getStatus(id: string): Promise<GitStatus> {
  const { data } = await apiClient.get<ApiResponse<GitStatus>>(`/api/git/repos/${id}/status`)
  return data.data
}

export async function getLog(
  id: string,
  branch?: string,
  limit = 30,
  skip = 0,
): Promise<GitCommit[]> {
  const params = new URLSearchParams({ limit: String(limit), skip: String(skip) })
  if (branch) params.set('branch', branch)
  const { data } = await apiClient.get<ApiResponse<GitCommit[]>>(
    `/api/git/repos/${id}/log?${params}`,
  )
  return data.data
}

export async function getDiff(id: string, hash: string): Promise<GitDiff> {
  const { data } = await apiClient.get<ApiResponse<GitDiff>>(
    `/api/git/repos/${id}/commits/${hash}/diff`,
  )
  return data.data
}

export async function getBranches(id: string): Promise<GitBranch[]> {
  const { data } = await apiClient.get<ApiResponse<GitBranch[]>>(
    `/api/git/repos/${id}/branches`,
  )
  return data.data
}

export async function createBranch(id: string, name: string, startPoint?: string): Promise<GitBranch> {
  const { data } = await apiClient.post<ApiResponse<GitBranch>>(
    `/api/git/repos/${id}/branches`,
    { name, startPoint },
  )
  return data.data
}

export async function deleteBranch(id: string, name: string): Promise<void> {
  await apiClient.delete(`/api/git/repos/${id}/branches/${encodeURIComponent(name)}`)
}

export async function checkout(id: string, branch: string, createNew = false): Promise<GitRepo> {
  const { data } = await apiClient.post<ApiResponse<GitRepo>>(
    `/api/git/repos/${id}/checkout`,
    { branch, createNew },
  )
  return data.data
}

export async function pull(id: string, remote?: string): Promise<GitRepo> {
  const { data } = await apiClient.post<ApiResponse<GitRepo>>(
    `/api/git/repos/${id}/pull`,
    { remote },
  )
  return data.data
}

export async function push(id: string, remote?: string, branch?: string): Promise<void> {
  await apiClient.post(`/api/git/repos/${id}/push`, { remote, branch })
}

export async function fetchRemote(id: string, remote?: string): Promise<void> {
  await apiClient.post(`/api/git/repos/${id}/fetch`, { remote })
}

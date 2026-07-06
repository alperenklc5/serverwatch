import { apiClient, getAccessToken } from './axios'
import { API_URL } from '../lib/constants'
import type { ApiResponse, DirectoryListing, FileContent, FileEntry } from '../types'

export interface UploadResponse {
  path: string
  name: string
  size: number
}

export async function listDirectory(path: string, showHidden = false): Promise<DirectoryListing> {
  const { data } = await apiClient.get<ApiResponse<DirectoryListing>>(
    `/api/files/list?path=${encodeURIComponent(path)}&showHidden=${showHidden}`,
  )
  return data.data
}

export async function readFile(path: string): Promise<FileContent> {
  const { data } = await apiClient.get<ApiResponse<FileContent>>(
    `/api/files/read?path=${encodeURIComponent(path)}`,
  )
  return data.data
}

export async function writeFile(path: string, content: string, encoding = 'UTF-8'): Promise<FileEntry> {
  const { data } = await apiClient.post<ApiResponse<FileEntry>>('/api/files/write', {
    path, content, encoding,
  })
  return data.data
}

export async function createEntry(
  parentPath: string,
  name: string,
  type: 'FILE' | 'DIRECTORY',
  content = '',
): Promise<FileEntry> {
  const { data } = await apiClient.post<ApiResponse<FileEntry>>('/api/files/create', {
    path: parentPath, name, type, content,
  })
  return data.data
}

export async function deleteEntry(path: string, recursive = false): Promise<void> {
  await apiClient.delete(`/api/files?path=${encodeURIComponent(path)}&recursive=${recursive}`)
}

export async function moveEntry(sourcePath: string, targetPath: string): Promise<FileEntry> {
  const { data } = await apiClient.post<ApiResponse<FileEntry>>('/api/files/move', {
    sourcePath, targetPath,
  })
  return data.data
}

export async function copyEntry(sourcePath: string, targetPath: string): Promise<FileEntry> {
  const { data } = await apiClient.post<ApiResponse<FileEntry>>('/api/files/copy', {
    sourcePath, targetPath,
  })
  return data.data
}

export function uploadFile(
  targetDir: string,
  file: File,
  onProgress: (pct: number) => void,
  signal?: AbortSignal,
): Promise<UploadResponse> {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('targetPath', targetDir)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_URL}/api/files/upload`)

    const token = getAccessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    xhr.upload.addEventListener('progress', e => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
    })

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const resp = JSON.parse(xhr.responseText) as { data: UploadResponse }
          resolve(resp.data)
        } catch {
          reject(new Error('Invalid response'))
        }
      } else {
        reject(new Error(`Upload failed: ${xhr.status}`))
      }
    }

    xhr.onerror = () => reject(new Error('Network error'))
    signal?.addEventListener('abort', () => { xhr.abort(); reject(new Error('Cancelled')) })
    xhr.send(formData)
  })
}

export async function downloadFile(path: string): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(
    `/api/files/download?path=${encodeURIComponent(path)}`,
    { responseType: 'blob' },
  )
  return data
}

export async function chmodEntry(path: string, permissions: string): Promise<FileEntry> {
  const { data } = await apiClient.post<ApiResponse<FileEntry>>('/api/files/chmod', {
    path, permissions,
  })
  return data.data
}

export async function searchFiles(root: string, query: string, maxResults = 50): Promise<FileEntry[]> {
  const { data } = await apiClient.get<ApiResponse<FileEntry[]>>(
    `/api/files/search?root=${encodeURIComponent(root)}&query=${encodeURIComponent(query)}&maxResults=${maxResults}`,
  )
  return data.data
}

export async function getRoots(): Promise<string[]> {
  const { data } = await apiClient.get<ApiResponse<string[]>>('/api/files/roots')
  return data.data
}

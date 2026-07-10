import { apiClient } from './axios'
import type { ApiResponse, User, Permission, PermissionInfo } from '../types'

export async function getUsers(): Promise<User[]> {
  const { data } = await apiClient.get<ApiResponse<User[]>>('/api/auth/users')
  return data.data
}

export async function createUser(
  username:    string,
  email:       string,
  password:    string,
  displayName: string,
  permissions?: Permission[],
): Promise<User> {
  const { data } = await apiClient.post<ApiResponse<User>>('/api/auth/register', {
    username, email, password, displayName, permissions,
  })
  return data.data
}

export async function enableUser(userId: number): Promise<void> {
  await apiClient.patch(`/api/auth/users/${userId}/enable`)
}

export async function disableUser(userId: number): Promise<void> {
  await apiClient.patch(`/api/auth/users/${userId}/disable`)
}

export async function deleteUser(userId: number): Promise<void> {
  await apiClient.delete(`/api/auth/users/${userId}`)
}

export async function logoutAllSessions(): Promise<void> {
  await apiClient.post('/api/auth/logout-all')
}

export async function getUserPermissions(userId: number): Promise<PermissionInfo[]> {
  const { data } = await apiClient.get<ApiResponse<PermissionInfo[]>>(
    `/api/perm/perms/${userId}`,
  )
  return data.data
}

export async function setUserPermissions(userId: number, permissions: Permission[]): Promise<void> {
  await apiClient.put(`/api/perm/perms/${userId}`, { permissions })
}

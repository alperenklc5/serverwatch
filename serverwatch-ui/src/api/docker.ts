import { apiClient } from './axios'
import type {
  ApiResponse, ContainerInfo, ContainerStats, ContainerLogDTO,
  DockerInfoDTO, ImageDTO, InspectResponse,
} from '../types'

export async function listContainers(showAll = false): Promise<ContainerInfo[]> {
  const { data } = await apiClient.get<ApiResponse<ContainerInfo[]>>(
    `/api/docker/containers?all=${showAll}`,
  )
  return data.data
}

export async function inspectContainer(id: string): Promise<InspectResponse> {
  const { data } = await apiClient.get<ApiResponse<InspectResponse>>(
    `/api/docker/containers/${id}`,
  )
  return data.data
}

export async function getContainerStats(id: string): Promise<ContainerStats> {
  const { data } = await apiClient.get<ApiResponse<ContainerStats>>(
    `/api/docker/containers/${id}/stats`,
  )
  return data.data
}

export async function getContainerLogs(
  id: string,
  tail = 100,
): Promise<ContainerLogDTO> {
  const { data } = await apiClient.get<ApiResponse<ContainerLogDTO>>(
    `/api/docker/containers/${id}/logs?tail=${tail}&stdout=true&stderr=true`,
  )
  return data.data
}

export async function startContainer(id: string): Promise<void> {
  await apiClient.post(`/api/docker/containers/${id}/start`)
}

export async function stopContainer(id: string, timeout = 10): Promise<void> {
  await apiClient.post(`/api/docker/containers/${id}/stop?timeout=${timeout}`)
}

export async function restartContainer(id: string, timeout = 10): Promise<void> {
  await apiClient.post(`/api/docker/containers/${id}/restart?timeout=${timeout}`)
}

export async function removeContainer(id: string, force = false): Promise<void> {
  await apiClient.delete(`/api/docker/containers/${id}?force=${force}`)
}

export async function getDockerInfo(): Promise<DockerInfoDTO> {
  const { data } = await apiClient.get<ApiResponse<DockerInfoDTO>>('/api/docker/info')
  return data.data
}

export async function listImages(): Promise<ImageDTO[]> {
  const { data } = await apiClient.get<ApiResponse<ImageDTO[]>>('/api/docker/images')
  return data.data
}

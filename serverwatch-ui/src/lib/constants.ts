export const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8090'
export const WS_URL = (import.meta.env.VITE_WS_URL as string | undefined) ?? 'ws://localhost:8090/ws'

export const TOKEN_KEY = 'accessToken'
export const REFRESH_TOKEN_KEY = 'refreshToken'

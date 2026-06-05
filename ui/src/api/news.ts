import { get, post } from './request'

export interface NewsItem {
  title: string
  url: string
  source: string
  publishTime: string
  summary: string
  rank: number
  category: string
}

export interface NewsResponse {
  success: boolean
  message: string
  news: NewsItem[]
  updateTime: string
  count: number
}

export interface NewsStatus {
  newsCount: number
  lastUpdateTime: string
  service: string
  status: string
}

export interface RefreshResponse {
  success: boolean
  message: string
  count: number
  updateTime: string
}

export const newsApi = {
  getHotNews: () => get<NewsResponse>('/api/v16/news/hot'),

  refreshNews: () => post<RefreshResponse>('/api/v16/news/refresh'),

  getStatus: () => get<NewsStatus>('/api/v16/news/status')
}

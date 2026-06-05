import { ref, computed } from 'vue'
import type { SessionFolder, SessionMeta } from '@/types/chat'
import { STORAGE_KEYS } from '@/config/app'
import { debug } from '@/utils/debug'

const STORAGE_KEY = STORAGE_KEYS.SESSION_FOLDERS
const META_KEY = STORAGE_KEYS.SESSION_META

const folders = ref<SessionFolder[]>([])
const sessionMetaMap = ref<Map<string, SessionMeta>>(new Map())

const loadFromStorage = () => {
  try {
    const storedFolders = localStorage.getItem(STORAGE_KEY)
    if (storedFolders) {
      folders.value = JSON.parse(storedFolders)
    }
    const storedMeta = localStorage.getItem(META_KEY)
    if (storedMeta) {
      sessionMetaMap.value = new Map(Object.entries(JSON.parse(storedMeta)))
    }
  } catch (e) {
    debug.error('Failed to load session folders from storage:', e)
  }
}

const saveToStorage = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(folders.value))
    const metaObj = Object.fromEntries(sessionMetaMap.value)
    localStorage.setItem(META_KEY, JSON.stringify(metaObj))
  } catch (e) {
    debug.error('Failed to save session folders to storage:', e)
  }
}

export function useSessionFolders() {
  if (folders.value.length === 0 && sessionMetaMap.value.size === 0) {
    loadFromStorage()
  }

  const sortedFolders = computed(() => {
    return [...folders.value].sort((a, b) => a.order - b.order)
  })

  const createFolder = (name: string, color?: string) => {
    const newFolder: SessionFolder = {
      id: `folder_${crypto.randomUUID()}`,
      name,
      color,
      order: folders.value.length,
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    folders.value.push(newFolder)
    saveToStorage()
    return newFolder
  }

  const renameFolder = (folderId: string, name: string) => {
    const folder = folders.value.find(f => f.id === folderId)
    if (folder) {
      folder.name = name
      folder.updatedAt = Date.now()
      saveToStorage()
    }
  }

  const deleteFolder = (folderId: string) => {
    const index = folders.value.findIndex(f => f.id === folderId)
    if (index !== -1) {
      folders.value.splice(index, 1)
      // Move sessions from this folder to uncategorized
      sessionMetaMap.value.forEach((meta) => {
        if (meta.folderId === folderId) {
          meta.folderId = undefined
        }
      })
      saveToStorage()
    }
  }

  const getSessionMeta = (sessionId: string): SessionMeta => {
    return sessionMetaMap.value.get(sessionId) || {}
  }

  const setSessionMeta = (sessionId: string, meta: SessionMeta) => {
    const existing = sessionMetaMap.value.get(sessionId) || {}
    sessionMetaMap.value.set(sessionId, { ...existing, ...meta })
    saveToStorage()
  }

  const pinSession = (sessionId: string) => {
    setSessionMeta(sessionId, { isPinned: true })
  }

  const unpinSession = (sessionId: string) => {
    setSessionMeta(sessionId, { isPinned: false })
  }

  const moveToFolder = (sessionId: string, folderId: string | undefined) => {
    setSessionMeta(sessionId, { folderId })
  }

  const isPinned = (sessionId: string): boolean => {
    return getSessionMeta(sessionId).isPinned || false
  }

  const getSessionFolder = (sessionId: string): string | undefined => {
    return getSessionMeta(sessionId).folderId
  }

  return {
    folders: sortedFolders,
    createFolder,
    renameFolder,
    deleteFolder,
    pinSession,
    unpinSession,
    moveToFolder,
    isPinned,
    getSessionFolder,
    getSessionMeta
  }
}

import { useState, useEffect, useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { ProgressUpdate, ErrorEvent, DupeProgress, RepoRepoFile } from '../types'

interface WebSocketState {
  events: any[]
  connected: boolean
  activeProcesses: Record<string, ProgressUpdate>
  activeDupeProcesses: Record<string, DupeProgress>
  isAnyProcessRunning: boolean
  errors: ErrorEvent[]
  toast: { id: string; message: string; repo?: string } | null
  setToast: (toast: { id: string; message: string; repo?: string } | null) => void
  setErrors: React.Dispatch<React.SetStateAction<ErrorEvent[]>>
  dupeResults: Record<string, RepoRepoFile[][]>
  setDupeResults: React.Dispatch<React.SetStateAction<Record<string, RepoRepoFile[][]>>>
  isLoadingDupesManual: boolean
  setIsLoadingDupesManual: (v: boolean) => void
  globalDupes: RepoRepoFile[][] | null
  setGlobalDupes: (v: RepoRepoFile[][] | null) => void
  isLoadingGlobalDupes: boolean
  setIsLoadingGlobalDupes: (v: boolean) => void
  cancelMutation: any
  setActiveProcesses: React.Dispatch<React.SetStateAction<Record<string, ProgressUpdate>>>
}

export function useWebSocket(
  selectedRepoRef: React.MutableRefObject<string | null>,
  appConfigRef: React.MutableRefObject<{ verbose: boolean }>
): WebSocketState {
  const [events, setEvents] = useState<any[]>([])
  const [connected, setConnected] = useState(false)
  const [activeProcesses, setActiveProcesses] = useState<Record<string, ProgressUpdate>>({})
  const [activeDupeProcesses, setActiveDupeProcesses] = useState<Record<string, DupeProgress>>({})
  const [errors, setErrors] = useState<ErrorEvent[]>([])
  const [toast, setToast] = useState<{ id: string; message: string; repo?: string } | null>(null)
  const [dupeResults, setDupeResults] = useState<Record<string, RepoRepoFile[][]>>({})
  const [isLoadingDupesManual, setIsLoadingDupesManual] = useState(false)
  const [globalDupes, setGlobalDupes] = useState<RepoRepoFile[][] | null>(null)
  const [isLoadingGlobalDupes, setIsLoadingGlobalDupes] = useState(false)

  const toastTimeoutRef = useRef<any>(null)
  const pendingProgressRef = useRef<Record<string, ProgressUpdate>>({})
  const throttleTimerRef = useRef<any>(null)
  const queryClient = useQueryClient()

  const isAnyProcessRunning = Object.values(activeProcesses).length > 0

  const cancelMutation = useMutation({
    mutationFn: (name: string) => axios.post(`/api/repos/${name}/cancel`),
  })

  useEffect(() => {
    let ws: WebSocket | null = null
    let reconnectTimeout: any = null
    let shouldReconnect = true

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      ws = new WebSocket(`${protocol}//${window.location.host}/events`)

      ws.onopen = () => {
        setConnected(true)
        queryClient.invalidateQueries({ queryKey: ['repos'] })
      }

      ws.onclose = () => {
        setConnected(false)
        if (shouldReconnect) {
          reconnectTimeout = setTimeout(connect, 2000)
        }
      }

      ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        ws?.close()
      }

      ws.onmessage = (event) => {
        const data = JSON.parse(event.data)
        if (appConfigRef.current.verbose) {
          console.debug('[WS EVENT]', data.type, data.payload || data)
        }
        if (data.type === 'dupe-start' || data.type === 'dupe-processing-repo' || data.type === 'dupe-grouping-hamming' || data.type === 'dupe-finished') {
          const repoName = data.payload.repo || 'batch'
          setActiveDupeProcesses(prev => ({ ...prev, [repoName]: data.payload }))
          if (data.type === 'dupe-finished') {
            // dupe-finished from backend doesn't have groups yet, dupes-finished has.
          }
          return
        }
        if (data.type === 'dupes-finished') {
          const repoName = data.payload.repo || 'batch'
          setDupeResults(prev => ({ ...prev, [repoName]: data.payload.groups }))
          setActiveDupeProcesses(prev => {
            const next = { ...prev }
            delete next[repoName]
            return next
          })
          if (repoName === 'batch') {
            setGlobalDupes(data.payload.groups)
            setIsLoadingGlobalDupes(false)
          } else if (repoName === selectedRepoRef.current) {
            setIsLoadingDupesManual(false)
          }
          return
        }
        if (data.type === 'progress' && data.payload?.reset) {
          const repoName = data.payload.repo || 'default'
          const fresh = { repo: repoName, scanningActive: true, hashingActive: false, filesDiscovered: 0, directoriesDiscovered: 0, filesProcessed: 0, filesTotal: 0, progressPercent: 0 }
          pendingProgressRef.current[repoName] = fresh
          setActiveProcesses((prev) => ({ ...prev, [repoName]: fresh }))
          return
        }
        if (data.type === 'progress') {
          const repoName = data.payload.repo || 'default'
          const pending = pendingProgressRef.current[repoName]
          const merged = pending ? { ...pending } : { repo: repoName, scanningActive: true, hashingActive: false, filesDiscovered: 0, directoriesDiscovered: 0, filesProcessed: 0, filesTotal: 0, progressPercent: 0 }
          const incoming = data.payload
          Object.keys(incoming).forEach((key: string) => {
            if (incoming[key] !== undefined && incoming[key] !== null) {
              (merged as any)[key] = incoming[key]
            }
          })
          if (pending?.hashingActive && merged.hashingActive === false && merged.scanningActive !== false) {
            merged.hashingActive = true
          }
          if (pending?.filesTotal && merged.filesTotal && merged.filesTotal < pending.filesTotal) merged.filesTotal = pending.filesTotal
          if (pending?.filesProcessed && merged.filesProcessed && merged.filesProcessed < pending.filesProcessed) merged.filesProcessed = pending.filesProcessed
          if (pending?.filesDiscovered && merged.filesDiscovered && merged.filesDiscovered < pending.filesDiscovered) merged.filesDiscovered = pending.filesDiscovered
          if (pending?.directoriesDiscovered && merged.directoriesDiscovered && merged.directoriesDiscovered < pending.directoriesDiscovered) merged.directoriesDiscovered = pending.directoriesDiscovered
          const processed = merged.filesProcessed || 0
          const total = merged.filesTotal || 0
          if (total > 0) {
            merged.progressPercent = Math.min((processed / total) * 100, 100)
          }
          pendingProgressRef.current[repoName] = merged

          setActiveProcesses((prev) => {
            if (!prev[repoName]) {
              return { ...prev, [repoName]: merged }
            }
            return prev
          })

          if (!throttleTimerRef.current) {
            throttleTimerRef.current = setTimeout(() => {
              throttleTimerRef.current = null
              const snapshot = { ...pendingProgressRef.current }
              setActiveProcesses((prev) => {
                const next = { ...prev }
                Object.entries(snapshot).forEach(([repo, update]) => {
                  if (next[repo]) {
                    next[repo] = update
                  }
                })
                return next
              })
            }, 1000)
          }
        } else if (data.type === 'finished') {
          const repoName = data.payload?.repo || 'default'
          delete pendingProgressRef.current[repoName]
          if (Object.keys(pendingProgressRef.current).length === 0 && throttleTimerRef.current) {
            clearTimeout(throttleTimerRef.current)
            throttleTimerRef.current = null
          }
          setActiveProcesses((prev) => {
            const next = { ...prev }
            delete next[repoName]
            return next
          })
          queryClient.invalidateQueries({ queryKey: ['repos'] })
        } else if (data.type === 'error') {
          const repoName = data.payload?.repo || 'default'
          delete pendingProgressRef.current[repoName]
          if (Object.keys(pendingProgressRef.current).length === 0 && throttleTimerRef.current) {
            clearTimeout(throttleTimerRef.current)
            throttleTimerRef.current = null
          }
          setActiveProcesses((prev) => {
            const next = { ...prev }
            delete next[repoName]
            return next
          })
          const newError: ErrorEvent = {
            id: Math.random().toString(36).substring(2, 9),
            timestamp: Date.now(),
            repo: data.payload.repo,
            message: data.payload.message,
            read: false
          }
          setErrors(prev => [newError, ...prev])
          setToast({ id: newError.id, message: newError.message, repo: newError.repo })

          if (toastTimeoutRef.current) clearTimeout(toastTimeoutRef.current)
          toastTimeoutRef.current = setTimeout(() => {
            setToast(null)
          }, 5000)
        }
        setEvents((prev) => [data, ...prev].slice(0, 50))
      }
    }

    connect()

    return () => {
      shouldReconnect = false
      if (ws) ws.close()
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      if (toastTimeoutRef.current) clearTimeout(toastTimeoutRef.current)
    }
  }, [queryClient])

  return {
    events,
    connected,
    activeProcesses,
    activeDupeProcesses,
    isAnyProcessRunning,
    errors,
    toast,
    setToast,
    setErrors,
    dupeResults,
    setDupeResults,
    isLoadingDupesManual,
    setIsLoadingDupesManual,
    globalDupes,
    setGlobalDupes,
    isLoadingGlobalDupes,
    setIsLoadingGlobalDupes,
    cancelMutation,
    setActiveProcesses
  }
}

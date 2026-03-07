import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { Database, Activity, RefreshCw, Trash2, Plus, Folder, X, Search, FileText, ChevronRight, AlertTriangle, Bell, Trash, Copy, Move, Zap } from 'lucide-react'

interface RepoStats {
  fileCount: number;
  totalSize: number;
  mimeTypeDistribution: Record<string, number>;
}

interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
  codec?: 'JSON' | 'MESSAGEPACK';
  compressed?: boolean;
  stats?: RepoStats;
}

interface RepoFile {
  relativePath: string;
  size: number;
  hash: string;
}

interface RepoRepoFile {
  repo: Repo;
  repoFile: RepoFile;
}

interface ProgressUpdate {
  repo?: string;
  path?: string;
  currentFile?: string;
  status?: string;
  progressPercent?: number;
  filesProcessed?: number;
  filesTotal?: number;
  hashedProcessed?: number;
  hashedTotal?: number;
  unchangedProcessed?: number;
  unchangedTotal?: number;
  directoriesProcessed?: number;
  directoriesTotal?: number;
  deletedProcessed?: number;
  deletedTotal?: number;
  duration?: string;
  eta?: string;
  errors?: string;
  scanningActive?: boolean;
  filesDiscovered?: number;
  directoriesDiscovered?: number;
}

interface ErrorEvent {
  id: string;
  timestamp: number;
  repo?: string;
  message: string;
  read: boolean;
}

function App() {
  const [events, setEvents] = useState<any[]>([])
  const [connected, setConnected] = useState(false)
  const [activeProgress, setActiveProgress] = useState<ProgressUpdate | null>(null)
  const [smoothProgress, setSmoothProgress] = useState(0)
  const [showAddModal, setShowAddModal] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [errors, setErrors] = useState<ErrorEvent[]>([])
  const [toast, setToast] = useState<{ id: string; message: string; repo?: string } | null>(null)
  const toastTimeoutRef = useRef<any>(null)
  const [showPruneModal, setShowPruneModal] = useState<string | null>(null)
  const [showRelocateModal, setShowRelocateModal] = useState<Repo | null>(null)
  const [showCloneModal, setShowCloneModal] = useState<Repo | null>(null)
  const [showMoveModal, setShowMoveModal] = useState<Repo | null>(null)
  const [relocatePath, setRelocatePath] = useState('')
  const [cloneData, setCloneData] = useState({ destinationName: '', path: '' })
  const [moveData, setMoveData] = useState({ destinationName: '' })
  const [newRepo, setNewRepo] = useState<Repo>({ 
    name: '', 
    absolutePath: './Documents', 
    indices: 10,
    codec: 'MESSAGEPACK',
    compressed: false
  })
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const { data: repos, isLoading } = useQuery<Repo[]>({
    queryKey: ['repos'],
    queryFn: async () => {
      const response = await axios.get('/api/repos')
      return response.data
    },
  })

  const { data: dupes, isLoading: isLoadingDupes } = useQuery<RepoRepoFile[][]>({
    queryKey: ['dupes', selectedRepo],
    queryFn: async () => {
      if (!selectedRepo) return []
      const response = await axios.get(`/api/repos/${selectedRepo}/dupes`)
      // The API returns groups as RepoRepoFile[]
      // The backend structure is List<List<RepoRepoFile>>
      return response.data
    },
    enabled: !!selectedRepo,
  })

  const resetNewRepo = () => {
    setNewRepo({ name: '', absolutePath: './Documents', indices: 10, codec: 'MESSAGEPACK', compressed: false })
  }

  const createMutation = useMutation({
    mutationFn: (repo: Repo) => axios.post('/api/repos', repo),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
      return response.data
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to create repository'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const deleteMutation = useMutation({
    mutationFn: (name: string) => axios.delete(`/api/repos/${name}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to delete repository'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const updateMutation = useMutation({
    mutationFn: (name: string) => axios.post(`/api/repos/${name}/update`),
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to start update'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const pruneMutation = useMutation({
    mutationFn: (name: string) => axios.post(`/api/repos/${name}/prune`),
    onSuccess: () => {
      setShowPruneModal(null)
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to start prune'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const relocateMutation = useMutation({
    mutationFn: ({ name, path }: { name: string, path: string }) => axios.post(`/api/repos/${name}/relocate`, { path }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
      setShowRelocateModal(null)
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to relocate repository'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const cloneMutation = useMutation({
    mutationFn: ({ name, data }: { name: string, data: { destinationName: string, path: string } }) => axios.post(`/api/repos/${name}/cp`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
      setShowCloneModal(null)
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to clone repository'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  const moveRepoMutation = useMutation({
    mutationFn: ({ name, destinationName }: { name: string, destinationName: string }) => axios.post(`/api/repos/${name}/mv`, { destinationName }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
      setShowMoveModal(null)
    },
    onError: (error: any) => {
      const message = error.response?.data?.description || error.message || 'Failed to move repository'
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      }
      setErrors(prev => [newError, ...prev])
      setToast({ id: newError.id, message: newError.message })
    }
  })

  interface BrowserItem {
    name: string;
    path: string;
    isDirectory: boolean;
  }

  interface BrowserResponse {
    currentPath: string;
    parentPath: string | null;
    items: BrowserItem[];
  }

  const [showBrowser, setShowBrowser] = useState(false);
  const [browserData, setBrowserData] = useState<BrowserResponse | null>(null);
  const [browserOnSelect, setBrowserOnSelect] = useState<(path: string) => void>(() => () => {});

  const browseMutation = useMutation({
    mutationFn: async (path?: string) => {
      const response = await axios.get('/api/utils/browse', { params: { path } })
      return response.data as BrowserResponse
    },
    onSuccess: (data) => {
      setBrowserData(data);
      setShowBrowser(true);
    }
  })

  const openBrowser = (initialPath: string, onSelect: (path: string) => void) => {
    setBrowserOnSelect(() => onSelect);
    browseMutation.mutate(initialPath);
  };

  const BrowserModal = () => {
    if (!showBrowser || !browserData) return null;
    return (
      <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
        <div className="bg-slate-900 border border-slate-800 w-full max-w-2xl rounded-2xl shadow-2xl flex flex-col max-h-[80vh]">
          <div className="p-6 border-b border-slate-800 flex justify-between items-center">
            <h2 className="text-xl font-black text-white flex items-center gap-2">
              <Folder className="w-6 h-6 text-blue-500" />
              Browse Directory
            </h2>
            <button 
              onClick={() => setShowBrowser(false)}
              className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition-colors"
            >
              <X className="w-6 h-6" />
            </button>
          </div>
          
          <div className="p-4 bg-slate-950/50 border-b border-slate-800 flex items-center gap-2 overflow-x-auto">
            <button 
              onClick={() => browserData.parentPath && browseMutation.mutate(browserData.parentPath)}
              disabled={!browserData.parentPath}
              className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white disabled:opacity-30 shrink-0"
            >
              <ChevronRight className="w-5 h-5 rotate-180" />
            </button>
            <div className="text-xs font-mono text-slate-400 truncate py-1">
              {browserData.currentPath}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {browserData.items.length === 0 && (
              <div className="p-12 text-center text-slate-500 font-medium">
                No directories found.
              </div>
            )}
            <div className="grid grid-cols-1 gap-1">
              {browserData.items.map((item) => (
                <button
                  key={item.path}
                  onClick={() => browseMutation.mutate(item.path)}
                  className="flex items-center gap-3 p-3 hover:bg-blue-600/10 rounded-xl text-left group transition-all"
                >
                  <Folder className="w-5 h-5 text-blue-500 group-hover:scale-110 transition-transform" />
                  <span className="text-sm font-bold text-slate-200 group-hover:text-white truncate">{item.name}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="p-6 border-t border-slate-800 flex justify-end gap-3">
            <button
              onClick={() => setShowBrowser(false)}
              className="px-6 py-2.5 rounded-xl font-bold text-sm text-slate-400 hover:text-white transition-all"
            >
              Cancel
            </button>
            <button
              onClick={() => {
                browserOnSelect(browserData.currentPath);
                setShowBrowser(false);
              }}
              className="bg-blue-600 hover:bg-blue-500 text-white px-8 py-2.5 rounded-xl font-black text-sm transition-all shadow-lg shadow-blue-600/20"
            >
              Select Directory
            </button>
          </div>
        </div>
      </div>
    );
  };

  useEffect(() => {
    if (activeProgress) {
      const target = activeProgress.progressPercent || 0
      if (target > smoothProgress) {
        const timer = setTimeout(() => {
          setSmoothProgress(prev => {
            const diff = target - prev
            if (diff <= 0) return prev
            // Move 10% of the way to target, or at least 0.1%
            return prev + Math.max(diff * 0.1, 0.1)
          })
        }, 50)
        return () => clearTimeout(timer)
      } else if (target < smoothProgress) {
        setSmoothProgress(target)
      }
    } else {
      setSmoothProgress(0)
    }
  }, [activeProgress, smoothProgress])

  useEffect(() => {
    let ws: WebSocket | null = null;
    let reconnectTimeout: any = null;
    let shouldReconnect = true;

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
        if (data.type === 'progress') {
          setActiveProgress((prev: ProgressUpdate | null) => {
            // If we switch to a different repo, reset state
            if (!prev || (data.payload.repo && data.payload.repo !== prev.repo)) {
              return {
                ...data.payload,
                scanningActive: data.payload.scanningActive ?? true,
                filesDiscovered: data.payload.filesDiscovered ?? 0,
                directoriesDiscovered: data.payload.directoriesDiscovered ?? 0
              };
            }
            // Merge updates
            return { ...prev, ...data.payload };
          });
        } else if (data.type === 'finished') {
          setActiveProgress(null)
          queryClient.invalidateQueries({ queryKey: ['repos'] })
        } else if (data.type === 'error') {
          setActiveProgress(null)
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

    connect();

    return () => {
      shouldReconnect = false;
      if (ws) ws.close()
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      if (toastTimeoutRef.current) clearTimeout(toastTimeoutRef.current)
    }
  }, [queryClient])

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  const getTopMimeTypes = (stats?: RepoStats) => {
    if (!stats?.mimeTypeDistribution) return []
    const total = Object.values(stats.mimeTypeDistribution).reduce((a, b) => a + b, 0)
    return Object.entries(stats.mimeTypeDistribution)
      .sort(([, a], [, b]) => (b as any) - (a as any))
      .slice(0, 5)
      .map(([type, count]) => ({
        type,
        percentage: ((count as any) / total * 100).toFixed(1),
        count
      }))
  }

  const getMimeColor = (mime: string) => {
    const type = mime.toLowerCase();
    if (type.startsWith('image/')) return 'bg-blue-100 text-blue-800 border-blue-200';
    if (type.startsWith('video/')) return 'bg-orange-100 text-orange-800 border-orange-200';
    if (type.startsWith('audio/')) return 'bg-green-100 text-green-800 border-green-200';
    if (type.startsWith('text/')) return 'bg-yellow-100 text-yellow-800 border-yellow-200';
    if (type.includes('pdf')) return 'bg-red-100 text-red-800 border-red-200';
    if (type.includes('zip') || type.includes('compressed')) return 'bg-purple-100 text-purple-800 border-purple-200';
    return 'bg-slate-100 text-slate-800 border-slate-200';
  }

  const unreadErrorCount = errors.filter(e => !e.read).length

  return (
    <div className="min-h-screen w-full bg-slate-950 text-slate-50 p-6 md:p-10 pb-32">
      {/* Disconnection Overlay */}
      {!connected && (
        <div className="fixed inset-0 z-[200] bg-slate-950/80 backdrop-blur-xl flex items-center justify-center p-6 animate-in fade-in duration-500">
          <div className="bg-slate-900 border-2 border-red-500/50 rounded-[2.5rem] p-12 max-w-xl w-full shadow-[0_0_100px_rgba(239,68,68,0.2)] text-center relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-2 bg-gradient-to-r from-red-500 via-orange-500 to-red-500 animate-pulse" />
            
            <div className="bg-red-500/20 w-24 h-24 rounded-3xl flex items-center justify-center mx-auto mb-8 animate-bounce">
              <AlertTriangle className="w-12 h-12 text-red-500" />
            </div>
            
            <h2 className="text-4xl font-black text-white mb-4 tracking-tight uppercase">Connection Lost</h2>
            <p className="text-slate-400 text-lg mb-10 leading-relaxed font-medium">
              The application is disconnected. <br/>
              Attempting to reconnect automatically...
            </p>
            
            <div className="flex items-center justify-center gap-3 text-red-400 font-bold bg-red-500/10 py-4 px-8 rounded-2xl border border-red-500/20 inline-flex">
              <RefreshCw className="w-5 h-5 animate-spin" />
              <span className="uppercase tracking-widest text-sm">Searching for backend</span>
            </div>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toast && (
        <div 
          className="fixed bottom-10 right-10 z-[100] animate-in slide-in-from-right-10 fade-in duration-300 cursor-pointer"
          onClick={() => {
            setShowErrorModal(true)
            setToast(null)
          }}
        >
          <div className="bg-red-600 border border-red-500 rounded-2xl p-5 shadow-2xl shadow-red-900/40 flex items-start gap-4 max-w-md">
            <div className="bg-white/20 p-2 rounded-xl">
              <AlertTriangle className="w-6 h-6 text-white" />
            </div>
            <div className="flex-1">
              <div className="flex justify-between items-start mb-1">
                <span className="text-xs font-black uppercase tracking-widest text-red-100">Error Occurred</span>
                <button onClick={(e) => { e.stopPropagation(); setToast(null); }} className="text-white/60 hover:text-white">
                  <X className="w-4 h-4" />
                </button>
              </div>
              <p className="font-bold text-white mb-1">{toast.repo ? `[${toast.repo}] ` : ''}Operation failed</p>
              <p className="text-sm text-red-100/80 line-clamp-2">{toast.message}</p>
            </div>
          </div>
        </div>
      )}

      <div className="max-w-[1600px] mx-auto">
        <header className="mb-10 flex justify-between items-center">
          <h1 className="text-4xl font-extrabold flex items-center gap-3 text-white tracking-tight">
            <Database className="w-10 h-10 text-blue-500" />
            Dedup <span className="text-slate-500 font-light">Dashboard</span>
          </h1>
          <div className="flex gap-4">
            <button
              onClick={() => {
                setShowErrorModal(true)
                setErrors(prev => prev.map(e => ({ ...e, read: true })))
              }}
              className="relative p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-slate-400 hover:text-white transition-all hover:bg-slate-800"
              title="Error History"
            >
              <Bell className="w-5 h-5" />
              {unreadErrorCount > 0 && (
                <span className="absolute -top-1 -right-1 bg-red-600 text-white text-[10px] font-black w-5 h-5 flex items-center justify-center rounded-full border-2 border-slate-950">
                  {unreadErrorCount}
                </span>
              )}
            </button>
            {activeProgress && (
              <div className="bg-blue-500/10 border border-blue-500/20 px-4 py-2 rounded-xl flex flex-col items-end min-w-[220px]">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-[10px] uppercase font-bold text-blue-400 tracking-widest leading-none truncate max-w-[120px]">
                    Updating {activeProgress.repo}
                  </span>
                  <RefreshCw className="w-3 h-3 text-blue-500 animate-spin shrink-0" />
                </div>
                
                <div className="h-4 flex items-center">
                  {activeProgress.scanningActive && (
                    <div className="flex items-center gap-2 animate-pulse">
                      <Search className="w-3 h-3 text-emerald-400 shrink-0" />
                      <span className="text-[10px] font-bold text-emerald-400 whitespace-nowrap">
                        Scanning: {activeProgress.filesDiscovered || 0}
                      </span>
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 w-full mt-1">
                  <div className="flex-1 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-blue-500 transition-all duration-300 ease-linear" 
                      style={{ width: `${smoothProgress || 0}%` }}
                    ></div>
                  </div>
                  <span className="text-[10px] font-black text-blue-100 min-w-[35px] text-right">{Math.round(smoothProgress || 0)}%</span>
                </div>
              </div>
            )}
            <button 
              onClick={() => {
                setSelectedRepo(null)
                queryClient.invalidateQueries({ queryKey: ['repos'] })
              }}
              className={`px-5 py-2.5 rounded-xl flex items-center gap-2 transition-all font-bold text-sm ${!selectedRepo ? 'bg-slate-800 text-white shadow-lg shadow-blue-500/10 border border-slate-700' : 'text-slate-400 hover:text-white'}`}
            >
              <Activity className="w-4 h-4" />
              Overview
            </button>
            <button 
              onClick={() => setShowAddModal(true)}
              className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-xl flex items-center gap-2 transition-all font-bold text-sm shadow-lg shadow-blue-600/20"
            >
              <Plus className="w-5 h-5" />
              Add Repository
            </button>
          </div>
        </header>

        {activeProgress && (
          <div className="fixed bottom-0 left-0 right-0 z-40 p-4 md:p-6 animate-in slide-in-from-bottom-full duration-500">
            <div className="max-w-[1600px] mx-auto">
              <div className="bg-slate-900/90 backdrop-blur-md border border-blue-500/30 rounded-2xl p-4 md:p-6 shadow-2xl shadow-blue-500/20">
                <div className="flex flex-col md:flex-row gap-4 md:items-center justify-between mb-4">
                  <div className="flex items-center gap-4 flex-1 min-w-0">
                    <div className="bg-blue-600/20 p-2 rounded-xl shrink-0">
                      <RefreshCw className="w-6 h-6 text-blue-500 animate-spin" />
                    </div>
                    <div className="min-w-0 flex-1 flex flex-col gap-1">
                      <h3 className="text-lg font-black text-white flex items-center gap-2 truncate">
                        Updating <span className="text-blue-500 truncate">{activeProgress.repo}</span>
                      </h3>
                      <div className="flex flex-col h-10 justify-center">
                        <div className="h-4">
                          {activeProgress.scanningActive ? (
                            <div className="flex items-center gap-2 text-emerald-400 animate-pulse">
                              <Search className="w-3 h-3" />
                              <span className="text-[10px] font-black uppercase tracking-tighter">
                                Scanning... Found {activeProgress.filesDiscovered || 0} files
                              </span>
                            </div>
                          ) : activeProgress.currentFile ? (
                            <p className="text-[10px] font-black uppercase text-blue-400 tracking-tighter truncate mb-0.5">
                              Processing: <span className="text-slate-100 italic">{activeProgress.currentFile}</span>
                            </p>
                          ) : null}
                        </div>
                        <p className="text-xs text-slate-400 font-medium truncate h-4">{activeProgress.status}</p>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-6 shrink-0">
                    <div className="text-right min-w-[80px]">
                      <span className="text-[10px] uppercase font-bold text-slate-500 block mb-0.5">Progress</span>
                      <span className="text-xl font-black text-white tracking-tighter">{Math.round(smoothProgress || 0)}%</span>
                    </div>
                    <div className="text-right border-l border-slate-800 pl-6 min-w-[120px]">
                      <span className="text-[10px] uppercase font-bold text-slate-500 block mb-0.5">ETA</span>
                      <span className="text-xl font-black text-blue-400 tracking-tighter">
                        {activeProgress.scanningActive ? 'Scanning...' : (activeProgress.eta || '--:--:--')}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden mb-4">
                  <div 
                    className="h-full bg-blue-500 transition-all duration-300 ease-linear shadow-[0_0_10px_rgba(59,130,246,0.5)]" 
                    style={{ width: `${smoothProgress || 0}%` }}
                  ></div>
                </div>

                <div className="flex flex-wrap items-center gap-x-8 gap-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase font-bold text-slate-500">Duration</span>
                    <span className="text-xs font-bold text-slate-200">{activeProgress.duration || '0s'}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase font-bold text-slate-500">Files</span>
                    <span className="text-xs font-bold text-slate-200">{activeProgress.filesProcessed || 0} / {activeProgress.filesTotal || 0}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase font-bold text-slate-500">Dirs</span>
                    <span className="text-xs font-bold text-slate-200">{activeProgress.directoriesProcessed || 0} / {activeProgress.directoriesTotal || 0}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase font-bold text-slate-500">Errors</span>
                    <span className={`text-xs font-bold ${activeProgress.errors && activeProgress.errors !== 'none' && activeProgress.errors !== '0' ? 'text-red-400' : 'text-emerald-400'}`}>
                      {activeProgress.errors || '0'}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {!selectedRepo ? (
          <div className="flex flex-col xl:flex-row gap-10">
            {/* Repository List */}
            <section className="flex-1">
            <div className="flex justify-between items-center mb-6">
                <h2 className="text-2xl font-bold flex items-center gap-3 text-slate-200">
                  <Database className="w-6 h-6 text-blue-400" />
                  Your Repositories
                  {repos && <span className="text-sm font-normal bg-slate-800 text-slate-400 px-2.5 py-0.5 rounded-full">{repos.length}</span>}
                </h2>
                
                <div className="flex items-center gap-3">
                  <button 
                    onClick={() => {
                      if (repos) repos.forEach(r => updateMutation.mutate(r.name))
                    }}
                    disabled={!repos || repos.length === 0 || !!activeProgress}
                    className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2 border border-slate-700 disabled:opacity-30"
                  >
                    <RefreshCw className="w-4 h-4" />
                    Scan All Repos
                  </button>
                  <button 
                    disabled={!repos || repos.length === 0 || !!activeProgress}
                    className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2 border border-slate-700 disabled:opacity-30"
                  >
                    <Search className="w-4 h-4" />
                    Global Duplicate Check
                  </button>
                </div>
              </div>
              
              {isLoading ? (
                <div className="space-y-6">
                  {[1, 2, 3].map(i => (
                    <div key={i} className="animate-pulse bg-slate-900/50 border border-slate-800 p-8 rounded-2xl h-40"></div>
                  ))}
                </div>
              ) : (
                <div className="grid gap-6">
                  {repos?.length === 0 && (
                    <div className="bg-slate-900/30 border-2 border-dashed border-slate-800 p-20 rounded-2xl text-center">
                      <div className="w-20 h-20 bg-slate-800/50 rounded-full flex items-center justify-center mx-auto mb-6">
                        <Folder className="w-10 h-10 text-slate-600" />
                      </div>
                      <h3 className="text-xl font-bold text-slate-300 mb-2">No repositories yet</h3>
                      <p className="text-slate-500 max-w-sm mx-auto mb-8">Connect a directory to start identifying duplicate files across your storage.</p>
                      <button 
                        onClick={() => setShowAddModal(true)}
                        className="text-blue-400 hover:text-blue-300 font-bold underline underline-offset-4"
                      >
                        Create your first repository
                      </button>
                    </div>
                  )}
                  {repos?.map((repo) => (
                    <div key={repo.name} className="group bg-slate-900/80 border border-slate-800 rounded-2xl hover:border-blue-500/40 transition-all hover:shadow-2xl hover:shadow-blue-500/5 overflow-hidden flex flex-col">
                      {/* Top Content */}
                      <div className="p-6 md:p-8 flex flex-col md:flex-row gap-8 items-start md:items-center">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-3 mb-1">
                            <h3 
                              className="text-2xl font-black text-white group-hover:text-blue-400 transition-colors truncate cursor-pointer"
                              onClick={() => {
                                setMoveData({ destinationName: `${repo.name}` })
                                setShowMoveModal(repo)
                              }}
                              title="Click to rename"
                            >
                              {repo.name}
                            </h3>
                            <span className="text-[10px] uppercase tracking-widest font-black text-slate-500 bg-slate-800 px-2 py-0.5 rounded">
                              {repo.codec || 'MSG'}
                            </span>
                          </div>
                          <p 
                            className="text-slate-500 font-mono text-xs truncate max-w-md cursor-pointer hover:text-indigo-400 transition-colors" 
                            title="Click to move to another folder"
                            onClick={() => {
                              setRelocatePath(repo.absolutePath)
                              setShowRelocateModal(repo)
                            }}
                          >
                            {repo.absolutePath}
                          </p>
                        </div>

                        {/* Stats Section - Broad Layout */}
                        <div className="flex flex-wrap items-center gap-8 md:gap-12 text-slate-300">
                          {repo.stats && (
                            <>
                              <div className="flex flex-col">
                                <span className="text-[10px] uppercase font-bold text-slate-500 tracking-tighter mb-0.5">Files</span>
                                <span className="text-xl font-black text-blue-100">{repo.stats.fileCount.toLocaleString()}</span>
                              </div>
                              <div className="flex flex-col">
                                <span className="text-[10px] uppercase font-bold text-slate-500 tracking-tighter mb-0.5">Total Size</span>
                                <span className="text-xl font-black text-blue-100">{formatSize(repo.stats.totalSize)}</span>
                              </div>
                            </>
                          )}
                          
                          <div className="flex flex-col min-w-[200px]">
                            <span className="text-[10px] uppercase font-bold text-slate-500 tracking-tighter mb-1.5">MIME Distribution</span>
                            <div className="flex flex-wrap gap-1.5">
                              {getTopMimeTypes(repo.stats).length > 0 ? (
                                getTopMimeTypes(repo.stats).map((mime, idx) => (
                                  <div key={idx} className={`border ${getMimeColor(mime.type)} rounded-md px-2 py-0.5 text-[9px] font-black flex items-center gap-1.5 shadow-sm`}>
                                    <span className="truncate max-w-[60px]" title={mime.type}>{mime.type.split('/').pop()}</span>
                                    <span>{Math.round(parseFloat(mime.percentage))}%</span>
                                  </div>
                                ))
                              ) : (
                                <span className="text-[10px] text-slate-600 italic">No data yet</span>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Bottom Actions Section */}
                      <div className="mt-auto px-6 py-4 bg-slate-950/40 border-t border-slate-800/50 flex flex-wrap justify-between items-center gap-4">
                        <div className="flex items-center gap-3">
                          <button
                            onClick={() => setSelectedRepo(repo.name)}
                            disabled={!!activeProgress}
                            className="text-xs font-black uppercase tracking-widest text-blue-500 hover:text-white hover:bg-blue-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <Search className="w-4 h-4" />
                            Explorer
                          </button>
                          <button
                            onClick={() => updateMutation.mutate(repo.name)}
                            disabled={updateMutation.isPending || !!activeProgress}
                            className="text-xs font-black uppercase tracking-widest text-emerald-500 hover:text-white hover:bg-emerald-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <RefreshCw className={`w-4 h-4 ${updateMutation.isPending ? 'animate-spin' : ''}`} />
                            Update
                          </button>
                          <button
                            onClick={() => setShowPruneModal(repo.name)}
                            disabled={!!activeProgress}
                            className="text-xs font-black uppercase tracking-widest text-amber-500 hover:text-white hover:bg-amber-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
                            title="Remove missing files from index"
                          >
                            <Zap className="w-4 h-4" />
                            Cleanup
                          </button>
                          <button
                            onClick={() => {
                              setCloneData({ destinationName: `${repo.name}_copy`, path: repo.absolutePath })
                              setShowCloneModal(repo)
                            }}
                            disabled={!!activeProgress}
                            className="text-xs font-black uppercase tracking-widest text-cyan-500 hover:text-white hover:bg-cyan-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
                            title="Create a copy of this repository"
                          >
                            <Copy className="w-4 h-4" />
                            Clone
                          </button>
                        </div>

                        <button 
                          onClick={() => {
                            if (confirm(`CRITICAL: Are you sure you want to remove ${repo.name}?\n\nThis will remove the repository from Dedup, but will NOT delete your files on disk.`)) {
                              deleteMutation.mutate(repo.name)
                            }
                          }}
                          disabled={!!activeProgress}
                          className="p-2 text-slate-600 hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all ml-auto disabled:opacity-30 disabled:cursor-not-allowed"
                          title="Remove Repository"
                        >
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Live Events Sidebar */}
            <section className="xl:w-[400px]">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-bold flex items-center gap-2 text-slate-200">
                  <Activity className="w-5 h-5 text-emerald-400" />
                  Live Activity
                </h2>
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
                  <span className="text-[10px] text-slate-500 uppercase font-bold tracking-widest">Streaming</span>
                </div>
              </div>
              <div className="bg-slate-900/50 border border-slate-800 rounded-2xl h-[700px] flex flex-col overflow-hidden shadow-2xl relative">
                <div className="flex-1 overflow-y-auto p-5 space-y-4 font-mono text-[10px] scrollbar-thin scrollbar-thumb-slate-800">
                  {events.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-slate-600 italic space-y-4">
                      <Activity className="w-8 h-8 opacity-20" />
                      <p>Idle... System ready.</p>
                    </div>
                  ) : (
                    events.filter(e => e.type !== 'progress').map((event, i) => (
                      <div key={i} className={`border-l-2 ${event.type === 'error' ? 'border-red-500' : event.type === 'finished' ? 'border-emerald-500' : 'border-blue-500'} pl-4 py-3 bg-white/5 rounded-r-lg group hover:bg-white/10 transition-all shadow-sm mb-3`}>
                        <div className="flex justify-between mb-1">
                          <span className={`font-bold uppercase tracking-tighter ${event.type === 'error' ? 'text-red-400' : event.type === 'finished' ? 'text-emerald-400' : 'text-blue-400'}`}>
                            {event.type || 'EVENT'}
                          </span>
                          <span className="text-slate-600 text-[9px]">{new Date().toLocaleTimeString()}</span>
                        </div>
                        <div className="text-blue-100/90 leading-relaxed overflow-x-hidden">
                          {event.type === 'finished' ? (
                            <div className="flex items-center gap-2 text-emerald-100/90">
                              <Database className="w-3 h-3" />
                              <span>Repository <span className="font-bold">{event.payload.repo}</span> update finished.</span>
                            </div>
                          ) : event.type === 'error' ? (
                            <div className="text-red-100/90">
                              <p className="font-bold mb-0.5">{event.payload.repo ? `[${event.payload.repo}] ` : ''}Error:</p>
                              <p className="text-[9px] opacity-80">{event.payload.message}</p>
                            </div>
                          ) : (
                            <pre className="whitespace-pre-wrap font-mono">
                              {typeof event.payload === 'string' ? event.payload : JSON.stringify(event.payload, null, 2)}
                            </pre>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
                <div className="absolute bottom-0 inset-x-0 h-20 bg-gradient-to-t from-slate-950/80 to-transparent pointer-events-none"></div>
              </div>
            </section>
          </div>
        ) : (
          <section className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
            <div className="p-6 border-b border-slate-800 bg-slate-900/50 flex justify-between items-center">
              <div className="flex items-center gap-4">
                <button 
                  onClick={() => setSelectedRepo(null)}
                  className="p-2 hover:bg-slate-800 rounded-lg transition-colors text-slate-400 hover:text-white"
                >
                  <ChevronRight className="w-6 h-6 rotate-180" />
                </button>
                <div>
                  <h2 className="text-2xl font-bold text-blue-400">{selectedRepo}</h2>
                  <p className="text-sm text-slate-500">Duplicate Explorer</p>
                </div>
              </div>
              <div className="flex gap-4">
                <div className="relative">
                  <Search className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                  <input 
                    type="text" 
                    placeholder="Filter duplicates..." 
                    className="bg-slate-950 border border-slate-800 rounded-lg pl-10 pr-4 py-2 text-sm outline-none focus:border-blue-500 transition-all w-64"
                  />
                </div>
              </div>
            </div>
            
            <div className="p-6 min-h-[600px]">
              {isLoadingDupes ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-4">
                  <RefreshCw className="w-10 h-10 text-blue-500 animate-spin" />
                  <p className="text-slate-400">Scanning for duplicates...</p>
                </div>
              ) : dupes?.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-4 text-center">
                  <div className="w-16 h-16 bg-emerald-500/10 rounded-full flex items-center justify-center mb-2">
                    <Search className="w-8 h-8 text-emerald-500" />
                  </div>
                  <h3 className="text-xl font-bold">No Duplicates Found!</h3>
                  <p className="text-slate-500 max-w-md">Your repository looks clean. No duplicate file hashes were detected.</p>
                </div>
              ) : (
                <div className="space-y-6">
                  <p className="text-sm text-slate-400 mb-4">Found {dupes?.length} duplicate groups</p>
                  {dupes?.map((group, i) => (
                    <div key={i} className="bg-slate-950/50 border border-slate-800 rounded-xl overflow-hidden">
                      <div className="p-4 bg-slate-800/20 border-b border-slate-800 flex justify-between items-center">
                        <div className="flex items-center gap-2">
                          <FileText className="w-4 h-4 text-blue-400" />
                          <span className="font-mono text-sm font-bold text-blue-100">{group[0].repoFile.hash.substring(0, 10)}...</span>
                          <span className="text-[10px] text-slate-500 px-2 py-0.5 bg-slate-800 rounded">{(group[0].repoFile.size / 1024 / 1024).toFixed(2)} MB</span>
                        </div>
                        <span className="text-xs text-slate-500">{group.length} occurrences</span>
                      </div>
                      <div className="divide-y divide-slate-800/50">
                        {group.map((item, j) => (
                          <div key={j} className="p-4 flex justify-between items-center hover:bg-slate-800/20 transition-colors">
                            <div className="flex items-center gap-3">
                              <div className="p-2 bg-slate-900 rounded border border-slate-800">
                                  <FileText className="w-5 h-5 text-slate-400" />
                              </div>
                              <div>
                                <p className="text-sm font-medium text-slate-200">{item.repoFile.relativePath}</p>
                                <p className="text-[10px] text-slate-500 font-mono">{item.repo.absolutePath}</p>
                              </div>
                            </div>
                            <div className="flex gap-2">
                              <button className="text-[10px] font-bold uppercase tracking-wider text-slate-500 hover:text-white px-2 py-1 rounded transition-colors">Open</button>
                              <button className="text-[10px] font-bold uppercase tracking-wider text-red-500 hover:bg-red-500/10 px-2 py-1 rounded transition-colors">Delete</button>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        )}

        {/* Add Repository Modal */}
        {/* Modals Section */}
        {/* Prune Modal */}
        {showPruneModal && (
          <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-md shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
              <div className="p-8">
                <div className="flex justify-between items-center mb-6">
                  <h3 className="text-2xl font-black text-white flex items-center gap-3">
                    <Zap className="w-6 h-6 text-amber-500" />
                    Cleanup Repository
                  </h3>
                  <button onClick={() => setShowPruneModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                    <X className="w-5 h-5 text-slate-400" />
                  </button>
                </div>
              
                <div className="space-y-6 text-slate-300">
                  <p>Cleaning up will remove all missing files from the index for <span className="font-bold text-white">"{showPruneModal}"</span>.</p>
                  <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-4 flex gap-4">
                    <AlertTriangle className="w-6 h-6 text-amber-500 shrink-0" />
                    <p className="text-xs leading-relaxed text-amber-200/70">
                      This process cleans the index and keeps only existing files. 
                      It is safe for your files on disk, but may take some time depending on index size.
                    </p>
                  </div>
                </div>

                <div className="flex gap-3 mt-8">
                  <button 
                    onClick={() => setShowPruneModal(null)}
                    className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={() => pruneMutation.mutate(showPruneModal)}
                    disabled={pruneMutation.isPending}
                    className="flex-1 bg-amber-600 hover:bg-amber-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-amber-900/20 transition-all flex items-center justify-center gap-2"
                  >
                    {pruneMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Zap className="w-5 h-5" />}
                    Clean up missing files
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Relocate Modal */}
        {showRelocateModal && (
          <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
              <div className="p-8">
                <div className="flex justify-between items-center mb-6">
                  <h3 className="text-2xl font-black text-white flex items-center gap-3">
                    <Folder className="w-6 h-6 text-indigo-500" />
                    Move Repository Folder
                  </h3>
                  <button onClick={() => setShowRelocateModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                    <X className="w-5 h-5 text-slate-400" />
                  </button>
                </div>
              
                <div className="space-y-6">
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Repository</label>
                    <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm">
                      {showRelocateModal.name}
                    </div>
                  </div>

                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Folder Path</label>
                    <div className="flex gap-2">
                      <input 
                        type="text" 
                        value={relocatePath}
                        onChange={(e) => setRelocatePath(e.target.value)}
                        className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 transition-all font-mono text-sm"
                        placeholder="/absolute/path/to/data"
                      />
                      <button 
                        onClick={() => openBrowser(relocatePath, (path) => setRelocatePath(path))}
                        className="bg-slate-800 hover:bg-slate-700 text-slate-200 p-3 rounded-2xl transition-all"
                        title="Browse Directory"
                      >
                        <Folder className="w-5 h-5" />
                      </button>
                    </div>
                    <p className="mt-2 text-[10px] text-slate-500 italic px-1">Update the folder where this repository is stored. Files stay where they are.</p>
                  </div>
                </div>

                <div className="flex gap-3 mt-8">
                  <button 
                    onClick={() => setShowRelocateModal(null)}
                    className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={() => relocateMutation.mutate({ name: showRelocateModal.name, path: relocatePath })}
                    disabled={relocateMutation.isPending || !relocatePath}
                    className="flex-1 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-indigo-900/20 transition-all flex items-center justify-center gap-2"
                  >
                    {relocateMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Folder className="w-5 h-5" />}
                    Move Folder
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Clone Modal */}
        {showCloneModal && (
          <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
              <div className="p-8">
                <div className="flex justify-between items-center mb-6">
                  <h3 className="text-2xl font-black text-white flex items-center gap-3">
                    <Copy className="w-6 h-6 text-cyan-500" />
                    Clone Repository
                  </h3>
                  <button onClick={() => setShowCloneModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                    <X className="w-5 h-5 text-slate-400" />
                  </button>
                </div>
              
                <div className="space-y-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Source</label>
                      <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm truncate">
                        {showCloneModal.name}
                      </div>
                    </div>
                    <div>
                      <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Name</label>
                      <input 
                        type="text" 
                        value={cloneData.destinationName}
                        onChange={(e) => setCloneData(prev => ({ ...prev, destinationName: e.target.value }))}
                        className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 focus:border-cyan-500 transition-all font-bold"
                        placeholder="New Repository Name"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Folder Path</label>
                    <div className="flex gap-2">
                      <input 
                        type="text" 
                        value={cloneData.path}
                        onChange={(e) => setCloneData(prev => ({ ...prev, path: e.target.value }))}
                        className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 focus:border-cyan-500 transition-all font-mono text-sm"
                      />
                      <button 
                        onClick={() => openBrowser(cloneData.path, (path) => setCloneData(prev => ({ ...prev, path })))}
                        className="bg-slate-800 hover:bg-slate-700 text-slate-200 p-3 rounded-2xl transition-all"
                      >
                        <Folder className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                </div>

                <div className="flex gap-3 mt-8">
                  <button 
                    onClick={() => setShowCloneModal(null)}
                    className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={() => cloneMutation.mutate({ name: showCloneModal.name, data: cloneData })}
                    disabled={cloneMutation.isPending || !cloneData.destinationName || !cloneData.path}
                    className="flex-1 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-cyan-900/20 transition-all flex items-center justify-center gap-2"
                  >
                    {cloneMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Copy className="w-5 h-5" />}
                    Clone
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Move Modal */}
        {showMoveModal && (
          <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-md shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
              <div className="p-8">
                <div className="flex justify-between items-center mb-6">
                  <h3 className="text-2xl font-black text-white flex items-center gap-3">
                    <Move className="w-6 h-6 text-violet-500" />
                    Rename Repository
                  </h3>
                  <button onClick={() => setShowMoveModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                    <X className="w-5 h-5 text-slate-400" />
                  </button>
                </div>
              
                <div className="space-y-6">
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Current Name</label>
                    <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm">
                      {showMoveModal.name}
                    </div>
                  </div>

                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Repository Name</label>
                    <input 
                      type="text" 
                      value={moveData.destinationName}
                      onChange={(e) => setMoveData({ destinationName: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 text-white focus:outline-none focus:ring-2 focus:ring-violet-500/50 focus:border-violet-500 transition-all font-bold"
                      placeholder="e.g. My Photos"
                    />
                    <p className="mt-2 text-[10px] text-slate-500 italic px-1">This will change how the repository is identified in Dedup. Your files are not moved.</p>
                  </div>
                </div>

                <div className="flex gap-3 mt-8">
                  <button 
                    onClick={() => setShowMoveModal(null)}
                    className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                  >
                    Cancel
                  </button>
                  <button 
                    onClick={() => moveRepoMutation.mutate({ name: showMoveModal.name, destinationName: moveData.destinationName })}
                    disabled={moveRepoMutation.isPending || !moveData.destinationName}
                    className="flex-1 bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-violet-900/20 transition-all flex items-center justify-center gap-2"
                  >
                    {moveRepoMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Move className="w-5 h-5" />}
                    Rename
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {showAddModal && (
          <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
            <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl">
              <div className="p-6 border-b border-slate-800 flex justify-between items-center bg-slate-900/50">
                <h3 className="text-xl font-bold">Add New Repository</h3>
                <button onClick={() => setShowAddModal(false)} className="text-slate-500 hover:text-white">
                  <X className="w-6 h-6" />
                </button>
              </div>
              <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400">Absolute Path</label>
                  <div className="flex gap-2">
                    <input 
                      type="text" 
                      value={newRepo.absolutePath}
                      onChange={e => {
                        const path = e.target.value
                        setNewRepo(prev => {
                          const name = prev.name || path.split(/[/\\]/).pop() || ''
                          return { ...prev, absolutePath: path, name }
                        })
                      }}
                      className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                      placeholder="/home/user/music"
                    />
                    <button 
                      onClick={() => openBrowser(newRepo.absolutePath, (path) => {
                        setNewRepo((prev) => {
                          const name = prev.name || path.split(/[/\\]/).pop() || ''
                          return { ...prev, absolutePath: path, name }
                        })
                      })}
                      className="bg-slate-800 hover:bg-slate-700 text-white px-3 py-2 rounded-lg transition-colors border border-slate-700"
                      title="Browse Filesystem"
                    >
                      <Folder className="w-5 h-5" />
                    </button>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400">Repository Name</label>
                  <input 
                    type="text" 
                    value={newRepo.name}
                    onChange={e => setNewRepo({...newRepo, name: e.target.value})}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                    placeholder="e.g. My Music"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-400">Index Files</label>
                    <input 
                      type="number" 
                      value={newRepo.indices}
                      onChange={e => setNewRepo({...newRepo, indices: parseInt(e.target.value) || 1})}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                    />
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-400">Codec</label>
                    <select 
                      value={newRepo.codec}
                      onChange={e => {
                        const codec = e.target.value as 'JSON' | 'MESSAGEPACK'
                        setNewRepo({...newRepo, codec, compressed: codec === 'JSON' ? false : newRepo.compressed})
                      }}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all appearance-none"
                    >
                      <option value="MESSAGEPACK">MsgPack</option>
                      <option value="JSON">JSON</option>
                    </select>
                  </div>
                </div>
                <div className="flex items-center gap-2 pt-2">
                  <input 
                    type="checkbox" 
                    id="compressed"
                    checked={newRepo.compressed}
                    disabled={newRepo.codec === 'JSON'}
                    onChange={e => setNewRepo({...newRepo, compressed: e.target.checked})}
                    className="w-4 h-4 bg-slate-950 border-slate-800 rounded text-blue-600 focus:ring-blue-500 disabled:opacity-30"
                  />
                  <label 
                    htmlFor="compressed" 
                    className={`text-sm font-medium ${newRepo.codec === 'JSON' ? 'text-slate-600' : 'text-slate-400'}`}
                  >
                    Compressed Index (MsgPack only)
                  </label>
                </div>
              </div>
              <div className="p-6 bg-slate-900/50 border-t border-slate-800 flex justify-between items-center gap-3">
                <button 
                  onClick={() => {
                    setShowAddModal(false)
                    resetNewRepo()
                  }}
                  className="px-4 py-2 rounded-lg border border-slate-800 hover:bg-slate-800 transition-colors font-semibold text-sm"
                >
                  Cancel
                </button>
                <div className="flex gap-3">
                  <button 
                    onClick={() => {
                      createMutation.mutate(newRepo, {
                        onSuccess: () => {
                          resetNewRepo()
                        }
                      })
                    }}
                    disabled={!newRepo.name || !newRepo.absolutePath || createMutation.isPending}
                    className="px-4 py-2 rounded-lg border border-slate-700 hover:bg-slate-800 text-white font-semibold transition-colors text-sm"
                  >
                    Add Another
                  </button>
                  <button 
                    onClick={() => {
                      createMutation.mutate(newRepo, {
                        onSuccess: () => {
                          setShowAddModal(false)
                          resetNewRepo()
                        }
                      })
                    }}
                    disabled={!newRepo.name || !newRepo.absolutePath || createMutation.isPending}
                    className="bg-slate-700 hover:bg-slate-600 text-white px-4 py-2 rounded-lg font-semibold transition-colors text-sm"
                  >
                    Add
                  </button>
                  <button 
                    onClick={() => {
                      createMutation.mutate(newRepo, {
                        onSuccess: (response) => {
                          updateMutation.mutate(response.data.name)
                          setShowAddModal(false)
                          resetNewRepo()
                        }
                      })
                    }}
                    disabled={!newRepo.name || !newRepo.absolutePath || createMutation.isPending}
                    className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-semibold transition-colors text-sm"
                  >
                    {createMutation.isPending ? 'Adding...' : 'Add and Scan'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Error History Modal */}
        <BrowserModal />

        {showErrorModal && (
          <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
            <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl overflow-hidden shadow-2xl flex flex-col max-h-[80vh]">
              <div className="p-6 border-b border-slate-800 flex justify-between items-center bg-slate-900/50">
                <div className="flex items-center gap-3">
                  <div className="bg-red-500/10 p-2 rounded-lg">
                    <AlertTriangle className="w-5 h-5 text-red-500" />
                  </div>
                  <h3 className="text-xl font-bold">Error History</h3>
                </div>
                <div className="flex items-center gap-4">
                  {errors.length > 0 && (
                    <button 
                      onClick={() => setErrors([])}
                      className="text-xs font-bold uppercase tracking-widest text-slate-500 hover:text-red-400 flex items-center gap-2 transition-colors"
                    >
                      <Trash className="w-4 h-4" />
                      Clear All
                    </button>
                  )}
                  <button onClick={() => setShowErrorModal(false)} className="text-slate-500 hover:text-white">
                    <X className="w-6 h-6" />
                  </button>
                </div>
              </div>
              
              <div className="flex-1 overflow-y-auto p-6">
                {errors.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center py-20 text-center">
                    <div className="bg-slate-800/50 p-6 rounded-full mb-4">
                      <Bell className="w-12 h-12 text-slate-600" />
                    </div>
                    <h4 className="text-lg font-bold text-slate-400">No errors recorded</h4>
                    <p className="text-sm text-slate-600 mt-1">Errors encountered during background tasks will appear here.</p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {errors.map((error) => (
                      <div key={error.id} className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex gap-4 hover:border-red-500/30 transition-all group">
                        <div className="bg-red-500/5 p-2 rounded-lg self-start">
                          <AlertTriangle className="w-5 h-5 text-red-500" />
                        </div>
                        <div className="flex-1">
                          <div className="flex justify-between items-start mb-2">
                            <span className="text-[10px] font-black uppercase tracking-widest text-slate-500">
                              {new Date(error.timestamp).toLocaleString()}
                            </span>
                            <button 
                              onClick={() => setErrors(prev => prev.filter(e => e.id !== error.id))}
                              className="text-slate-600 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>
                          <div className="flex items-center gap-2 mb-1">
                            {error.repo && (
                              <span className="bg-red-500/10 text-red-400 text-[10px] font-black px-2 py-0.5 rounded border border-red-500/20 uppercase">
                                {error.repo}
                              </span>
                            )}
                            <h5 className="font-bold text-slate-100">Operation Failed</h5>
                          </div>
                          <p className="text-sm text-slate-400 leading-relaxed bg-slate-900/50 p-3 rounded-lg border border-white/5 mt-2">
                            {error.message}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              
              <div className="p-4 bg-slate-900/50 border-t border-slate-800 text-center">
                <button 
                  onClick={() => setShowErrorModal(false)}
                  className="bg-slate-800 hover:bg-slate-700 text-white px-8 py-2 rounded-xl font-bold transition-all text-sm"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default App

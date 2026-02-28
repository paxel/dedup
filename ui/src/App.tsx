import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { Database, Activity, RefreshCw, Trash2, Plus, Folder, X, Search, FileText, ChevronRight, AlertTriangle, Bell, Trash } from 'lucide-react'

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
  const [activeProgress, setActiveProgress] = useState<any>(null)
  const [showAddModal, setShowAddModal] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [errors, setErrors] = useState<ErrorEvent[]>([])
  const [toast, setToast] = useState<{ id: string; message: string; repo?: string } | null>(null)
  const toastTimeoutRef = useRef<any>(null)
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

  const browseMutation = useMutation({
    mutationFn: async (path?: string) => {
      const response = await axios.get('/api/utils/browse', { params: { path } })
      return response.data?.path
    },
    onSuccess: (path) => {
      if (path) {
        setNewRepo((prev) => {
          const name = prev.name || path.split(/[/\\]/).pop() || ''
          return { ...prev, absolutePath: path, name }
        })
      }
    }
  })

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
            if (!prev || (data.payload.repo && data.payload.repo !== prev.repo)) {
              return data.payload;
            }
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
              <div className="bg-blue-500/10 border border-blue-500/20 px-4 py-2 rounded-xl flex items-center gap-3">
                <div className="flex flex-col items-end">
                  <span className="text-[10px] uppercase font-bold text-blue-400 tracking-widest leading-none mb-1">Updating {activeProgress.repo}</span>
                  <div className="flex items-center gap-2">
                    <div className="w-32 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-blue-500 transition-all duration-500" 
                        style={{ width: `${activeProgress.progressPercent || 0}%` }}
                      ></div>
                    </div>
                    <span className="text-xs font-black text-blue-100">{Math.round(activeProgress.progressPercent || 0)}%</span>
                  </div>
                </div>
                <RefreshCw className="w-5 h-5 text-blue-500 animate-spin" />
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
                  <div className="flex items-center gap-4">
                    <div className="bg-blue-600/20 p-2 rounded-xl">
                      <RefreshCw className="w-6 h-6 text-blue-500 animate-spin" />
                    </div>
                    <div>
                      <h3 className="text-lg font-black text-white flex items-center gap-2">
                        Updating <span className="text-blue-500">{activeProgress.repo}</span>
                      </h3>
                      <p className="text-xs text-slate-400 font-medium truncate max-w-md">{activeProgress.status}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-6">
                    <div className="text-right">
                      <span className="text-[10px] uppercase font-bold text-slate-500 block mb-0.5">Progress</span>
                      <span className="text-xl font-black text-white tracking-tighter">{Math.round(activeProgress.progressPercent || 0)}%</span>
                    </div>
                    {activeProgress.eta && (
                      <div className="text-right border-l border-slate-800 pl-6">
                        <span className="text-[10px] uppercase font-bold text-slate-500 block mb-0.5">ETA</span>
                        <span className="text-xl font-black text-blue-400 tracking-tighter">{activeProgress.eta}</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden mb-4">
                  <div 
                    className="h-full bg-blue-500 transition-all duration-700 ease-out shadow-[0_0_10px_rgba(59,130,246,0.5)]" 
                    style={{ width: `${activeProgress.progressPercent || 0}%` }}
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
              <div className="flex justify-between items-center mb-8">
                <h2 className="text-2xl font-bold flex items-center gap-3 text-slate-200">
                  <Database className="w-6 h-6 text-blue-400" />
                  Your Repositories
                  {repos && <span className="text-sm font-normal bg-slate-800 text-slate-400 px-2.5 py-0.5 rounded-full">{repos.length}</span>}
                </h2>
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
                        <div className="flex-1 min-w-0 cursor-pointer" onClick={() => setSelectedRepo(repo.name)}>
                          <div className="flex items-center gap-3 mb-1">
                            <h3 className="text-2xl font-black text-white group-hover:text-blue-400 transition-colors truncate">{repo.name}</h3>
                            <span className="text-[10px] uppercase tracking-widest font-black text-slate-500 bg-slate-800 px-2 py-0.5 rounded">
                              {repo.codec || 'MSG'}
                            </span>
                          </div>
                          <p className="text-slate-500 font-mono text-xs truncate max-w-md" title={repo.absolutePath}>{repo.absolutePath}</p>
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
                            className="text-xs font-black uppercase tracking-widest text-blue-500 hover:text-white hover:bg-blue-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2"
                          >
                            <Search className="w-4 h-4" />
                            Duplicate Explorer
                          </button>
                          <button
                            onClick={() => updateMutation.mutate(repo.name)}
                            disabled={updateMutation.isPending}
                            className="text-xs font-black uppercase tracking-widest text-emerald-500 hover:text-white hover:bg-emerald-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-50"
                          >
                            <RefreshCw className={`w-4 h-4 ${updateMutation.isPending ? 'animate-spin' : ''}`} />
                            Update Index
                          </button>
                        </div>

                        <button 
                          onClick={() => {
                            if (confirm(`CRITICAL: Are you sure you want to remove ${repo.name}?\n\nThis will remove the repository from Dedup, but will NOT delete your files on disk.`)) {
                              deleteMutation.mutate(repo.name)
                            }
                          }}
                          className="p-2 text-slate-600 hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all ml-auto"
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
                      onClick={() => browseMutation.mutate(newRepo.absolutePath)}
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

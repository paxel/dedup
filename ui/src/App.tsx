import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { Database, Activity, RefreshCw, Plus, Folder, X, Search, ChevronRight, AlertTriangle, Bell, Zap } from 'lucide-react'

import { 
  Repo, 
  RepoRepoFile, 
  ErrorEvent, 
  formatSize
} from './types'

import { useWebSocket } from './hooks/useWebSocket'
import { DuplicateGroupsView } from './components/DuplicateGroupsView'
import { AddRepoModal } from './components/AddRepoModal'
import { ActionModals } from './components/ActionModals'
import { SimilarityModal } from './components/SimilarityModal'
import { BrowserModal } from './components/BrowserModal'
import { ErrorModal } from './components/ErrorModal'
import { LiveActivity } from './components/LiveActivity'
import { RepoCard } from './components/RepoCard'
import { ToolbarDropdown } from './components/ToolbarDropdown'
import { UpdateSettings } from './components/UpdateSettings'

function App() {
  const selectedRepoRef = useRef<string | null>(null)
  const [appConfig, setAppConfig] = useState<{ verbose: boolean }>({ verbose: false })
  const appConfigRef = useRef(appConfig)

  useEffect(() => { appConfigRef.current = appConfig }, [appConfig])
  useEffect(() => {
    axios.get('/api/config')
      .then(res => setAppConfig(res.data))
      .catch(err => console.error('Failed to load config', err))
  }, [])

  const ws = useWebSocket(selectedRepoRef, appConfigRef)
  const {
    events, connected, activeProcesses, activeDupeProcesses, isAnyProcessRunning,
    errors, setErrors, toast, setToast,
    dupeResults, setDupeResults, isLoadingDupesManual, setIsLoadingDupesManual,
    globalDupes, setGlobalDupes, isLoadingGlobalDupes, setIsLoadingGlobalDupes,
    cancelMutation, setActiveProcesses
  } = ws

  const [showAddModal, setShowAddModal] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [showGlobalDupes, setShowGlobalDupes] = useState(false)
  const [selectedReposForDupes, setSelectedReposForDupes] = useState<Set<string>>(new Set())
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
    codec: 'MESSAGEPACK'
  })
  
  const isValidRepoName = (name: string) => {
    if (!name || name.trim() === '') return false;
    // Match backend: letters, digits or underscores
    return /^[a-zA-Z0-9_]+$/.test(name);
  };

  const isRepoNameUnique = (name: string) => {
    return !repos?.some(r => r.name.toLowerCase() === name.toLowerCase());
  };

  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const { data: repos, isLoading } = useQuery<Repo[]>({
    queryKey: ['repos'],
    queryFn: async () => {
      const response = await axios.get('/api/repos')
      return response.data
    },
  })

  useQuery<RepoRepoFile[][]>({
    queryKey: ['dupes', selectedRepo],
    queryFn: async () => {
      if (!selectedRepo) return []
      setIsLoadingDupesManual(true)
      await axios.get(`/api/repos/${selectedRepo}/dupes`)
      return [] // We'll get the results via WebSocket
    },
    enabled: false, // Manual trigger only
  })

  const [showSimilarityModal, setShowSimilarityModal] = useState<{repoName: string | null, isGlobal: boolean} | null>(null)
  const [similarityThreshold, setSimilarityThreshold] = useState(95)

  const handleDuplicateClick = (name: string, threshold: number = 0) => {
    setSelectedRepo(name)
    selectedRepoRef.current = name
    setDupeResults(prev => {
      const next = { ...prev };
      delete next[name];
      return next;
    });
    setIsLoadingDupesManual(true);
    axios.get(`/api/repos/${name}/dupes?threshold=${threshold}`).catch(error => {
      setIsLoadingDupesManual(false);
      const message = error.response?.data?.message || error.message || 'Failed to start duplicate detection';
      const newError: ErrorEvent = {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: Date.now(),
        message,
        read: false
      };
      setErrors(prev => [newError, ...prev]);
      setToast({ id: newError.id, message: newError.message });
    });
  }

  const cancelDupeMutation = useMutation({
    mutationFn: (name?: string) => axios.post(`/api/repos/dupes/cancel${name ? `?name=${name}` : ''}`),
  })

  const resetNewRepo = () => {
    setNewRepo({ name: '', absolutePath: './Documents', indices: 10, codec: 'MESSAGEPACK' })
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

  const [updateOptions, setUpdateOptions] = useState({ threads: 2, refreshFingerprints: false })

  const batchUpdateMutation = useMutation({
    mutationFn: (names: string[]) => axios.post('/api/repos/update-batch', { repos: names, ...updateOptions }),
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
  const updateMutation = useMutation({
    mutationFn: (name: string) => axios.post(`/api/repos/${name}/update`, updateOptions),
    onMutate: (name: string) => {
      setActiveProcesses(prev => {
        const next = { ...prev }
        delete next[name]
        return next
      })
    },
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

  const [showBrowser, setShowBrowser] = useState(false)
  const [browserOnSelect, setBrowserOnSelect] = useState<(path: string) => void>(() => () => {})
  const [browserInitialPath, setBrowserInitialPath] = useState<string | undefined>(undefined)

  const openBrowser = (initialPath: string, onSelect: (path: string) => void) => {
    setBrowserOnSelect(() => onSelect)
    setBrowserInitialPath(initialPath)
    setShowBrowser(true)
  }

  const unreadErrorCount = errors.filter(e => !e.read).length

  return (
    <div className="min-h-screen w-full bg-slate-950 text-slate-50 px-4 py-6 pb-32">
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

      <div className="w-full">
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
            {Object.values(activeProcesses).map((proc) => (
              <div key={proc.repo} className="bg-blue-500/10 border border-blue-500/20 px-4 py-2 rounded-xl flex flex-col items-end min-w-[220px] mb-2 last:mb-0">
                <div className="flex items-center gap-2 mb-1 w-full justify-between">
                  <span className="text-[10px] uppercase font-bold text-blue-400 tracking-widest leading-none truncate max-w-[120px]">
                    {proc.repo}
                  </span>
                  <RefreshCw className="w-3 h-3 text-blue-500 animate-spin shrink-0" />
                </div>
              </div>
            ))}
            <button 
              onClick={() => {
                if (isLoadingGlobalDupes) cancelDupeMutation.mutate(undefined)
                setSelectedRepo(null)
                setShowGlobalDupes(false)
                queryClient.invalidateQueries({ queryKey: ['repos'] })
              }}
              className={`px-5 py-2.5 rounded-xl flex items-center gap-2 transition-all font-bold text-sm ${!selectedRepo && !showGlobalDupes ? 'bg-slate-800 text-white shadow-lg shadow-blue-500/10 border border-slate-700' : 'text-slate-400 hover:text-white'}`}
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

        {/* Floating progress overlays */}
        {Object.values(activeProcesses).length > 0 && (
          <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-3 w-full max-w-3xl px-4">
            {Object.values(activeProcesses).map((proc) => (
              <div key={proc.repo} className="flex flex-col gap-3">
                {/* Scan Overlay – floating card, visible while scanning */}
                {proc.scanningActive && (
                  <div className="bg-slate-900/95 border border-blue-500/30 backdrop-blur-xl rounded-2xl shadow-2xl shadow-blue-500/10 px-6 py-4">
                    <div className="flex items-center gap-4">
                      <div className="bg-blue-600/20 p-2.5 rounded-xl shrink-0">
                        <RefreshCw className="w-5 h-5 text-blue-400 animate-spin" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-[10px] uppercase font-bold text-blue-400 tracking-widest">Scanning — {proc.repo}</p>
                      </div>
                      <div className="flex items-center gap-4 text-sm">
                        <span className="text-slate-400"><span className="font-bold text-white">{proc.directoriesDiscovered || 0}</span> dirs</span>
                        <span className="text-slate-400"><span className="font-bold text-white">{proc.filesDiscovered || 0}</span> files</span>
                      </div>
                      <button
                        onClick={() => proc.repo && cancelMutation.mutate(proc.repo)}
                        className="bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 text-red-400 hover:text-red-300 px-3 py-1.5 rounded-lg text-xs font-bold uppercase tracking-widest transition-all flex items-center gap-1.5"
                      >
                        <X className="w-3.5 h-3.5" />
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
                {/* Hash Overlay – floating card, visible while hashing */}
                {proc.hashingActive && (
                  <div className="bg-slate-900/95 border border-emerald-500/30 backdrop-blur-xl rounded-2xl shadow-2xl shadow-emerald-500/10 px-6 py-4">
                    <div className="flex items-center gap-4">
                      <div className="bg-emerald-600/20 p-2.5 rounded-xl shrink-0">
                        <RefreshCw className="w-5 h-5 text-emerald-400 animate-spin" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-[10px] uppercase font-bold text-emerald-400 tracking-widest mb-1.5">Hashing — {proc.repo}</p>
                        <div className="w-full h-2 bg-slate-700/80 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-gradient-to-r from-emerald-500 to-emerald-400 transition-all duration-500 ease-linear rounded-full"
                            style={{ width: `${proc.progressPercent || 0}%` }}
                          />
                        </div>
                      </div>
                      <div className="flex items-center gap-2 text-sm whitespace-nowrap">
                        <span className="font-bold text-white">{proc.filesProcessed || 0}</span>
                        <span className="text-slate-500">/</span>
                        <span className="text-slate-400">{proc.filesTotal || 0}</span>
                        <span className="text-emerald-400 font-bold ml-1">{Math.round(proc.progressPercent || 0)}%</span>
                      </div>
                      <button
                        onClick={() => proc.repo && cancelMutation.mutate(proc.repo)}
                        className="bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 text-red-400 hover:text-red-300 px-3 py-1.5 rounded-lg text-xs font-bold uppercase tracking-widest transition-all flex items-center gap-1.5"
                      >
                        <X className="w-3.5 h-3.5" />
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        {showGlobalDupes ? (
          <section className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden">
            <div className="p-6 border-b border-slate-800 flex justify-between items-center">
              <div className="flex items-center gap-4">
                <button
                  onClick={() => {
                    if (isLoadingGlobalDupes) cancelDupeMutation.mutate(undefined)
                    setShowGlobalDupes(false)
                  }}
                  className="p-2 hover:bg-slate-800 rounded-lg transition-colors text-slate-400 hover:text-white"
                >
                  <ChevronRight className="w-6 h-6 rotate-180" />
                </button>
                <div>
                  <h2 className="text-2xl font-bold text-blue-400">Duplicates</h2>
                  <p className="text-sm text-slate-500">Across {Array.from(selectedReposForDupes).join(', ')}</p>
                </div>
              </div>
            </div>

            <div className="p-6 min-h-[600px]">
              {isLoadingGlobalDupes ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-6">
                  <RefreshCw className="w-12 h-12 text-blue-500 animate-spin" />
                  <div className="text-center space-y-2">
                    <p className="text-slate-200 font-bold text-lg">Scanning for duplicates across repositories...</p>
                    {activeDupeProcesses['batch'] && (
                      <div className="text-slate-400 text-sm font-mono max-w-md mx-auto">
                        {activeDupeProcesses['batch'].type === 'dupe-processing-repo' && (
                          <span>Processing repo: <span className="text-blue-400">{activeDupeProcesses['batch'].repo}</span> ({activeDupeProcesses['batch'].index! + 1}/{activeDupeProcesses['batch'].total})</span>
                        )}
                        {activeDupeProcesses['batch'].type === 'dupe-grouping-hamming' && (
                          <span>Grouping by similarity ({activeDupeProcesses['batch'].similarity}): {activeDupeProcesses['batch'].index}/{activeDupeProcesses['batch'].total}</span>
                        )}
                        {activeDupeProcesses['batch'].type === 'dupe-finished' && (
                          <span>Found {activeDupeProcesses['batch'].groupCount} groups. Finalizing...</span>
                        )}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => cancelDupeMutation.mutate(undefined)}
                    className="bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 text-red-400 px-6 py-2 rounded-xl font-bold uppercase tracking-widest transition-all"
                  >
                    Cancel Check
                  </button>
                </div>
              ) : globalDupes?.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-4 text-center">
                  <div className="w-16 h-16 bg-emerald-500/10 rounded-full flex items-center justify-center mb-2">
                    <Search className="w-8 h-8 text-emerald-500" />
                  </div>
                  <h3 className="text-xl font-bold">No Duplicates Found!</h3>
                  <p className="text-slate-500 max-w-md">No duplicate file hashes were detected across the selected repositories.</p>
                </div>
              ) : (
                <DuplicateGroupsView groups={globalDupes!} formatSize={formatSize} />
              )}
            </div>
          </section>
        ) : !selectedRepo ? (
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
                  <UpdateSettings
                    threads={updateOptions.threads}
                    refreshFingerprints={updateOptions.refreshFingerprints}
                    onChange={setUpdateOptions}
                  />
                  <ToolbarDropdown
                    repos={repos || []}
                    isDisabled={isAnyProcessRunning}
                    icon={<RefreshCw className="w-4 h-4 text-emerald-500" />}
                    label="Update"
                    actionLabel="Update"
                    actionIcon={<RefreshCw className="w-3 h-3" />}
                    actionClassName="bg-emerald-600 hover:bg-emerald-500"
                    onAction={(selected) => batchUpdateMutation.mutate(selected)}
                  />
                  <ToolbarDropdown
                    repos={repos || []}
                    isDisabled={isAnyProcessRunning}
                    icon={<Search className="w-4 h-4" />}
                    label="Duplicates"
                    actionLabel="Duplicates"
                    actionIcon={<Search className="w-3 h-3" />}
                    actionClassName="bg-blue-600 hover:bg-blue-500"
                    onAction={(selected) => {
                      setSelectedReposForDupes(new Set(selected))
                      setIsLoadingGlobalDupes(true)
                      setShowGlobalDupes(true)
                      setSelectedRepo(null)
                      setGlobalDupes(null)
                      axios.post(`/api/repos/dupes?threshold=0`, selected).catch(e => {
                        console.error('Global dupe check failed', e)
                        setIsLoadingGlobalDupes(false)
                        setGlobalDupes([])
                      })
                    }}
                  />
                  <ToolbarDropdown
                    repos={repos || []}
                    isDisabled={isAnyProcessRunning}
                    icon={<Zap className="w-4 h-4 text-indigo-500" />}
                    label="Similarity"
                    actionLabel="Similarity"
                    actionIcon={<Zap className="w-3 h-3" />}
                    actionClassName="bg-indigo-600 hover:bg-indigo-500"
                    checkboxAccent="accent-indigo-500"
                    onAction={() => {
                      setSimilarityThreshold(95)
                      setShowSimilarityModal({repoName: null, isGlobal: true})
                    }}
                  />
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
                    <RepoCard
                      key={repo.name}
                      repo={repo}
                      isAnyProcessRunning={isAnyProcessRunning}
                      onDuplicateClick={handleDuplicateClick}
                      onSimilarityClick={(r) => {
                        setSimilarityThreshold(95)
                        setShowSimilarityModal(r)
                      }}
                      updateMutation={updateMutation}
                      onPruneClick={(name) => setShowPruneModal(name)}
                      onDeleteClick={(name) => {
                        if (confirm(`CRITICAL: Are you sure you want to remove ${name}?\n\nThis will remove the repository from Dedup, but will NOT delete your files on disk.`)) {
                          deleteMutation.mutate(name)
                        }
                      }}
                      onMoveClick={(r) => {
                        setMoveData({ destinationName: r.name })
                        setShowMoveModal(r)
                      }}
                      onRelocateClick={(r) => {
                        setRelocatePath(r.absolutePath)
                        setShowRelocateModal(r)
                      }}
                      onCloneClick={(r) => {
                        setCloneData({ destinationName: `${r.name}_copy`, path: r.absolutePath })
                        setShowCloneModal(r)
                      }}
                    />
                  ))}
                </div>
              )}
            </section>

            <LiveActivity events={events} />
          </div>
        ) : (
          <section className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
            <div className="p-6 border-b border-slate-800 bg-slate-900/50 flex justify-between items-center">
              <div className="flex items-center gap-4">
                <button 
                  onClick={() => {
                    if (isLoadingDupesManual) cancelDupeMutation.mutate(selectedRepo!)
                    setSelectedRepo(null)
                  }}
                  className="p-2 hover:bg-slate-800 rounded-lg transition-colors text-slate-400 hover:text-white"
                >
                  <ChevronRight className="w-6 h-6 rotate-180" />
                </button>
                <div>
                  <h2 className="text-2xl font-bold text-blue-400">{selectedRepo}</h2>
                  <p className="text-sm text-slate-500">Duplicates</p>
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
              {isLoadingDupesManual ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-6">
                  <RefreshCw className="w-12 h-12 text-blue-500 animate-spin" />
                  <div className="text-center space-y-2">
                    <p className="text-slate-200 font-bold text-lg">Scanning for duplicates...</p>
                    {activeDupeProcesses[selectedRepo!] && (
                      <div className="text-slate-400 text-sm font-mono max-w-md mx-auto">
                        {activeDupeProcesses[selectedRepo!].type === 'dupe-processing-repo' && (
                          <span>Processing repo: <span className="text-blue-400">{activeDupeProcesses[selectedRepo!].repo}</span> ({activeDupeProcesses[selectedRepo!].index! + 1}/{activeDupeProcesses[selectedRepo!].total})</span>
                        )}
                        {activeDupeProcesses[selectedRepo!].type === 'dupe-grouping-hamming' && (
                          <span>Grouping by similarity ({activeDupeProcesses[selectedRepo!].similarity}): {activeDupeProcesses[selectedRepo!].index}/{activeDupeProcesses[selectedRepo!].total}</span>
                        )}
                        {activeDupeProcesses[selectedRepo!].type === 'dupe-finished' && (
                          <span>Found {activeDupeProcesses[selectedRepo!].groupCount} groups. Finalizing...</span>
                        )}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => cancelDupeMutation.mutate(selectedRepo!)}
                    className="bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 text-red-400 px-6 py-2 rounded-xl font-bold uppercase tracking-widest transition-all"
                  >
                    Cancel Check
                  </button>
                </div>
              ) : (dupeResults[selectedRepo!] || []).length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full py-20 space-y-4 text-center">
                  <div className="w-16 h-16 bg-emerald-500/10 rounded-full flex items-center justify-center mb-2">
                    <Search className="w-8 h-8 text-emerald-500" />
                  </div>
                  <h3 className="text-xl font-bold">No Duplicates Found!</h3>
                  <p className="text-slate-500 max-w-md">Your repository looks clean. No duplicate file hashes were detected.</p>
                </div>
              ) : (
                <DuplicateGroupsView groups={dupeResults[selectedRepo!] || []} formatSize={formatSize} />
              )}
            </div>
          </section>
        )}

        <ActionModals
          showPruneModal={showPruneModal}
          setShowPruneModal={setShowPruneModal}
          pruneMutation={pruneMutation}
          showRelocateModal={showRelocateModal}
          setShowRelocateModal={setShowRelocateModal}
          relocatePath={relocatePath}
          setRelocatePath={setRelocatePath}
          relocateMutation={relocateMutation}
          showCloneModal={showCloneModal}
          setShowCloneModal={setShowCloneModal}
          cloneData={cloneData}
          setCloneData={setCloneData}
          cloneMutation={cloneMutation}
          showMoveModal={showMoveModal}
          setShowMoveModal={setShowMoveModal}
          moveData={moveData}
          setMoveData={setMoveData}
          moveRepoMutation={moveRepoMutation}
          openBrowser={openBrowser}
        />

        <AddRepoModal
          show={showAddModal}
          onClose={() => setShowAddModal(false)}
          newRepo={newRepo}
          setNewRepo={setNewRepo}
          isValidRepoName={isValidRepoName}
          isRepoNameUnique={isRepoNameUnique}
          openBrowser={openBrowser}
          createMutation={createMutation}
          updateMutation={updateMutation}
          resetNewRepo={resetNewRepo}
        />

        <BrowserModal
          show={showBrowser}
          onClose={() => setShowBrowser(false)}
          onSelect={(path) => {
            browserOnSelect(path)
            setShowBrowser(false)
          }}
          initialPath={browserInitialPath}
        />

        <SimilarityModal
          showSimilarityModal={showSimilarityModal}
          setShowSimilarityModal={setShowSimilarityModal}
          similarityThreshold={similarityThreshold}
          setSimilarityThreshold={setSimilarityThreshold}
          onConfirm={(threshold) => {
            if (showSimilarityModal) {
              handleDuplicateClick(showSimilarityModal.repoName || '', threshold)
            }
          }}
        />

        {showErrorModal && (
          <ErrorModal
            errors={errors}
            setErrors={setErrors}
            onClose={() => setShowErrorModal(false)}
          />
        )}
      </div>
    </div>
  )
}

export default App

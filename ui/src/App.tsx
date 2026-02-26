import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { Database, Activity, RefreshCw, Trash2, Plus, Folder, X, Search, FileText, ChevronRight } from 'lucide-react'

interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
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

function App() {
  const [events, setEvents] = useState<any[]>([])
  const [showAddModal, setShowAddModal] = useState(false)
  const [newRepo, setNewRepo] = useState({ name: '', absolutePath: '', indices: 10 })
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

  const createMutation = useMutation({
    mutationFn: (repo: Repo) => axios.post('/api/repos', repo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
      setShowAddModal(false)
      setNewRepo({ name: '', absolutePath: '', indices: 10 })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (name: string) => axios.delete(`/api/repos/${name}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repos'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: (name: string) => axios.post(`/api/repos/${name}/update`),
  })

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${protocol}//${window.location.host}/events`)
    ws.onmessage = (event) => {
      const data = JSON.parse(event.data)
      setEvents((prev) => [data, ...prev].slice(0, 50))
    }
    return () => ws.close()
  }, [])

  return (
    <div className="min-h-screen w-full bg-slate-950 text-slate-50 p-8">
      <header className="mb-12 flex justify-between items-center">
        <h1 className="text-4xl font-bold flex items-center gap-3 text-white">
          <Database className="w-10 h-10 text-blue-500" />
          Dedup Dashboard
        </h1>
        <div className="flex gap-4">
          <button 
            onClick={() => {
              setSelectedRepo(null)
              queryClient.invalidateQueries({ queryKey: ['repos'] })
            }}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition-all font-semibold ${!selectedRepo ? 'bg-slate-800 text-white border border-slate-700' : 'text-slate-400 hover:text-white'}`}
          >
            Dashboard
          </button>
          <button 
            onClick={() => setShowAddModal(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg flex items-center gap-2 transition-colors font-semibold"
          >
            <Plus className="w-5 h-5" />
            Add Repository
          </button>
        </div>
      </header>

      {!selectedRepo ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Repository List */}
          <section className="lg:col-span-2">
            <h2 className="text-2xl font-semibold mb-6 flex items-center gap-2 text-slate-200">
              <Database className="w-6 h-6 text-blue-400" />
              Repositories
            </h2>
            {isLoading ? (
              <div className="space-y-4">
                {[1, 2, 3].map(i => (
                  <div key={i} className="animate-pulse bg-slate-900/50 border border-slate-800 p-6 rounded-xl h-24"></div>
                ))}
              </div>
            ) : (
              <div className="grid gap-4">
                {repos?.length === 0 && (
                  <div className="bg-slate-900/30 border border-dashed border-slate-800 p-12 rounded-xl text-center">
                    <Folder className="w-12 h-12 text-slate-700 mx-auto mb-4" />
                    <p className="text-slate-500">No repositories found. Add one to get started.</p>
                  </div>
                )}
                {repos?.map((repo) => (
                  <div key={repo.name} className="group bg-slate-900 border border-slate-800 p-6 rounded-xl hover:border-blue-500/50 transition-all hover:shadow-lg hover:shadow-blue-500/5">
                    <div className="flex justify-between items-start">
                      <div className="cursor-pointer" onClick={() => setSelectedRepo(repo.name)}>
                        <h3 className="text-xl font-bold text-blue-400 group-hover:text-blue-300 transition-colors">{repo.name}</h3>
                        <p className="text-slate-400 font-mono text-sm mt-1">{repo.absolutePath}</p>
                      </div>
                      <button 
                        onClick={() => {
                          if (confirm(`Are you sure you want to remove ${repo.name}?`)) {
                            deleteMutation.mutate(repo.name)
                          }
                        }}
                        className="p-2 text-slate-500 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-all"
                        title="Remove Repository"
                      >
                        <Trash2 className="w-5 h-5" />
                      </button>
                    </div>
                    <div className="mt-4 flex items-center justify-between">
                      <div className="flex gap-2">
                        <span className="text-[10px] uppercase tracking-wider font-bold text-slate-500 bg-slate-800/50 px-2 py-1 rounded">
                          Indices: {repo.indices}
                        </span>
                        <button
                          onClick={() => setSelectedRepo(repo.name)}
                          className="text-[10px] uppercase tracking-wider font-bold text-blue-500 hover:text-blue-400 transition-colors"
                        >
                          Find Duplicates →
                        </button>
                      </div>
                      <button
                        onClick={() => updateMutation.mutate(repo.name)}
                        disabled={updateMutation.isPending}
                        className="text-xs bg-blue-500/10 text-blue-400 hover:bg-blue-500 hover:text-white px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5 font-semibold"
                      >
                        <RefreshCw className={`w-3 h-3 ${updateMutation.isPending ? 'animate-spin' : ''}`} />
                        Update Index
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Live Events */}
          <section>
            <h2 className="text-2xl font-semibold mb-6 flex items-center gap-2 text-slate-200">
              <Activity className="w-6 h-6 text-emerald-400" />
              Live Events
            </h2>
            <div className="bg-slate-900 border border-slate-800 rounded-xl h-[600px] flex flex-col overflow-hidden shadow-xl">
              <div className="p-4 border-b border-slate-800 bg-slate-900/50 flex justify-between items-center">
                <span className="text-sm font-medium text-slate-400">Activity Stream</span>
                <RefreshCw className="w-4 h-4 text-emerald-500 animate-spin" />
              </div>
              <div className="flex-1 overflow-y-auto p-4 space-y-3 font-mono text-xs scrollbar-thin scrollbar-thumb-slate-800">
                {events.length === 0 ? (
                  <p className="text-slate-600 italic">Waiting for events...</p>
                ) : (
                  events.map((event, i) => (
                    <div key={i} className="border-l-2 border-blue-500 pl-3 py-2 bg-blue-500/5 rounded-r">
                      <pre className="whitespace-pre-wrap text-blue-100">{JSON.stringify(event, null, 2)}</pre>
                    </div>
                  ))
                )}
              </div>
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
            <div className="p-6 space-y-4">
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
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-400">Absolute Path</label>
                <input 
                  type="text" 
                  value={newRepo.absolutePath}
                  onChange={e => setNewRepo({...newRepo, absolutePath: e.target.value})}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                  placeholder="/home/user/music"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-400">Index Files</label>
                <input 
                  type="number" 
                  value={newRepo.indices}
                  onChange={e => setNewRepo({...newRepo, indices: parseInt(e.target.value) || 1})}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                />
              </div>
            </div>
            <div className="p-6 bg-slate-900/50 border-t border-slate-800 flex gap-3">
              <button 
                onClick={() => setShowAddModal(false)}
                className="flex-1 px-4 py-2 rounded-lg border border-slate-800 hover:bg-slate-800 transition-colors font-semibold"
              >
                Cancel
              </button>
              <button 
                onClick={() => createMutation.mutate(newRepo as Repo)}
                disabled={!newRepo.name || !newRepo.absolutePath || createMutation.isPending}
                className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg font-semibold transition-colors"
              >
                {createMutation.isPending ? 'Adding...' : 'Add Repository'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default App

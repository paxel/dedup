import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { Database, Activity, RefreshCw } from 'lucide-react'

interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
}

function App() {
  const [events, setEvents] = useState<any[]>([])

  const { data: repos, isLoading } = useQuery<Repo[]>({
    queryKey: ['repos'],
    queryFn: async () => {
      const response = await axios.get('/api/repos')
      return response.data
    },
  })

  useEffect(() => {
    const ws = new WebSocket(`ws://${window.location.host}/events`)
    ws.onmessage = (event) => {
      const data = JSON.parse(event.data)
      setEvents((prev) => [data, ...prev].slice(0, 50))
    }
    return () => ws.close()
  }, [])

  return (
    <div className="min-h-screen w-full bg-slate-950 text-slate-50 p-8">
      <header className="mb-12">
        <h1 className="text-4xl font-bold flex items-center gap-3">
          <Database className="w-10 h-10 text-blue-500" />
          Dedup Dashboard
        </h1>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Repository List */}
        <section className="lg:col-span-2">
          <h2 className="text-2xl font-semibold mb-4 flex items-center gap-2">
            <Database className="w-6 h-6" />
            Repositories
          </h2>
          {isLoading ? (
            <div className="animate-pulse flex space-x-4">
              <div className="flex-1 space-y-4 py-1">
                <div className="h-4 bg-slate-800 rounded w-3/4"></div>
                <div className="space-y-2">
                  <div className="h-4 bg-slate-800 rounded"></div>
                  <div className="h-4 bg-slate-800 rounded w-5/6"></div>
                </div>
              </div>
            </div>
          ) : (
            <div className="grid gap-4">
              {repos?.map((repo) => (
                <div key={repo.name} className="bg-slate-900 border border-slate-800 p-6 rounded-xl hover:border-blue-500 transition-colors">
                  <h3 className="text-xl font-bold text-blue-400">{repo.name}</h3>
                  <p className="text-slate-400 font-mono text-sm mt-1">{repo.absolutePath}</p>
                  <div className="mt-4 flex items-center gap-4">
                    <span className="text-xs bg-slate-800 px-2 py-1 rounded">
                      Indices: {repo.indices}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Live Events */}
        <section>
          <h2 className="text-2xl font-semibold mb-4 flex items-center gap-2">
            <Activity className="w-6 h-6" />
            Live Events
          </h2>
          <div className="bg-slate-900 border border-slate-800 rounded-xl h-[600px] flex flex-col overflow-hidden">
            <div className="p-4 border-b border-slate-800 bg-slate-900/50 flex justify-between items-center">
              <span className="text-sm font-medium text-slate-400">Activity Stream</span>
              <RefreshCw className="w-4 h-4 text-slate-500 animate-spin" />
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-3 font-mono text-xs">
              {events.length === 0 ? (
                <p className="text-slate-600 italic">Waiting for events...</p>
              ) : (
                events.map((event, i) => (
                  <div key={i} className="border-l-2 border-blue-500 pl-3 py-1 bg-slate-800/30 rounded-r">
                    <pre className="whitespace-pre-wrap">{JSON.stringify(event, null, 2)}</pre>
                  </div>
                ))
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

export default App

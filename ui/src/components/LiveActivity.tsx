import { Activity, Database } from 'lucide-react'

interface LiveActivityProps {
  events: any[]
}

export const LiveActivity = ({ events }: LiveActivityProps) => {
  return (
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
  )
}

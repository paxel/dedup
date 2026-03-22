import { RefreshCw, X } from 'lucide-react'

interface ProgressUpdate {
  repo?: string;
  progressPercent?: number;
  filesProcessed?: number;
  filesTotal?: number;
  scanningActive?: boolean;
  hashingActive?: boolean;
  filesDiscovered?: number;
  directoriesDiscovered?: number;
}

interface ProgressOverlaysProps {
  activeProcesses: Record<string, ProgressUpdate>;
  cancelMutation: any;
}

export const ProgressOverlays = ({ activeProcesses, cancelMutation }: ProgressOverlaysProps) => {
  if (Object.values(activeProcesses).length === 0) return null;

  return (
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
  );
}

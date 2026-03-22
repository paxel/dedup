import { AlertTriangle, Bell, Trash, X } from 'lucide-react'
import { ErrorEvent } from '../types'

interface ErrorModalProps {
  errors: ErrorEvent[]
  setErrors: React.Dispatch<React.SetStateAction<ErrorEvent[]>>
  onClose: () => void
}

export const ErrorModal = ({ errors, setErrors, onClose }: ErrorModalProps) => {
  return (
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
            <button onClick={onClose} className="text-slate-500 hover:text-white">
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
            onClick={onClose}
            className="bg-slate-800 hover:bg-slate-700 text-white px-8 py-2 rounded-xl font-bold transition-all text-sm"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}

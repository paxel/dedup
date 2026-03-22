import { X, Zap } from 'lucide-react'

interface SimilarityModalProps {
  showSimilarityModal: { repoName: string | null, isGlobal: boolean } | null;
  setShowSimilarityModal: (val: { repoName: string | null, isGlobal: boolean } | null) => void;
  similarityThreshold: number;
  setSimilarityThreshold: (val: number) => void;
  onConfirm: (threshold: number) => void;
}

export const SimilarityModal = ({
  showSimilarityModal,
  setShowSimilarityModal,
  similarityThreshold,
  setSimilarityThreshold,
  onConfirm
}: SimilarityModalProps) => {
  if (!showSimilarityModal) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 w-full max-w-md rounded-2xl shadow-2xl flex flex-col overflow-hidden">
        <div className="p-6 border-b border-slate-800 flex justify-between items-center">
          <h2 className="text-xl font-black text-white flex items-center gap-2">
            <Zap className="w-6 h-6 text-indigo-500" />
            Similarity Search
          </h2>
          <button
            onClick={() => setShowSimilarityModal(null)}
            className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition-colors"
          >
            <X className="w-6 h-6" />
          </button>
        </div>
        
        <div className="p-8 space-y-6">
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <label className="text-sm font-medium text-slate-400">Threshold (%)</label>
              <span className="text-xl font-black text-indigo-400">{similarityThreshold}%</span>
            </div>
            <input 
              type="number" 
              min="1"
              max="100"
              value={similarityThreshold}
              onChange={(e) => {
                const val = parseInt(e.target.value);
                if (!isNaN(val)) {
                  setSimilarityThreshold(Math.min(100, Math.max(1, val)));
                }
              }}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-3 text-lg font-bold text-white focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 outline-none transition-all"
              autoFocus
            />
            <p className="text-xs text-slate-500 italic">Enter a value between 1 and 100 for fuzzy matching.</p>
          </div>
        </div>

        <div className="p-6 bg-slate-950/50 border-t border-slate-800 flex justify-end gap-3">
          <button 
            onClick={() => setShowSimilarityModal(null)}
            className="px-6 py-2 rounded-xl border border-slate-800 hover:bg-slate-800 transition-colors font-bold text-sm uppercase tracking-widest text-slate-400"
          >
            Cancel
          </button>
          <button 
            onClick={() => {
              onConfirm(similarityThreshold);
              setShowSimilarityModal(null);
            }}
            className="bg-indigo-600 hover:bg-indigo-500 text-white px-8 py-2 rounded-xl font-black uppercase tracking-widest transition-all text-sm"
          >
            Check
          </button>
        </div>
      </div>
    </div>
  );
};

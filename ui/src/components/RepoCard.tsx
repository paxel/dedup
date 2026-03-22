import { Search, Zap, RefreshCw, Trash2, Copy } from 'lucide-react'
import { Repo, formatSize, getTopMimeTypes, getMimeColor } from '../types'

interface RepoCardProps {
  repo: Repo;
  isAnyProcessRunning: boolean;
  onDuplicateClick: (name: string) => void;
  onSimilarityClick: (repo: {repoName: string, isGlobal: boolean}) => void;
  updateMutation: any;
  onPruneClick: (name: string) => void;
  onDeleteClick: (name: string) => void;
  onMoveClick: (repo: Repo) => void;
  onRelocateClick: (repo: Repo) => void;
  onCloneClick: (repo: Repo) => void;
}

export const RepoCard = ({
  repo,
  isAnyProcessRunning,
  onDuplicateClick,
  onSimilarityClick,
  updateMutation,
  onPruneClick,
  onDeleteClick,
  onMoveClick,
  onRelocateClick,
  onCloneClick
}: RepoCardProps) => {
  return (
    <div key={repo.name} className="group bg-slate-900/80 border border-slate-800 rounded-2xl hover:border-blue-500/40 transition-all hover:shadow-2xl hover:shadow-blue-500/5 overflow-hidden flex flex-col">
      {/* Top Content */}
      <div className="p-6 md:p-8 flex flex-col md:flex-row gap-8 items-start md:items-center">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 mb-1">
            <h3 
              className="text-2xl font-black text-white group-hover:text-blue-400 transition-colors truncate cursor-pointer"
              onClick={() => onMoveClick(repo)}
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
            onClick={() => onRelocateClick(repo)}
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
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={() => onDuplicateClick(repo.name)}
            disabled={isAnyProcessRunning}
            className="text-xs font-black uppercase tracking-widest text-blue-500 hover:text-white hover:bg-blue-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed border border-blue-500/20"
            title="Find duplicate files within this repository"
          >
            <Search className="w-4 h-4" />
            Duplicates
          </button>
          <button
            onClick={() => onSimilarityClick({repoName: repo.name, isGlobal: false})}
            disabled={isAnyProcessRunning}
            className="text-xs font-black uppercase tracking-widest text-indigo-500 hover:text-white hover:bg-indigo-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed border border-indigo-500/20"
            title="Find similar files using perceptual hashing"
          >
            <Zap className="w-4 h-4" />
            Similarity
          </button>
          <button
            onClick={() => updateMutation.mutate(repo.name)}
            disabled={updateMutation.isPending || isAnyProcessRunning}
            className="text-xs font-black uppercase tracking-widest text-emerald-500 hover:text-white hover:bg-emerald-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed border border-emerald-500/20"
            title="Scan for new and changed files and update the index"
          >
            <RefreshCw className={`w-4 h-4 ${updateMutation.isPending ? 'animate-spin' : ''}`} />
            Update
          </button>
          <button
            onClick={() => onPruneClick(repo.name)}
            disabled={isAnyProcessRunning}
            className="text-xs font-black uppercase tracking-widest text-amber-500 hover:text-white hover:bg-amber-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
            title="Remove files from the index that no longer exist on disk"
          >
            <Zap className="w-4 h-4" />
            Cleanup
          </button>
          <button
            onClick={() => onCloneClick(repo)}
            disabled={isAnyProcessRunning}
            className="text-xs font-black uppercase tracking-widest text-cyan-500 hover:text-white hover:bg-cyan-600/20 px-3 py-1.5 rounded-lg transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed"
            title="Create a copy of this repository"
          >
            <Copy className="w-4 h-4" />
            Clone
          </button>
        </div>

          <button 
            onClick={() => onDeleteClick(repo.name)}
            disabled={isAnyProcessRunning}
            className="p-2 text-slate-600 hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all ml-auto disabled:opacity-30 disabled:cursor-not-allowed"
            title="Remove Repository"
          >
            <Trash2 className="w-5 h-5" />
          </button>
      </div>
    </div>
  )
}

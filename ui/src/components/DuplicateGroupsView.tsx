import { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import { Copy, RefreshCw, Trash2 } from 'lucide-react'
import { RepoRepoFile, getRelativePath, getSize, getHash, formatDate, formatFileSize } from '../types'
import { FilePreview } from './FilePreview'

interface DuplicateGroupsViewProps {
  groups: RepoRepoFile[][];
  formatSize: (bytes: number | undefined) => string;
}

const GROUPS_PAGE_SIZE = 20

export function DuplicateGroupsView({ groups, formatSize }: DuplicateGroupsViewProps) {
  const [kept, setKept] = useState<Record<number, Set<number>>>(() => {
    const init: Record<number, Set<number>> = {}
    groups.forEach((_, gi) => { init[gi] = new Set([0]) })
    return init
  })
  const [deleting, setDeleting] = useState(false)
  const [deleteResult, setDeleteResult] = useState<{ deleted: number; errors: string[] } | null>(null)
  const [visibleCount, setVisibleCount] = useState(GROUPS_PAGE_SIZE)
  const loadMoreRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = loadMoreRef.current
    if (!el) return
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) { setVisibleCount(prev => Math.min(prev + GROUPS_PAGE_SIZE, groups.length)) } },
      { rootMargin: '400px' }
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [groups.length, visibleCount])

  const toggleKeep = (gi: number, fi: number) => {
    setKept(prev => {
      const next = { ...prev }
      const s = new Set(prev[gi] || [])
      if (s.has(fi)) s.delete(fi)
      else s.add(fi)
      next[gi] = s
      return next
    })
  }

  const handleDeleteUnselected = async () => {
    if (!confirm('Are you sure you want to delete all UNSELECTED files? This cannot be undone.')) return
    const toDelete: { repoName: string; repoPath: string; relativePath: string; size: number }[] = []
    groups.forEach((group, gi) => {
      group.forEach((item, fi) => {
        if (!(kept[gi] || new Set()).has(fi)) {
          toDelete.push({
            repoName: item.repo.name,
            repoPath: item.repo.absolutePath,
            relativePath: getRelativePath(item.repoFile),
            size: getSize(item.repoFile),
          })
        }
      })
    })
    if (toDelete.length === 0) return
    setDeleting(true)
    try {
      const res = await axios.post('/api/files/delete', toDelete)
      setDeleteResult(res.data)
    } catch (e: any) {
      setDeleteResult({ deleted: 0, errors: [e.message || 'Delete failed'] })
    } finally {
      setDeleting(false)
    }
  }

  const totalUnselected = groups.reduce((acc, group, gi) => {
    return acc + group.filter((_, fi) => !(kept[gi] || new Set()).has(fi)).length
  }, 0)

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center mb-4">
        <p className="text-sm text-slate-400">
          Found {groups.length} duplicate group{groups.length !== 1 ? 's' : ''}.
          Select the files you want to <strong className="text-white">KEEP</strong>. Unselected files will be deleted.
        </p>
      </div>

      <div className="space-y-8">
        {groups.slice(0, visibleCount).map((group, gi) => (
          <div key={gi} className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden">
            <div className="px-5 py-3 bg-slate-800/40 border-b border-slate-800 flex justify-between items-center">
              <div className="flex items-center gap-3">
                <Copy className="w-4 h-4 text-blue-400" />
                <span className="font-bold text-slate-200">Group {gi + 1}</span>
                <span className="text-[10px] text-slate-500 bg-slate-800 px-2 py-0.5 rounded">
                  {group[0]?.repoFile ? getHash(group[0].repoFile).substring(0, 12) : ''}...
                </span>
                <span className="text-[10px] text-slate-500 bg-slate-800 px-2 py-0.5 rounded">
                  {group[0]?.repoFile ? formatSize(getSize(group[0].repoFile)) : ''}
                </span>
              </div>
              <span className="text-xs text-slate-500">{group.length} files</span>
            </div>
            <div className="flex overflow-x-auto gap-4 p-4">
              {group.map((item, fi) => {
                const isKept = (kept[gi] || new Set()).has(fi)
                const rf = item.repoFile
                const absPath = item.repo.absolutePath + '/' + getRelativePath(rf)
                return (
                  <div
                    key={fi}
                    className={`flex-shrink-0 w-64 border rounded-xl p-3 flex flex-col transition-all cursor-pointer ${
                      isKept
                        ? 'border-emerald-500/60 bg-emerald-500/5'
                        : 'border-slate-700 bg-slate-950/50 opacity-60'
                    }`}
                    onClick={() => toggleKeep(gi, fi)}
                  >
                    <FilePreview absolutePath={absPath} mimeType={rf?.m} />
                    <div className="mt-2 text-xs space-y-1 flex-grow">
                      <p className="font-medium text-slate-200 truncate" title={getRelativePath(rf)}>{getRelativePath(rf)}</p>
                      <p className="text-slate-500"><strong>Repo:</strong> {item.repo.name}</p>
                      <p className="text-slate-500"><strong>Size:</strong> {formatFileSize(getSize(rf))}</p>
                      {rf?.is && <p className="text-slate-500"><strong>Image:</strong> {rf.is.width}×{rf.is.height}</p>}
                      <p className="text-slate-500"><strong>Modified:</strong> {formatDate(rf?.l)}</p>
                    </div>
                    <div className="mt-2 pt-2 border-t border-slate-800 flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={isKept}
                        onChange={() => toggleKeep(gi, fi)}
                        onClick={e => e.stopPropagation()}
                        className="accent-emerald-500 w-4 h-4"
                      />
                      <span className={`text-xs font-bold ${isKept ? 'text-emerald-400' : 'text-slate-500'}`}>
                        {isKept ? 'Keep' : 'Will delete'}
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        ))}

        {visibleCount < groups.length && (
          <div ref={loadMoreRef} className="h-20 flex items-center justify-center">
            <RefreshCw className="w-6 h-6 animate-spin text-slate-600" />
          </div>
        )}
      </div>

      {deleteResult && (
        <div className={`mt-8 p-6 rounded-2xl border ${deleteResult.errors.length > 0 ? 'bg-red-900/20 border-red-500/50' : 'bg-emerald-900/20 border-emerald-500/50'}`}>
          <h4 className="font-black text-lg mb-2">Results</h4>
          <p className="text-sm">Successfully deleted {deleteResult.deleted} files.</p>
          {deleteResult.errors.length > 0 && (
            <div className="mt-4">
              <p className="text-xs font-bold text-red-400 uppercase tracking-widest mb-2">Errors</p>
              <ul className="text-xs text-red-300 list-disc list-inside space-y-1">
                {deleteResult.errors.map((err, i) => <li key={i}>{err}</li>)}
              </ul>
            </div>
          )}
          <button
            onClick={() => { setDeleteResult(null); window.location.reload(); }}
            className="mt-6 bg-white/10 hover:bg-white/20 text-white font-bold py-2 px-6 rounded-xl text-xs transition-all"
          >
            Close & Refresh
          </button>
        </div>
      )}

      <div className="fixed bottom-10 right-10 z-[50] flex flex-col items-end gap-4 animate-in slide-in-from-bottom-10">
        <button
          onClick={handleDeleteUnselected}
          disabled={deleting || totalUnselected === 0}
          className="bg-red-600 hover:bg-red-500 disabled:opacity-30 disabled:cursor-not-allowed text-white font-bold py-3 px-10 rounded-2xl text-sm uppercase tracking-widest transition-all shadow-lg shadow-red-900/30 flex items-center gap-2"
        >
          {deleting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
          Delete {totalUnselected} Unselected File{totalUnselected !== 1 ? 's' : ''}
        </button>
      </div>
    </div>
  )
}

import { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import { Copy, RefreshCw, Trash2, SkipForward, ChevronRight, Zap } from 'lucide-react'
import { RepoRepoFile, getRelativePath, getSize, getHash, formatDate, formatFileSize } from '../types'
import { FilePreview } from './FilePreview'

interface DuplicateGroupsViewProps {
  dupeKey: string;
  totalGroups: number;
  totalFiles: number;
  formatSize: (bytes: number | undefined) => string;
  onDone?: () => void;
}

const BATCH_SIZE_OPTIONS = [50, 100, 200]
const GROUPS_PAGE_SIZE = 20

export function DuplicateGroupsView({ dupeKey, totalGroups, totalFiles, formatSize, onDone }: DuplicateGroupsViewProps) {
  const [batchSize, setBatchSize] = useState(100)
  const [offset, setOffset] = useState(0)
  const [groups, setGroups] = useState<RepoRepoFile[][]>([])
  const [loading, setLoading] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [kept, setKept] = useState<Record<number, Set<number>>>({})
  const [deleting, setDeleting] = useState(false)
  const [deleteResult, setDeleteResult] = useState<{ deleted: number; errors: string[] } | null>(null)
  const [totalDeleted, setTotalDeleted] = useState(0)
  const [totalSkipped, setTotalSkipped] = useState(0)
  const [visibleCount, setVisibleCount] = useState(GROUPS_PAGE_SIZE)
  const loadMoreRef = useRef<HTMLDivElement>(null)

  const batchNumber = Math.floor(offset / batchSize) + 1
  const totalBatches = Math.ceil(totalGroups / batchSize)

  const fetchBatch = async (newOffset: number) => {
    setLoading(true)
    setDeleteResult(null)
    try {
      const res = await axios.get(`/api/repos/${dupeKey}/dupes/batch?offset=${newOffset}&limit=${batchSize}`)
      const data = res.data
      setGroups(data.groups)
      setHasMore(data.hasMore)
      setOffset(newOffset)
      setVisibleCount(GROUPS_PAGE_SIZE)
      // Default: keep first file in each group
      const init: Record<number, Set<number>> = {}
      data.groups.forEach((_: RepoRepoFile[], gi: number) => { init[gi] = new Set([0]) })
      setKept(init)
    } catch (e: any) {
      console.error('Failed to fetch batch', e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (totalGroups > 0) {
      fetchBatch(0)
    }
  }, [totalGroups, dupeKey])

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

  const collectUnselected = () => {
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
    return toDelete
  }

  const handleDeleteAndNext = async () => {
    const toDelete = collectUnselected()
    if (toDelete.length > 0) {
      setDeleting(true)
      try {
        const res = await axios.post('/api/files/delete', toDelete)
        setTotalDeleted(prev => prev + res.data.deleted)
        setDeleteResult(res.data)
        if (res.data.errors.length > 0) {
          return // Stay on this batch if there were errors
        }
      } catch (e: any) {
        setDeleteResult({ deleted: 0, errors: [e.message || 'Delete failed'] })
        return
      } finally {
        setDeleting(false)
      }
    }
    if (hasMore) {
      fetchBatch(offset + batchSize)
    } else {
      handleFinished()
    }
  }

  const handleSkip = () => {
    setTotalSkipped(prev => prev + groups.length)
    if (hasMore) {
      fetchBatch(offset + batchSize)
    } else {
      handleFinished()
    }
  }

  const handleDeleteAllRemaining = async () => {
    if (!confirm('Delete all unselected files in ALL remaining batches using default selection (keep first file)? This cannot be undone.')) return
    setDeleting(true)
    let currentOffset = offset
    let batchDeleted = 0

    // First delete current batch
    const toDelete = collectUnselected()
    if (toDelete.length > 0) {
      try {
        const res = await axios.post('/api/files/delete', toDelete)
        batchDeleted += res.data.deleted
      } catch (e: any) {
        setDeleteResult({ deleted: batchDeleted, errors: [e.message || 'Delete failed'] })
        setDeleting(false)
        return
      }
    }
    currentOffset += batchSize

    // Process remaining batches automatically
    while (currentOffset < totalGroups) {
      try {
        const res = await axios.get(`/api/repos/${dupeKey}/dupes/batch?offset=${currentOffset}&limit=${batchSize}`)
        const batchGroups: RepoRepoFile[][] = res.data.groups
        const autoDelete: { repoName: string; repoPath: string; relativePath: string; size: number }[] = []
        batchGroups.forEach((group) => {
          group.forEach((item, fi) => {
            if (fi !== 0) { // Keep first, delete rest
              autoDelete.push({
                repoName: item.repo.name,
                repoPath: item.repo.absolutePath,
                relativePath: getRelativePath(item.repoFile),
                size: getSize(item.repoFile),
              })
            }
          })
        })
        if (autoDelete.length > 0) {
          const delRes = await axios.post('/api/files/delete', autoDelete)
          batchDeleted += delRes.data.deleted
        }
        currentOffset += batchSize
      } catch (e: any) {
        setDeleteResult({ deleted: batchDeleted, errors: [e.message || 'Auto-delete failed'] })
        setDeleting(false)
        return
      }
    }

    setTotalDeleted(prev => prev + batchDeleted)
    setDeleting(false)
    handleFinished()
  }

  const handleFinished = () => {
    // Clean up server-side results
    axios.delete(`/api/repos/${dupeKey}/dupes/results`).catch(() => {})
    setGroups([])
    setHasMore(false)
  }

  const totalUnselected = groups.reduce((acc, group, gi) => {
    return acc + group.filter((_, fi) => !(kept[gi] || new Set()).has(fi)).length
  }, 0)

  const isFinished = !hasMore && groups.length === 0

  if (totalGroups === 0) {
    return null
  }

  if (isFinished) {
    return (
      <div className="flex flex-col items-center justify-center py-20 space-y-6">
        <div className="w-20 h-20 bg-emerald-500/10 rounded-full flex items-center justify-center">
          <Trash2 className="w-10 h-10 text-emerald-500" />
        </div>
        <h3 className="text-2xl font-bold">Processing Complete</h3>
        <div className="text-slate-400 text-center space-y-1">
          <p>Total groups processed: <strong className="text-white">{totalGroups}</strong></p>
          <p>Files deleted: <strong className="text-emerald-400">{totalDeleted}</strong></p>
          {totalSkipped > 0 && <p>Groups skipped: <strong className="text-yellow-400">{totalSkipped}</strong></p>}
        </div>
        <button
          onClick={() => { if (onDone) onDone(); else window.location.reload(); }}
          className="bg-white/10 hover:bg-white/20 text-white font-bold py-3 px-8 rounded-xl text-sm transition-all"
        >
          Done
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Batch header */}
      <div className="flex flex-wrap justify-between items-center gap-4 mb-4">
        <div className="space-y-1">
          <p className="text-sm text-slate-400">
            Batch <strong className="text-white">{batchNumber}</strong> of <strong className="text-white">{totalBatches}</strong>
            {' — '}Showing groups {offset + 1}–{Math.min(offset + groups.length, totalGroups)} of {totalGroups}
          </p>
          <p className="text-xs text-slate-500">
            {totalFiles} total files across all groups. Select the files you want to <strong className="text-white">KEEP</strong>. Unselected files will be deleted.
            {totalDeleted > 0 && <span className="text-emerald-400 ml-2">({totalDeleted} files deleted so far)</span>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs text-slate-500">Batch size:</label>
          <select
            value={batchSize}
            onChange={e => setBatchSize(Number(e.target.value))}
            className="bg-slate-800 border border-slate-700 rounded-lg px-2 py-1 text-xs text-slate-300"
          >
            {BATCH_SIZE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </div>

      {/* Progress bar */}
      <div className="w-full bg-slate-800 rounded-full h-2">
        <div
          className="bg-blue-500 h-2 rounded-full transition-all duration-300"
          style={{ width: `${Math.min(((offset + groups.length) / totalGroups) * 100, 100)}%` }}
        />
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 space-y-4">
          <RefreshCw className="w-10 h-10 text-blue-500 animate-spin" />
          <p className="text-slate-400">Loading batch...</p>
        </div>
      ) : (
        <>
          {/* Groups */}
          <div className="space-y-8">
            {groups.slice(0, visibleCount).map((group, gi) => (
              <div key={gi} className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden">
                <div className="px-5 py-3 bg-slate-800/40 border-b border-slate-800 flex justify-between items-center">
                  <div className="flex items-center gap-3">
                    <Copy className="w-4 h-4 text-blue-400" />
                    <span className="font-bold text-slate-200">Group {offset + gi + 1}</span>
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

          {/* Delete result for current batch */}
          {deleteResult && deleteResult.errors.length > 0 && (
            <div className="p-6 rounded-2xl border bg-red-900/20 border-red-500/50">
              <h4 className="font-black text-lg mb-2">Errors in this batch</h4>
              <p className="text-sm">Deleted {deleteResult.deleted} files, but encountered errors:</p>
              <ul className="text-xs text-red-300 list-disc list-inside space-y-1 mt-2">
                {deleteResult.errors.map((err, i) => <li key={i}>{err}</li>)}
              </ul>
            </div>
          )}

          {/* Action buttons */}
          <div className="fixed bottom-10 right-10 z-[50] flex flex-col items-end gap-3 animate-in slide-in-from-bottom-10">
            <button
              onClick={handleDeleteAndNext}
              disabled={deleting}
              className="bg-red-600 hover:bg-red-500 disabled:opacity-30 disabled:cursor-not-allowed text-white font-bold py-3 px-8 rounded-2xl text-sm uppercase tracking-widest transition-all shadow-lg shadow-red-900/30 flex items-center gap-2"
            >
              {deleting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
              Delete {totalUnselected} & {hasMore ? 'Next Batch' : 'Finish'}
              <ChevronRight className="w-4 h-4" />
            </button>
            <button
              onClick={handleSkip}
              disabled={deleting}
              className="bg-slate-700 hover:bg-slate-600 disabled:opacity-30 text-white font-bold py-2 px-6 rounded-xl text-xs uppercase tracking-widest transition-all flex items-center gap-2"
            >
              <SkipForward className="w-3 h-3" />
              Skip Batch
            </button>
            {hasMore && (
              <button
                onClick={handleDeleteAllRemaining}
                disabled={deleting}
                className="bg-orange-600 hover:bg-orange-500 disabled:opacity-30 text-white font-bold py-2 px-6 rounded-xl text-xs uppercase tracking-widest transition-all flex items-center gap-2"
              >
                <Zap className="w-3 h-3" />
                Auto-Delete All Remaining
              </button>
            )}
          </div>
        </>
      )}
    </div>
  )
}

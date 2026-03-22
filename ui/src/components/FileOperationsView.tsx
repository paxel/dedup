import { useState, useEffect, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { Copy, Scissors, Trash2, ChevronRight, FolderOpen, Filter, X, RefreshCw } from 'lucide-react'
import { Repo } from '../types'

interface DiffProgress {
  key: string
  message: string
  total?: number
  completed?: number
  finished?: boolean
}

interface FileOperationsViewProps {
  repos: Repo[]
  isAnyProcessRunning: boolean
  onBack: () => void
  openBrowser: (initialPath: string, onSelect: (path: string) => void) => void
}

export const FileOperationsView = ({ repos, isAnyProcessRunning, onBack, openBrowser }: FileOperationsViewProps) => {
  const [sourceRepos, setSourceRepos] = useState<Set<string>>(new Set())
  const [referenceRepo, setReferenceRepo] = useState<string | null>(null)
  const [targetDir, setTargetDir] = useState('')
  const [command, setCommand] = useState<'copy' | 'move' | 'remove'>('copy')
  const [filter, setFilter] = useState('')
  const [showFilter, setShowFilter] = useState(false)
  const [diffProgress, setDiffProgress] = useState<DiffProgress | null>(null)
  const diffKeyRef = useRef<string | null>(null)

  const diffCopyMutation = useMutation({
    mutationFn: (params: { sourceRepos: string[]; referenceRepo?: string; targetDir?: string; filter: string | null }) => {
      if (command === 'remove') {
        return axios.post('/api/files/rm', { sourceRepos: params.sourceRepos, filter: params.filter })
      }
      return axios.post(command === 'move' ? '/api/diff/mv' : '/api/diff/cp', params)
    },
    onSuccess: (response) => {
      const key = response.data?.key
      if (key) {
        diffKeyRef.current = key
      }
    },
  })

  const cancelDiffMutation = useMutation({
    mutationFn: () => {
      const key = diffKeyRef.current
      return axios.post(`/api/diff/cancel${key ? `?key=${key}` : ''}`)
    },
    onSuccess: () => {
      diffKeyRef.current = null
      setDiffProgress(null)
    },
  })

  // Listen for diff-specific WebSocket events
  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${protocol}//${window.location.host}/events`)

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data)
      if (data.type === 'diff-progress') {
        setDiffProgress({
          key: data.payload.key,
          message: data.payload.message,
          total: data.payload.total,
          completed: data.payload.completed,
          finished: false,
        })
      } else if (data.type === 'diff-finished') {
        setDiffProgress({
          key: data.payload.key,
          message: data.payload.message,
          total: data.payload.total,
          completed: data.payload.completed,
          finished: true,
        })
        diffKeyRef.current = null
      } else if (data.type === 'diff-error') {
        setDiffProgress({
          key: data.payload.key,
          message: data.payload.message,
          finished: true,
        })
        diffKeyRef.current = null
      }
    }

    return () => {
      ws.close()
    }
  }, [])

  const isRunning = diffProgress !== null && !diffProgress.finished
  const needsReference = command !== 'remove'
  const needsTargetDir = command !== 'remove'
  const canExecute = sourceRepos.size > 0
    && (!needsReference || referenceRepo !== null)
    && (!needsTargetDir || targetDir.trim() !== '')
    && !isAnyProcessRunning && !diffCopyMutation.isPending && !isRunning

  const handleExecute = () => {
    if (!canExecute) return
    if (needsReference && !referenceRepo) return
    setDiffProgress(null)
    diffCopyMutation.mutate({
      sourceRepos: Array.from(sourceRepos),
      ...(needsReference ? { referenceRepo: referenceRepo! } : {}),
      ...(needsTargetDir ? { targetDir } : {}),
      filter: filter.trim() || null,
    })
  }

  const toggleSource = (name: string) => {
    setSourceRepos(prev => {
      const next = new Set(prev)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }

  return (
    <section className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden">
      {/* Header */}
      <div className="p-6 border-b border-slate-800 flex justify-between items-center">
        <div className="flex items-center gap-4">
          <button
            onClick={onBack}
            className="p-2 hover:bg-slate-800 rounded-lg transition-colors text-slate-400 hover:text-white"
            title="Back to overview"
          >
            <ChevronRight className="w-6 h-6 rotate-180" />
          </button>
          <div>
            <h2 className="text-2xl font-bold text-orange-400">File Operations</h2>
            <p className="text-sm text-slate-500">Select source and reference repos, then execute a command</p>
          </div>
        </div>
      </div>

      {/* Command Bar */}
      <div className="px-6 py-4 border-b border-slate-800 flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setCommand('copy')}
            className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest flex items-center gap-2 border transition-all ${
              command === 'copy'
                ? 'bg-orange-600/20 border-orange-500/40 text-orange-400'
                : 'border-slate-700 text-slate-500 hover:text-slate-300 hover:border-slate-600'
            }`}
            title="Copy files that exist in source but not in reference to the target directory"
          >
            <Copy className="w-4 h-4" />
            Copy Diff To
          </button>
          <button
            onClick={() => setCommand('move')}
            className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest flex items-center gap-2 border transition-all ${
              command === 'move'
                ? 'bg-orange-600/20 border-orange-500/40 text-orange-400'
                : 'border-slate-700 text-slate-500 hover:text-slate-300 hover:border-slate-600'
            }`}
            title="Move files that exist in source but not in reference to the target directory (removes from source)"
          >
            <Scissors className="w-4 h-4" />
            Move Diff To
          </button>
          <button
            onClick={() => setCommand('remove')}
            className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest flex items-center gap-2 border transition-all ${
              command === 'remove'
                ? 'bg-red-600/20 border-red-500/40 text-red-400'
                : 'border-slate-700 text-slate-500 hover:text-slate-300 hover:border-slate-600'
            }`}
            title="Delete files matching the filter from the selected source repositories"
          >
            <Trash2 className="w-4 h-4" />
            Remove Files
          </button>
        </div>

        <div className="flex-1" />

        <button
          onClick={() => setShowFilter(prev => !prev)}
          className={`p-2 rounded-lg transition-all border ${showFilter ? 'bg-slate-700 border-slate-600 text-white' : 'border-slate-700 text-slate-500 hover:text-slate-300'}`}
          title="Toggle filter"
        >
          <Filter className="w-4 h-4" />
        </button>

        {showFilter && (
          <input
            type="text"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter pattern (e.g. *.jpg)"
            className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-600 focus:outline-none focus:border-orange-500 w-64"
          />
        )}
      </div>

      {/* Two-panel layout */}
      <div className={`grid grid-cols-1 ${needsReference ? 'md:grid-cols-2' : ''} divide-y md:divide-y-0 md:divide-x divide-slate-800`}>
        {/* Source repos (left) - multi-select */}
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xs font-black uppercase tracking-widest text-slate-500">Source Repos</h3>
            <button
              onClick={() => {
                if (sourceRepos.size === repos.length) {
                  setSourceRepos(new Set())
                } else {
                  setSourceRepos(new Set(repos.map(r => r.name)))
                }
              }}
              className="text-[10px] font-bold text-slate-500 hover:text-white transition-colors"
              title={sourceRepos.size === repos.length ? 'Deselect all source repos' : 'Select all repos as source'}
            >
              {sourceRepos.size === repos.length ? 'Deselect All' : 'Select All'}
            </button>
          </div>
          <div className="space-y-1 max-h-[400px] overflow-y-auto">
            {repos.map(r => (
              <label
                key={r.name}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${sourceRepos.has(r.name) ? 'bg-orange-500/10 border border-orange-500/30' : 'hover:bg-slate-800 border border-transparent'}`}
              >
                <input
                  type="checkbox"
                  checked={sourceRepos.has(r.name)}
                  onChange={() => toggleSource(r.name)}
                  className="accent-orange-500 w-4 h-4"
                />
                <div className="min-w-0">
                  <span className="text-sm font-bold text-slate-200 block truncate">{r.name}</span>
                  <span className="text-[10px] text-slate-600 font-mono truncate block">{r.absolutePath}</span>
                </div>
              </label>
            ))}
            {repos.length === 0 && (
              <p className="text-sm text-slate-600 italic py-4 text-center">No repositories available</p>
            )}
          </div>
        </div>

        {/* Target/reference repo (right) - single-select */}
        {needsReference && <div className="p-6">
          <h3 className="text-xs font-black uppercase tracking-widest text-slate-500 mb-4">Reference Repo (diff against)</h3>
          <div className="space-y-1 max-h-[400px] overflow-y-auto">
            {repos.map(r => (
              <label
                key={r.name}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${referenceRepo === r.name ? 'bg-blue-500/10 border border-blue-500/30' : 'hover:bg-slate-800 border border-transparent'}`}
              >
                <input
                  type="radio"
                  name="referenceRepo"
                  checked={referenceRepo === r.name}
                  onChange={() => setReferenceRepo(r.name)}
                  className="accent-blue-500 w-4 h-4"
                />
                <div className="min-w-0">
                  <span className="text-sm font-bold text-slate-200 block truncate">{r.name}</span>
                  <span className="text-[10px] text-slate-600 font-mono truncate block">{r.absolutePath}</span>
                </div>
              </label>
            ))}
            {repos.length === 0 && (
              <p className="text-sm text-slate-600 italic py-4 text-center">No repositories available</p>
            )}
          </div>
        </div>}
      </div>

      {/* Action bar */}
      <div className="px-6 py-5 border-t border-slate-800 bg-slate-950/40 flex flex-wrap items-center gap-4">
        {needsTargetDir && <div className="flex items-center gap-3 flex-1 min-w-0">
          <span className="text-xs font-black uppercase tracking-widest text-slate-500 shrink-0">Target Dir</span>
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <input
              type="text"
              value={targetDir}
              onChange={(e) => setTargetDir(e.target.value)}
              placeholder="/path/to/output"
              className="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-600 focus:outline-none focus:border-orange-500 min-w-0"
            />
            <button
              onClick={() => openBrowser(targetDir || '.', (path) => setTargetDir(path))}
              className="p-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg text-slate-400 hover:text-white transition-all shrink-0"
              title="Browse for directory"
            >
              <FolderOpen className="w-4 h-4" />
            </button>
          </div>
        </div>}

        {!needsTargetDir && <div className="flex-1" />}

        {isRunning ? (
          <button
            onClick={() => cancelDiffMutation.mutate()}
            className="bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 text-red-400 hover:text-red-300 px-6 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2"
            title="Cancel the running diff copy operation"
          >
            <X className="w-4 h-4" />
            Cancel
          </button>
        ) : (
          <button
            onClick={handleExecute}
            disabled={!canExecute}
            className="bg-orange-600 hover:bg-orange-500 disabled:opacity-30 disabled:cursor-not-allowed text-white px-6 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2 shadow-lg shadow-orange-600/20"
            title={command === 'remove' ? 'Delete files matching the filter from the selected source repositories' : command === 'move' ? 'Move files from source repos that are not in the reference repo to the target directory' : 'Copy files from source repos that are not in the reference repo to the target directory'}
          >
            {command === 'remove' ? <Trash2 className="w-4 h-4" /> : command === 'move' ? <Scissors className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {command === 'remove' ? `Remove Files (${sourceRepos.size} repo${sourceRepos.size !== 1 ? 's' : ''})` : `${command === 'move' ? 'Move' : 'Copy'} Diff (${sourceRepos.size} → ${referenceRepo || '?'})`}
          </button>
        )}
      </div>

      {/* Progress / Status */}
      {isRunning && diffProgress && (
        <div className="px-6 py-4 bg-blue-500/10 border-t border-blue-500/20">
          <div className="flex items-center gap-4">
            <RefreshCw className="w-5 h-5 text-blue-400 animate-spin shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-bold text-blue-300">{diffProgress.message}</p>
              {diffProgress.total != null && diffProgress.total > 0 && (
                <div className="mt-2 w-full h-2 bg-slate-700/80 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-blue-500 to-blue-400 transition-all duration-500 ease-linear rounded-full"
                    style={{ width: `${Math.round(((diffProgress.completed || 0) / diffProgress.total) * 100)}%` }}
                  />
                </div>
              )}
            </div>
            {diffProgress.total != null && (
              <span className="text-sm text-slate-400 shrink-0">
                <span className="font-bold text-white">{diffProgress.completed || 0}</span> / {diffProgress.total}
              </span>
            )}
          </div>
        </div>
      )}
      {diffProgress?.finished && (
        <div className={`px-6 py-3 border-t text-sm font-bold ${
          diffProgress.message.includes('Cancelled') || diffProgress.message.includes('Error') || diffProgress.message.includes('Failed')
            ? 'bg-red-500/10 border-red-500/20 text-red-400'
            : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
        }`}>
          {diffProgress.message}
          {diffProgress.total != null && diffProgress.completed != null && (
            <span className="ml-2 text-slate-500">({diffProgress.completed}/{diffProgress.total} repos processed)</span>
          )}
        </div>
      )}
      {diffCopyMutation.isError && !diffProgress && (
        <div className="px-6 py-3 bg-red-500/10 border-t border-red-500/20 text-red-400 text-sm font-bold">
          Failed to start diff copy: {(diffCopyMutation.error as any)?.response?.data?.message || (diffCopyMutation.error as any)?.message}
        </div>
      )}
    </section>
  )
}

import { useState, useEffect, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { Copy, Scissors, Trash2, ChevronRight, FolderOpen, Filter, X, RefreshCw, ArrowRightLeft, FileType, Hash, Ruler, Info } from 'lucide-react'
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

type Command = 'copy' | 'move' | 'remove' | 'fileCopy' | 'fileMove' | 'sync'

export const FileOperationsView = ({ repos, isAnyProcessRunning, onBack, openBrowser }: FileOperationsViewProps) => {
  const [sourceRepos, setSourceRepos] = useState<Set<string>>(new Set())
  const [referenceRepo, setReferenceRepo] = useState<string | null>(null)
  const [targetDir, setTargetDir] = useState('')
  const [targetRepo, setTargetRepo] = useState<string | null>(null)
  const [command, setCommand] = useState<Command>('copy')
  const [showFilter, setShowFilter] = useState(false)
  const [activeFilterType, setActiveFilterType] = useState<'mime' | 'name' | 'size' | null>(null)
  const [mimeValue, setMimeValue] = useState('')
  const [nameValue, setNameValue] = useState('')
  const [sizeOp, setSizeOp] = useState('>=')
  const [sizeValue, setSizeValue] = useState('')
  const [diffProgress, setDiffProgress] = useState<DiffProgress | null>(null)
  const [syncCopyNew, setSyncCopyNew] = useState(true)
  const [syncDeleteMissing, setSyncDeleteMissing] = useState(false)
  const diffKeyRef = useRef<string | null>(null)

  const diffCopyMutation = useMutation({
    mutationFn: (params: { sourceRepos: string[]; referenceRepo?: string; targetDir?: string; targetRepo?: string; filter: string | null; copyNew?: boolean; deleteMissing?: boolean }) => {
      if (command === 'remove') {
        return axios.post('/api/files/rm', { sourceRepos: params.sourceRepos, filter: params.filter })
      }
      if (command === 'fileCopy') {
        return axios.post('/api/files/cp', { sourceRepos: params.sourceRepos, targetRepo: params.targetRepo, filter: params.filter })
      }
      if (command === 'fileMove') {
        return axios.post('/api/files/mv', { sourceRepos: params.sourceRepos, targetRepo: params.targetRepo, filter: params.filter })
      }
      if (command === 'sync') {
        return axios.post('/api/diff/sync', {
          sourceRepo: params.sourceRepos[0],
          targetRepo: params.targetRepo,
          copyNew: params.copyNew,
          deleteMissing: params.deleteMissing,
          filter: params.filter,
        })
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
  const needsReference = command === 'copy' || command === 'move'
  const needsTargetDir = command === 'copy' || command === 'move'
  const needsTargetRepo = command === 'sync' || command === 'fileCopy' || command === 'fileMove'
  const isSingleSource = command === 'sync'
  const canExecute = sourceRepos.size > 0
    && (!isSingleSource || sourceRepos.size === 1)
    && (!needsReference || referenceRepo !== null)
    && (!needsTargetDir || targetDir.trim() !== '')
    && (!needsTargetRepo || targetRepo !== null)
    && !isAnyProcessRunning && !diffCopyMutation.isPending && !isRunning

  const handleExecute = () => {
    if (!canExecute) return
    if (needsReference && !referenceRepo) return
    setDiffProgress(null)
    diffCopyMutation.mutate({
      sourceRepos: Array.from(sourceRepos),
      ...(needsReference ? { referenceRepo: referenceRepo! } : {}),
      ...(needsTargetDir ? { targetDir } : {}),
      ...(needsTargetRepo ? { targetRepo: targetRepo! } : {}),
      ...(command === 'sync' ? { copyNew: syncCopyNew, deleteMissing: syncDeleteMissing } : {}),
      filter: computedFilter || null,
    })
  }

  const toggleSource = (name: string) => {
    setSourceRepos(prev => {
      const next = new Set(prev)
      if (next.has(name)) {
        next.delete(name)
      } else {
        if (isSingleSource) {
          next.clear()
        }
        next.add(name)
      }
      return next
    })
  }

  const computedFilter = (() => {
    if (!showFilter || activeFilterType === null) return ''
    switch (activeFilterType) {
      case 'mime': return mimeValue.trim() ? `mime:${mimeValue.trim()}` : ''
      case 'name': return nameValue.trim() ? `name:${nameValue.trim()}` : ''
      case 'size': return sizeValue.trim() ? `size:${sizeOp}${sizeValue.trim()}` : ''
    }
    return ''
  })()

  const commandLabel = () => {
    switch (command) {
      case 'copy': return 'Copy Diff To'
      case 'move': return 'Move Diff To'
      case 'remove': return 'Remove Files'
      case 'fileCopy': return 'Copy Files To'
      case 'fileMove': return 'Move Files To'
      case 'sync': return 'Sync Repos'
    }
  }

  const commandIcon = () => {
    switch (command) {
      case 'copy': case 'fileCopy': return <Copy className="w-4 h-4" />
      case 'move': case 'fileMove': return <Scissors className="w-4 h-4" />
      case 'remove': return <Trash2 className="w-4 h-4" />
      case 'sync': return <ArrowRightLeft className="w-4 h-4" />
    }
  }

  const executeLabel = () => {
    switch (command) {
      case 'remove': return `Remove Files (${sourceRepos.size} repo${sourceRepos.size !== 1 ? 's' : ''})`
      case 'fileCopy': return `Copy Files (${sourceRepos.size} repo${sourceRepos.size !== 1 ? 's' : ''}) → ${targetRepo || '?'}`
      case 'fileMove': return `Move Files (${sourceRepos.size} repo${sourceRepos.size !== 1 ? 's' : ''}) → ${targetRepo || '?'}`
      case 'sync': return `Sync ${sourceRepos.size === 1 ? Array.from(sourceRepos)[0] : '?'} → ${targetRepo || '?'}`
      default: return `${command === 'move' ? 'Move' : 'Copy'} Diff (${sourceRepos.size} → ${referenceRepo || '?'})`
    }
  }

  const commandButton = (cmd: Command, label: string, icon: React.ReactNode, title: string, danger?: boolean) => (
    <button
      onClick={() => setCommand(cmd)}
      className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest flex items-center gap-2 border transition-all ${
        command === cmd
          ? danger
            ? 'bg-red-600/20 border-red-500/40 text-red-400'
            : 'bg-orange-600/20 border-orange-500/40 text-orange-400'
          : 'border-slate-700 text-slate-500 hover:text-slate-300 hover:border-slate-600'
      }`}
      title={title}
    >
      {icon}
      {label}
    </button>
  )

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
      <div className="px-6 py-4 border-b border-slate-800">
        {/* Diff-based commands */}
        <div className="flex flex-wrap items-center gap-2 mb-3">
          <span className="text-[10px] font-bold text-slate-600 uppercase tracking-widest w-16 shrink-0">Diff</span>
          {commandButton('copy', 'Copy Diff To', <Copy className="w-4 h-4" />, 'Copy files that exist in source but not in reference to the target directory')}
          {commandButton('move', 'Move Diff To', <Scissors className="w-4 h-4" />, 'Move files that exist in source but not in reference to the target directory (removes from source)')}
        </div>
        {/* Files-based commands */}
        <div className="flex flex-wrap items-center gap-2 mb-3">
          <span className="text-[10px] font-bold text-slate-600 uppercase tracking-widest w-16 shrink-0">Files</span>
          {commandButton('fileCopy', 'Copy Files To', <Copy className="w-4 h-4" />, 'Copy all matching files from source repos to the target repo')}
          {commandButton('fileMove', 'Move Files To', <Scissors className="w-4 h-4" />, 'Move all matching files from source repos to the target repo')}
          {commandButton('remove', 'Remove Files', <Trash2 className="w-4 h-4" />, 'Delete files matching the filter from the selected source repositories', true)}
        </div>
        {/* Sync command */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-[10px] font-bold text-slate-600 uppercase tracking-widest w-16 shrink-0">Sync</span>
          {commandButton('sync', 'Sync Repos', <ArrowRightLeft className="w-4 h-4" />, 'Sync content from source repo into target repo (by hash/size)')}

          {command === 'sync' && (
            <div className="flex items-center gap-4 ml-4">
              <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer">
                <input
                  type="checkbox"
                  checked={syncCopyNew}
                  onChange={(e) => setSyncCopyNew(e.target.checked)}
                  className="accent-orange-500 w-3.5 h-3.5"
                />
                Copy New
              </label>
              <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer">
                <input
                  type="checkbox"
                  checked={syncDeleteMissing}
                  onChange={(e) => setSyncDeleteMissing(e.target.checked)}
                  className="accent-red-500 w-3.5 h-3.5"
                />
                Delete Missing
              </label>
            </div>
          )}
        </div>

        {/* Filter section */}
        <div className="mt-3">
          <button
            onClick={() => setShowFilter(prev => !prev)}
            className={`p-2 rounded-lg transition-all border ${showFilter ? 'bg-slate-700 border-slate-600 text-white' : 'border-slate-700 text-slate-500 hover:text-slate-300'}`}
            title="Toggle filter panel"
          >
            <Filter className="w-4 h-4" />
          </button>

          {showFilter && (
            <div className="mt-3 bg-slate-800/60 border border-slate-700 rounded-xl p-4 space-y-3">
              {/* MIME filter */}
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 cursor-pointer w-20 shrink-0" title="Filter by MIME type substring (e.g. 'image', 'video/mp4', 'text/plain')">
                  <input
                    type="radio"
                    name="filterType"
                    checked={activeFilterType === 'mime'}
                    onChange={() => setActiveFilterType('mime')}
                    className="accent-orange-500 w-3.5 h-3.5"
                  />
                  <FileType className="w-4 h-4 text-slate-400" />
                  <span className="text-xs font-bold text-slate-400 uppercase">Mime</span>
                </label>
                <input
                  type="text"
                  value={mimeValue}
                  onChange={(e) => { setMimeValue(e.target.value); setActiveFilterType('mime') }}
                  placeholder="e.g. image, video/mp4, text/plain"
                  disabled={activeFilterType !== 'mime'}
                  className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 placeholder-slate-600 focus:outline-none focus:border-orange-500 disabled:opacity-40"
                />
                <div className="shrink-0" title="Matches files whose MIME type contains the given text. Example: 'image' matches image/jpeg, image/png, etc.">
                  <Info className="w-4 h-4 text-slate-600 hover:text-slate-400 cursor-help" />
                </div>
              </div>

              {/* Name filter */}
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 cursor-pointer w-20 shrink-0" title="Filter by file path substring (e.g. '.jpg', 'photos/', 'backup')">
                  <input
                    type="radio"
                    name="filterType"
                    checked={activeFilterType === 'name'}
                    onChange={() => setActiveFilterType('name')}
                    className="accent-orange-500 w-3.5 h-3.5"
                  />
                  <Hash className="w-4 h-4 text-slate-400" />
                  <span className="text-xs font-bold text-slate-400 uppercase">Name</span>
                </label>
                <input
                  type="text"
                  value={nameValue}
                  onChange={(e) => { setNameValue(e.target.value); setActiveFilterType('name') }}
                  placeholder="e.g. .jpg, photos/, backup"
                  disabled={activeFilterType !== 'name'}
                  className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 placeholder-slate-600 focus:outline-none focus:border-orange-500 disabled:opacity-40"
                />
                <div className="shrink-0" title="Matches files whose relative path contains the given text. Example: '.jpg' matches all JPEG files, 'photos/' matches files in a photos directory.">
                  <Info className="w-4 h-4 text-slate-600 hover:text-slate-400 cursor-help" />
                </div>
              </div>

              {/* Size filter */}
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 cursor-pointer w-20 shrink-0" title="Filter by file size in bytes with a comparison operator">
                  <input
                    type="radio"
                    name="filterType"
                    checked={activeFilterType === 'size'}
                    onChange={() => setActiveFilterType('size')}
                    className="accent-orange-500 w-3.5 h-3.5"
                  />
                  <Ruler className="w-4 h-4 text-slate-400" />
                  <span className="text-xs font-bold text-slate-400 uppercase">Size</span>
                </label>
                <select
                  value={sizeOp}
                  onChange={(e) => { setSizeOp(e.target.value); setActiveFilterType('size') }}
                  disabled={activeFilterType !== 'size'}
                  className="bg-slate-900 border border-slate-700 rounded-lg px-2 py-1.5 text-sm text-slate-200 focus:outline-none focus:border-orange-500 disabled:opacity-40 w-16"
                >
                  <option value=">=">≥</option>
                  <option value=">">{'>'}</option>
                  <option value="<=">≤</option>
                  <option value="<">{'<'}</option>
                  <option value="=">=</option>
                </select>
                <input
                  type="number"
                  value={sizeValue}
                  onChange={(e) => { setSizeValue(e.target.value); setActiveFilterType('size') }}
                  placeholder="bytes (e.g. 1048576 = 1MB)"
                  disabled={activeFilterType !== 'size'}
                  className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-slate-200 placeholder-slate-600 focus:outline-none focus:border-orange-500 disabled:opacity-40"
                />
                <div className="shrink-0" title="Matches files by size in bytes. Choose a comparison operator and enter the size value. Example: ≥ 1048576 matches files 1MB or larger.">
                  <Info className="w-4 h-4 text-slate-600 hover:text-slate-400 cursor-help" />
                </div>
              </div>

              {/* No filter option */}
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 cursor-pointer w-20 shrink-0" title="Disable filter — all files will be included">
                  <input
                    type="radio"
                    name="filterType"
                    checked={activeFilterType === null}
                    onChange={() => setActiveFilterType(null)}
                    className="accent-slate-500 w-3.5 h-3.5"
                  />
                  <X className="w-4 h-4 text-slate-500" />
                  <span className="text-xs font-bold text-slate-500 uppercase">None</span>
                </label>
                <span className="text-xs text-slate-600 italic">No filter — include all files</span>
              </div>

              {computedFilter && (
                <div className="pt-2 border-t border-slate-700/50">
                  <span className="text-[10px] text-slate-600 font-mono">Filter: {computedFilter}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Two-panel layout */}
      <div className={`grid grid-cols-1 ${needsReference || needsTargetRepo ? 'md:grid-cols-2' : ''} divide-y md:divide-y-0 md:divide-x divide-slate-800`}>
        {/* Source repos (left) - multi-select (or single for sync) */}
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xs font-black uppercase tracking-widest text-slate-500">
              Source Repo{isSingleSource ? '' : 's'}
            </h3>
            {!isSingleSource && (
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
            )}
          </div>
          <div className="space-y-1 max-h-[400px] overflow-y-auto">
            {repos.map(r => (
              <label
                key={r.name}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${sourceRepos.has(r.name) ? 'bg-orange-500/10 border border-orange-500/30' : 'hover:bg-slate-800 border border-transparent'}`}
              >
                <input
                  type={isSingleSource ? 'radio' : 'checkbox'}
                  name={isSingleSource ? 'sourceRepo' : undefined}
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

        {/* Reference repo (right) - for diff commands */}
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

        {/* Target repo (right) - for sync command */}
        {needsTargetRepo && <div className="p-6">
          <h3 className="text-xs font-black uppercase tracking-widest text-slate-500 mb-4">
            {command === 'sync' ? 'Target Repo (sync into)' : 'Target Repo'}
          </h3>
          <div className="space-y-1 max-h-[400px] overflow-y-auto">
            {repos.map(r => (
              <label
                key={r.name}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${targetRepo === r.name ? 'bg-blue-500/10 border border-blue-500/30' : 'hover:bg-slate-800 border border-transparent'}`}
              >
                <input
                  type="radio"
                  name="targetRepo"
                  checked={targetRepo === r.name}
                  onChange={() => setTargetRepo(r.name)}
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
            title="Cancel the running operation"
          >
            <X className="w-4 h-4" />
            Cancel
          </button>
        ) : (
          <button
            onClick={handleExecute}
            disabled={!canExecute}
            className="bg-orange-600 hover:bg-orange-500 disabled:opacity-30 disabled:cursor-not-allowed text-white px-6 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2 shadow-lg shadow-orange-600/20"
            title={commandLabel()}
          >
            {commandIcon()}
            {executeLabel()}
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
            <span className="ml-2 text-slate-500">({diffProgress.completed}/{diffProgress.total} processed)</span>
          )}
        </div>
      )}
      {diffCopyMutation.isError && !diffProgress && (
        <div className="px-6 py-3 bg-red-500/10 border-t border-red-500/20 text-red-400 text-sm font-bold">
          Failed to start operation: {(diffCopyMutation.error as any)?.response?.data?.message || (diffCopyMutation.error as any)?.message}
        </div>
      )}
    </section>
  )
}

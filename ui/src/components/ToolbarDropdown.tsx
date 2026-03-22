import { useRef, useState, useEffect } from 'react'
import { ChevronRight } from 'lucide-react'
import { Repo } from '../types'

interface ToolbarDropdownProps {
  repos: Repo[]
  isDisabled: boolean
  icon: React.ReactNode
  label: string
  actionLabel: string
  actionIcon: React.ReactNode
  actionClassName: string
  checkboxAccent?: string
  onAction: (selectedRepos: string[]) => void
}

export const ToolbarDropdown = ({
  repos,
  isDisabled,
  icon,
  label,
  actionLabel,
  actionIcon,
  actionClassName,
  checkboxAccent = 'accent-blue-500',
  onAction
}: ToolbarDropdownProps) => {
  const [showDropdown, setShowDropdown] = useState(false)
  const [selectedRepos, setSelectedRepos] = useState<Set<string>>(new Set())
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={() => {
          if (repos.length > 0) {
            setShowDropdown(prev => !prev)
            if (selectedRepos.size === 0) {
              setSelectedRepos(new Set(repos.map(r => r.name)))
            }
          }
        }}
        disabled={repos.length === 0 || isDisabled}
        className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-4 py-2 rounded-xl text-xs font-black uppercase tracking-widest transition-all flex items-center gap-2 border border-slate-700 disabled:opacity-30"
      >
        {icon}
        {label}
        <ChevronRight className={`w-3 h-3 transition-transform ${showDropdown ? 'rotate-90' : ''}`} />
      </button>
      {showDropdown && repos.length > 0 && (
        <div className="absolute right-0 top-full mt-2 z-50 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl min-w-[220px] overflow-hidden">
          <div className="p-2 border-b border-slate-800">
            <button
              onClick={() => {
                if (selectedRepos.size === repos.length) {
                  setSelectedRepos(new Set())
                } else {
                  setSelectedRepos(new Set(repos.map(r => r.name)))
                }
              }}
              className="w-full text-left px-3 py-1.5 text-xs font-bold text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition-all"
            >
              {selectedRepos.size === repos.length ? 'Deselect All' : 'Select All'}
            </button>
          </div>
          <div className="max-h-48 overflow-y-auto">
            {repos.map(r => (
              <label key={r.name} className="flex items-center gap-3 px-3 py-2 hover:bg-slate-800 cursor-pointer transition-all">
                <input
                  type="checkbox"
                  checked={selectedRepos.has(r.name)}
                  onChange={() => {
                    const next = new Set(selectedRepos)
                    if (next.has(r.name)) {
                      next.delete(r.name)
                    } else {
                      next.add(r.name)
                    }
                    setSelectedRepos(next)
                  }}
                  className={checkboxAccent}
                />
                <span className="text-sm text-slate-300 truncate">{r.name}</span>
              </label>
            ))}
          </div>
          <div className="p-2 border-t border-slate-800">
            <button
              disabled={selectedRepos.size < 1}
              onClick={() => {
                onAction(Array.from(selectedRepos))
                setShowDropdown(false)
              }}
              className={`w-full disabled:opacity-30 text-white px-3 py-2 rounded-lg text-xs font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2 ${actionClassName}`}
            >
              {actionIcon}
              {actionLabel} {selectedRepos.size} Repo{selectedRepos.size !== 1 ? 's' : ''}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

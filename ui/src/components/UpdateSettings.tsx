import { useState, useRef, useEffect } from 'react'
import { Settings } from 'lucide-react'

interface UpdateSettingsProps {
  threads: number
  refreshFingerprints: boolean
  onChange: (options: { threads: number; refreshFingerprints: boolean }) => void
}

export const UpdateSettings = ({ threads, refreshFingerprints, onChange }: UpdateSettingsProps) => {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(prev => !prev)}
        className="bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 p-2 rounded-xl transition-all border border-slate-700"
        title="Update Settings"
      >
        <Settings className="w-4 h-4" />
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-2 z-50 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl min-w-[260px] overflow-hidden p-4 space-y-4">
          <h4 className="text-[10px] uppercase font-black text-slate-500 tracking-widest">Update Settings</h4>

          <div className="flex items-center justify-between gap-4">
            <label className="text-xs font-bold text-slate-300">Threads</label>
            <input
              type="number"
              min={1}
              max={32}
              value={threads}
              onChange={(e) => {
                const val = Math.max(1, Math.min(32, parseInt(e.target.value) || 1))
                onChange({ threads: val, refreshFingerprints })
              }}
              className="w-16 bg-slate-800 border border-slate-700 rounded-lg px-2 py-1 text-sm text-slate-200 text-center focus:outline-none focus:border-blue-500"
            />
          </div>

          <label className="flex items-center justify-between gap-4 cursor-pointer">
            <span className="text-xs font-bold text-slate-300">Refresh Fingerprints</span>
            <input
              type="checkbox"
              checked={refreshFingerprints}
              onChange={(e) => onChange({ threads, refreshFingerprints: e.target.checked })}
              className="accent-emerald-500 w-4 h-4"
            />
          </label>
        </div>
      )}
    </div>
  )
}

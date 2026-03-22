import { X, Folder } from 'lucide-react'

interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
  codec?: 'JSON' | 'MESSAGEPACK';
}

interface AddRepoModalProps {
  show: boolean;
  onClose: () => void;
  newRepo: Repo;
  setNewRepo: (repo: Repo | ((prev: Repo) => Repo)) => void;
  isValidRepoName: (name: string) => boolean;
  isRepoNameUnique: (name: string) => boolean;
  openBrowser: (initialPath: string, onSelect: (path: string) => void) => void;
  createMutation: any;
  updateMutation: any;
  resetNewRepo: () => void;
}

export const AddRepoModal = ({
  show,
  onClose,
  newRepo,
  setNewRepo,
  isValidRepoName,
  isRepoNameUnique,
  openBrowser,
  createMutation,
  updateMutation,
  resetNewRepo
}: AddRepoModalProps) => {
  if (!show) return null;

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl">
        <div className="p-6 border-b border-slate-800 flex justify-between items-center bg-slate-900/50">
          <h3 className="text-xl font-bold">Add New Repository</h3>
          <button onClick={onClose} className="text-slate-500 hover:text-white">
            <X className="w-6 h-6" />
          </button>
        </div>
        <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-400">Absolute Path</label>
            <div className="flex gap-2">
              <input 
                type="text" 
                value={newRepo.absolutePath}
                onChange={e => {
                  const path = e.target.value
                  setNewRepo(prev => {
                    const suggestedName = path.split(/[/\\]/).filter(Boolean).pop() || ''
                    const sanitizedName = suggestedName.replace(/\s+/g, '_')
                    const name = prev.name || sanitizedName
                    return { ...prev, absolutePath: path, name }
                  })
                }}
                className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
                placeholder="/home/user/music"
              />
              <button 
                onClick={() => openBrowser(newRepo.absolutePath, (path) => {
                  setNewRepo((prev) => {
                    const suggestedName = path.split(/[/\\]/).filter(Boolean).pop() || ''
                    const sanitizedName = suggestedName.replace(/\s+/g, '_')
                    const name = prev.name || sanitizedName
                    return { ...prev, absolutePath: path, name }
                  })
                })}
                className="bg-slate-800 hover:bg-slate-700 text-white px-3 py-2 rounded-lg transition-colors border border-slate-700"
                title="Browse Filesystem"
              >
                <Folder className="w-5 h-5" />
              </button>
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-400">Repository Name</label>
            <input 
              type="text" 
              value={newRepo.name}
              onChange={e => setNewRepo({...newRepo, name: e.target.value})}
              className={`w-full bg-slate-950 border rounded-lg px-4 py-2 focus:ring-1 outline-none transition-all ${
                !isValidRepoName(newRepo.name) || !isRepoNameUnique(newRepo.name)
                  ? 'border-red-500 focus:border-red-500 focus:ring-red-500' 
                  : 'border-slate-800 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="e.g. My_Music"
            />
            {newRepo.name && !isValidRepoName(newRepo.name) && (
              <p className="text-xs text-red-500 mt-1">Invalid name: only letters, digits and underscores allowed</p>
            )}
            {newRepo.name && isValidRepoName(newRepo.name) && !isRepoNameUnique(newRepo.name) && (
              <p className="text-xs text-red-500 mt-1">Repository name already exists</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-400">Index Files</label>
              <input 
                type="number" 
                value={newRepo.indices}
                onChange={e => setNewRepo({...newRepo, indices: parseInt(e.target.value) || 1})}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-400">Codec</label>
              <select 
                value={newRepo.codec}
                onChange={e => {
                  const codec = e.target.value as 'JSON' | 'MESSAGEPACK'
                  setNewRepo({...newRepo, codec})
                }}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all appearance-none"
              >
                <option value="MESSAGEPACK">MsgPack</option>
                <option value="JSON">JSON</option>
              </select>
            </div>
          </div>
        </div>
        <div className="p-6 bg-slate-900/50 border-t border-slate-800 flex justify-between items-center gap-3">
          <button 
            onClick={() => {
              onClose()
              resetNewRepo()
            }}
            className="px-4 py-2 rounded-lg border border-slate-800 hover:bg-slate-800 transition-colors font-semibold text-sm"
          >
            Cancel
          </button>
          <div className="flex gap-3">
            <button 
              onClick={() => {
                createMutation.mutate(newRepo, {
                  onSuccess: () => {
                    resetNewRepo()
                  }
                })
              }}
              disabled={!isValidRepoName(newRepo.name) || !isRepoNameUnique(newRepo.name) || !newRepo.absolutePath || createMutation.isPending}
              className="px-4 py-2 rounded-lg border border-slate-700 hover:bg-slate-800 text-white font-semibold transition-colors text-sm"
            >
              Add Another
            </button>
            <button 
              onClick={() => {
                createMutation.mutate(newRepo, {
                  onSuccess: () => {
                    onClose()
                    resetNewRepo()
                  }
                })
              }}
              disabled={!isValidRepoName(newRepo.name) || !isRepoNameUnique(newRepo.name) || !newRepo.absolutePath || createMutation.isPending}
              className="bg-slate-700 hover:bg-slate-600 text-white px-4 py-2 rounded-lg font-semibold transition-colors text-sm"
            >
              Add
            </button>
            <button 
              onClick={() => {
                createMutation.mutate(newRepo, {
                  onSuccess: (response: any) => {
                    updateMutation.mutate(response.data.name)
                    onClose()
                    resetNewRepo()
                  }
                })
              }}
              disabled={!isValidRepoName(newRepo.name) || !isRepoNameUnique(newRepo.name) || !newRepo.absolutePath || createMutation.isPending}
              className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-semibold transition-colors text-sm"
            >
              {createMutation.isPending ? 'Adding...' : 'Add and Scan'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

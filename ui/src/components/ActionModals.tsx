import { X, RefreshCw, Zap, Folder, Copy, Move } from 'lucide-react'

interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
  codec?: 'JSON' | 'MESSAGEPACK';
}

interface ActionModalsProps {
  showPruneModal: string | null;
  setShowPruneModal: (val: string | null) => void;
  pruneMutation: any;
  showRelocateModal: Repo | null;
  setShowRelocateModal: (val: Repo | null) => void;
  relocatePath: string;
  setRelocatePath: (val: string) => void;
  relocateMutation: any;
  showCloneModal: Repo | null;
  setShowCloneModal: (val: Repo | null) => void;
  cloneData: { path: string; destinationName: string };
  setCloneData: (val: any) => void;
  cloneMutation: any;
  showMoveModal: Repo | null;
  setShowMoveModal: (val: Repo | null) => void;
  moveData: { destinationName: string };
  setMoveData: (val: any) => void;
  moveRepoMutation: any;
  openBrowser: (initialPath: string, onSelect: (path: string) => void) => void;
}

export const ActionModals = ({
  showPruneModal,
  setShowPruneModal,
  pruneMutation,
  showRelocateModal,
  setShowRelocateModal,
  relocatePath,
  setRelocatePath,
  relocateMutation,
  showCloneModal,
  setShowCloneModal,
  cloneData,
  setCloneData,
  cloneMutation,
  showMoveModal,
  setShowMoveModal,
  moveData,
  setMoveData,
  moveRepoMutation,
  openBrowser
}: ActionModalsProps) => {
  return (
    <>
      {/* Prune Modal */}
      {showPruneModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-md shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="p-8">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-2xl font-black text-white flex items-center gap-3">
                  <RefreshCw className="w-6 h-6 text-amber-500" />
                  Prune Repository
                </h3>
                <button onClick={() => setShowPruneModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
            
              <div className="space-y-4 mb-8">
                <p className="text-slate-300 leading-relaxed">
                  Do you want to remove all file entries from <span className="text-white font-bold">{showPruneModal}</span> that no longer exist on your disk?
                </p>
                <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-4 flex gap-3">
                  <Zap className="w-5 h-5 text-amber-500 shrink-0" />
                  <p className="text-[11px] text-amber-200/70 leading-normal">This process will only clean up the database. No actual files on your disk will be deleted.</p>
                </div>
              </div>

              <div className="flex gap-3">
                <button 
                  onClick={() => setShowPruneModal(null)}
                  className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                >
                  Cancel
                </button>
                <button 
                  onClick={() => pruneMutation.mutate(showPruneModal)}
                  disabled={pruneMutation.isPending}
                  className="flex-1 bg-amber-600 hover:bg-amber-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-amber-900/20 transition-all flex items-center justify-center gap-2"
                >
                  {pruneMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Zap className="w-5 h-5" />}
                  Clean up missing files
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Relocate Modal */}
      {showRelocateModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="p-8">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-2xl font-black text-white flex items-center gap-3">
                  <Folder className="w-6 h-6 text-indigo-500" />
                  Move Repository Folder
                </h3>
                <button onClick={() => setShowRelocateModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
            
              <div className="space-y-6">
                <div>
                  <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Repository</label>
                  <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm">
                    {showRelocateModal.name}
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Folder Path</label>
                  <div className="flex gap-2">
                    <input 
                      type="text" 
                      value={relocatePath}
                      onChange={(e) => setRelocatePath(e.target.value)}
                      className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 transition-all font-mono text-sm"
                      placeholder="/absolute/path/to/data"
                    />
                    <button 
                      onClick={() => openBrowser(relocatePath, (path) => setRelocatePath(path))}
                      className="bg-slate-800 hover:bg-slate-700 text-slate-200 p-3 rounded-2xl transition-all"
                      title="Browse Directory"
                    >
                      <Folder className="w-5 h-5" />
                    </button>
                  </div>
                  <p className="mt-2 text-[10px] text-slate-500 italic px-1">Update the folder where this repository is stored. Files stay where they are.</p>
                </div>
              </div>

              <div className="flex gap-3 mt-8">
                <button 
                  onClick={() => setShowRelocateModal(null)}
                  className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                >
                  Cancel
                </button>
                <button 
                  onClick={() => relocateMutation.mutate({ name: showRelocateModal.name, path: relocatePath })}
                  disabled={relocateMutation.isPending || !relocatePath}
                  className="flex-1 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-indigo-900/20 transition-all flex items-center justify-center gap-2"
                >
                  {relocateMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Folder className="w-5 h-5" />}
                  Move Folder
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Clone Modal */}
      {showCloneModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="p-8">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-2xl font-black text-white flex items-center gap-3">
                  <Copy className="w-6 h-6 text-cyan-500" />
                  Clone Repository
                </h3>
                <button onClick={() => setShowCloneModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
            
              <div className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Source</label>
                    <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm truncate">
                      {showCloneModal.name}
                    </div>
                  </div>
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Name</label>
                    <input 
                      type="text" 
                      value={cloneData.destinationName}
                      onChange={(e) => setCloneData((prev: any) => ({ ...prev, destinationName: e.target.value }))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 focus:border-cyan-500 transition-all font-bold"
                      placeholder="New Repository Name"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Folder Path</label>
                  <div className="flex gap-2">
                    <input 
                      type="text" 
                      value={cloneData.path}
                      onChange={(e) => setCloneData((prev: any) => ({ ...prev, path: e.target.value }))}
                      className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 focus:border-cyan-500 transition-all font-mono text-sm"
                      placeholder="/absolute/path/to/data"
                    />
                    <button 
                      onClick={() => openBrowser(cloneData.path, (path) => setCloneData((prev: any) => ({ ...prev, path })))}
                      className="bg-slate-800 hover:bg-slate-700 text-slate-200 p-3 rounded-2xl transition-all"
                      title="Browse Directory"
                    >
                      <Folder className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex gap-3 mt-8">
                <button 
                  onClick={() => setShowCloneModal(null)}
                  className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                >
                  Cancel
                </button>
                <button 
                  onClick={() => cloneMutation.mutate({ sourceName: showCloneModal.name, ...cloneData })}
                  disabled={cloneMutation.isPending || !cloneData.destinationName || !cloneData.path}
                  className="flex-1 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-cyan-900/20 transition-all flex items-center justify-center gap-2"
                >
                  {cloneMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Copy className="w-5 h-5" />}
                  Create Clone
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Move Modal */}
      {showMoveModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="p-8">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-2xl font-black text-white flex items-center gap-3">
                  <Move className="w-6 h-6 text-violet-500" />
                  Rename Repository
                </h3>
                <button onClick={() => setShowMoveModal(null)} className="p-2 hover:bg-slate-800 rounded-xl transition-colors">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
            
              <div className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">Current Name</label>
                    <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-4 text-white font-mono text-sm truncate">
                      {showMoveModal.name}
                    </div>
                  </div>
                  <div>
                    <label className="block text-[10px] uppercase font-black text-slate-500 tracking-widest mb-2 px-1">New Name</label>
                    <input 
                      type="text" 
                      value={moveData.destinationName}
                      onChange={(e) => setMoveData((prev: any) => ({ ...prev, destinationName: e.target.value }))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 text-white focus:outline-none focus:ring-2 focus:ring-violet-500/50 focus:border-violet-500 transition-all font-bold"
                      placeholder="New Repository Name"
                    />
                  </div>
                </div>
              </div>

              <div className="flex gap-3 mt-8">
                <button 
                  onClick={() => setShowMoveModal(null)}
                  className="px-6 py-3 rounded-2xl font-bold text-slate-400 hover:bg-slate-800 transition-all"
                >
                  Cancel
                </button>
                <button 
                  onClick={() => moveRepoMutation.mutate({ sourceName: showMoveModal.name, destinationName: moveData.destinationName })}
                  disabled={moveRepoMutation.isPending || !moveData.destinationName}
                  className="flex-1 bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white font-bold py-3 rounded-2xl shadow-lg shadow-violet-900/20 transition-all flex items-center justify-center gap-2"
                >
                  {moveRepoMutation.isPending ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Move className="w-5 h-5" />}
                  Rename
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

import { useState, useEffect } from 'react'
import { useMutation } from '@tanstack/react-query'
import axios from 'axios'
import { Folder, X, Database, Activity, ChevronRight, History } from 'lucide-react'

export interface BrowserItem {
  name: string;
  path: string;
  isDirectory: boolean;
}

export interface BrowserResponse {
  currentPath: string;
  parentPath: string | null;
  items: BrowserItem[];
}

interface BrowserModalProps {
  show: boolean;
  onClose: () => void;
  onSelect: (path: string) => void;
  initialPath?: string;
}

const HISTORY_KEY = 'dedup-browser-history';

export const BrowserModal = ({ show, onClose, onSelect, initialPath }: BrowserModalProps) => {
  const [browserData, setBrowserData] = useState<BrowserResponse | null>(null);
  const [browserShowHidden, setBrowserShowHidden] = useState(false);
  const [browserViewMode, setBrowserViewMode] = useState<'list' | 'grid'>('grid');
  const [history, setHistory] = useState<string[]>([]);

  useEffect(() => {
    const storedHistory = localStorage.getItem(HISTORY_KEY);
    if (storedHistory) {
      try {
        setHistory(JSON.parse(storedHistory));
      } catch (e) {
        console.error('Failed to parse history', e);
      }
    }
  }, []);

  const addToHistory = (path: string) => {
    const newHistory = [path, ...history.filter(p => p !== path)].slice(0, 5);
    setHistory(newHistory);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(newHistory));
  };

  const browseMutation = useMutation({
    mutationFn: async (path?: string) => {
      const response = await axios.get('/api/utils/browse', {
        params: {
          path,
          showHidden: browserShowHidden
        }
      })
      return response.data as BrowserResponse
    },
    onSuccess: (data) => {
      setBrowserData(data);
    }
  });

  useEffect(() => {
    if (show) {
      browseMutation.mutate(browserData?.currentPath || initialPath);
    }
  }, [show, browserShowHidden]);

  if (!show || !browserData) return null;

  const isWindows = browserData.currentPath.includes('\\');
  const separator = isWindows ? '\\' : '/';
  const breadcrumbs = browserData.currentPath.split(separator).filter(Boolean);

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 w-full max-w-3xl rounded-2xl shadow-2xl flex flex-col max-h-[80vh]">
        <div className="p-6 border-b border-slate-800 flex justify-between items-center">
          <h2 className="text-xl font-black text-white flex items-center gap-2">
            <Folder className="w-6 h-6 text-blue-500" />
            Browse Directory
          </h2>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setBrowserViewMode(prev => prev === 'list' ? 'grid' : 'list')}
              className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition-colors flex items-center gap-2"
              title={browserViewMode === 'list' ? 'Switch to Grid View' : 'Switch to List View'}
            >
              {browserViewMode === 'list' ? (
                <Database className="w-5 h-5" />
              ) : (
                <Activity className="w-5 h-5" />
              )}
            </button>
            <button
              onClick={() => setBrowserShowHidden(!browserShowHidden)}
              className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                browserShowHidden
                  ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
                  : 'bg-slate-800 text-slate-500 border border-transparent'
              }`}
            >
              Hidden
            </button>
            <button
              onClick={onClose}
              className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition-colors"
            >
              <X className="w-6 h-6" />
            </button>
          </div>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* History Pane */}
          {history.length > 0 && (
            <div className="w-48 border-r border-slate-800 bg-slate-950/30 p-4 flex flex-col gap-2 overflow-y-auto">
              <div className="flex items-center gap-2 text-slate-500 mb-2">
                <History className="w-4 h-4" />
                <span className="text-xs font-bold uppercase tracking-wider">History</span>
              </div>
              {history.map((path) => (
                <button
                  key={path}
                  onClick={() => browseMutation.mutate(path)}
                  className="text-left p-2 rounded-lg hover:bg-slate-800 transition-colors group"
                >
                  <div className="text-sm font-bold text-slate-300 group-hover:text-white truncate">
                    {path.split(/[\\/]/).pop() || path}
                  </div>
                  <div className="text-[10px] text-slate-500 truncate" title={path}>
                    {path}
                  </div>
                </button>
              ))}
            </div>
          )}

          <div className="flex-1 flex flex-col min-w-0">
            <div className="p-4 bg-slate-950/50 border-b border-slate-800 flex items-center gap-2 overflow-x-auto">
              <button
                onClick={() => browserData.parentPath && browseMutation.mutate(browserData.parentPath)}
                disabled={!browserData.parentPath}
                className="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white disabled:opacity-30 shrink-0"
              >
                <ChevronRight className="w-5 h-5 rotate-180" />
              </button>
              <div className="flex items-center text-xs font-mono text-slate-400 overflow-x-auto no-scrollbar py-1">
                <button
                  onClick={() => browseMutation.mutate(isWindows ? breadcrumbs[0] + '\\' : '/')}
                  className="hover:text-white hover:underline transition-colors shrink-0"
                >
                  {isWindows ? breadcrumbs[0] : 'root'}
                </button>
                {(isWindows ? breadcrumbs.slice(1) : breadcrumbs).map((part, idx) => {
                  const currentBreadcrumbPath = (isWindows ? breadcrumbs.slice(0, idx + 2) : breadcrumbs.slice(0, idx + 1)).join(separator);
                  return (
                    <div key={idx} className="flex items-center shrink-0">
                      <span className="mx-1 opacity-40">{separator}</span>
                      <button
                        onClick={() => browseMutation.mutate(isWindows ? currentBreadcrumbPath : '/' + currentBreadcrumbPath)}
                        className="hover:text-white hover:underline transition-colors"
                      >
                        {part}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4">
              {browserData.items.length === 0 && (
                <div className="p-12 text-center text-slate-500 font-medium">
                  No directories found.
                </div>
              )}
              <div className={browserViewMode === 'grid'
                ? "grid grid-cols-2 sm:grid-cols-3 gap-3"
                : "flex flex-col gap-1"
              }>
                {browserData.items.map((item) => (
                  <button
                    key={item.path}
                    onClick={() => browseMutation.mutate(item.path)}
                    className={`flex items-center gap-3 p-3 hover:bg-blue-600/10 rounded-xl text-left group transition-all border border-transparent hover:border-blue-500/20 ${
                      browserViewMode === 'grid' ? 'flex-col items-center text-center p-4' : ''
                    }`}
                  >
                    <Folder className={`text-blue-500 group-hover:scale-110 transition-transform ${
                      browserViewMode === 'grid' ? 'w-10 h-10 mb-1' : 'w-5 h-5'
                    }`} />
                    <span className={`text-sm font-bold text-slate-200 group-hover:text-white truncate w-full ${
                      browserViewMode === 'grid' ? 'text-center' : ''
                    }`}>{item.name}</span>
                  </button>
                ))}
              </div>
            </div>

            <div className="p-6 border-t border-slate-800 flex justify-end gap-3">
              <button
                onClick={onClose}
                className="px-6 py-2.5 rounded-xl font-bold text-sm text-slate-400 hover:text-white transition-all"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  addToHistory(browserData.currentPath);
                  onSelect(browserData.currentPath);
                  onClose();
                }}
                className="bg-blue-600 hover:bg-blue-500 text-white px-8 py-2.5 rounded-xl font-black text-sm transition-all shadow-lg shadow-blue-600/20"
              >
                Select Directory
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

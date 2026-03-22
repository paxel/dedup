export interface RepoStats {
  fileCount: number;
  totalSize: number;
  mimeTypeDistribution: Record<string, number>;
}

export interface Repo {
  name: string;
  absolutePath: string;
  indices: number;
  codec?: 'JSON' | 'MESSAGEPACK';
  stats?: RepoStats;
}

export interface RepoFile {
  p: string; // relativePath
  s: number; // size
  h: string; // hash
  l: number; // lastModified
  m?: string; // mimeType
  f?: string; // fingerprint
  vh?: string; // videoHash
  ph?: string; // pdfHash
  ah?: string; // audioHash
  is?: { width: number; height: number }; // imageSize
}

export interface RepoRepoFile {
  repo: Repo;
  repoFile: RepoFile;
}

export interface ProgressUpdate {
  repo?: string;
  path?: string;
  currentFile?: string;
  status?: string;
  progressPercent?: number;
  filesProcessed?: number;
  filesTotal?: number;
  hashedProcessed?: number;
  hashedTotal?: number;
  unchangedProcessed?: number;
  unchangedTotal?: number;
  directoriesProcessed?: number;
  directoriesTotal?: number;
  deletedProcessed?: number;
  deletedTotal?: number;
  duration?: string;
  eta?: string;
  endTime?: string;
  errors?: string;
  scanningActive?: boolean;
  hashingActive?: boolean;
  filesDiscovered?: number;
  directoriesDiscovered?: number;
}

export interface ErrorEvent {
  id: string;
  timestamp: number;
  repo?: string;
  message: string;
  read: boolean;
}

export interface DupeProgress {
  type: 'dupe-start' | 'dupe-processing-repo' | 'dupe-grouping-hamming' | 'dupes-finished' | 'dupe-finished';
  repo?: string;
  index?: number;
  total?: number;
  groupCount?: number;
  bitLength?: number;
  similarity?: number;
  groups?: RepoRepoFile[][];
}

export function getRelativePath(rf: RepoFile): string {
  return rf.p;
}

export function getSize(rf: RepoFile): number {
  return rf.s;
}

export function getHash(rf: RepoFile): string {
  return rf.h;
}

export function formatDate(ts: number): string {
  if (!ts) return 'Unknown'
  const d = new Date(ts)
  return d.toISOString().replace('T', ' ').substring(0, 19)
}

export function formatFileSize(size: number): string {
  if (!size || size === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(size) / Math.log(k))
  return parseFloat((size / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

export function formatSize(bytes: number | undefined): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export function getTopMimeTypes(stats?: RepoStats): { type: string; count: number; percentage: string }[] {
  if (!stats?.mimeTypeDistribution) return []
  const total = Object.values(stats.mimeTypeDistribution).reduce((a, b) => a + b, 0)
  return Object.entries(stats.mimeTypeDistribution)
    .sort(([, a], [, b]) => (b as any) - (a as any))
    .slice(0, 5)
    .map(([type, count]) => ({
      type,
      percentage: ((count as any) / total * 100).toFixed(1),
      count
    }))
}

export function getMimeColor(mime: string): string {
  const type = mime.toLowerCase()
  if (type.startsWith('image/')) return 'bg-blue-100 text-blue-800 border-blue-200'
  if (type.startsWith('video/')) return 'bg-orange-100 text-orange-800 border-orange-200'
  if (type.startsWith('audio/')) return 'bg-green-100 text-green-800 border-green-200'
  if (type.startsWith('text/')) return 'bg-yellow-100 text-yellow-800 border-yellow-200'
  if (type.includes('pdf')) return 'bg-red-100 text-red-800 border-red-200'
  if (type.includes('zip') || type.includes('compressed')) return 'bg-purple-100 text-purple-800 border-purple-200'
  return 'bg-slate-100 text-slate-800 border-slate-200'
}

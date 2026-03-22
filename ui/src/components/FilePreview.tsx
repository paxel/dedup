import { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import { FileText } from 'lucide-react'

interface FilePreviewProps {
  absolutePath: string;
  mimeType?: string;
}

export function FilePreview({ absolutePath, mimeType }: FilePreviewProps) {
  const [preview, setPreview] = useState<{ type: string; src?: string; frames?: string[] } | null>(null)
  const [error, setError] = useState(false)
  const [visible, setVisible] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) { setVisible(true); observer.disconnect() } },
      { rootMargin: '200px' }
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (!visible || !absolutePath) return
    const mime = mimeType || ''
    if (mime.startsWith('image/')) {
      setPreview({ type: 'image', src: `/api/files/preview?path=${encodeURIComponent(absolutePath)}` })
    } else if (mime.startsWith('video/')) {
      axios.get(`/api/files/preview?path=${encodeURIComponent(absolutePath)}`)
        .then(res => setPreview({ type: 'video', frames: res.data.frames }))
        .catch(() => setError(true))
    } else if (mime === 'application/pdf') {
      axios.get(`/api/files/preview?path=${encodeURIComponent(absolutePath)}`)
        .then(res => setPreview({ type: 'pdf', src: `data:image/jpeg;base64,${res.data.frame}` }))
        .catch(() => setError(true))
    } else if (mime.startsWith('audio/')) {
      setPreview({ type: 'audio', src: `/api/files/preview?path=${encodeURIComponent(absolutePath)}` })
    } else {
      setPreview(null)
    }
  }, [visible, absolutePath, mimeType])

  const placeholder = <div ref={containerRef} className="h-40 bg-slate-800 rounded flex items-center justify-center"><FileText className="w-10 h-10 text-slate-600" /></div>
  if (!visible) return placeholder
  if (error) return <div className="h-40 bg-slate-800 rounded flex items-center justify-center text-slate-500 text-xs">Preview error</div>
  if (!preview) return placeholder

  if (preview.type === 'image') {
    return <img src={preview.src} alt="preview" className="h-40 w-full object-contain bg-slate-800 rounded" onError={() => setError(true)} />
  }
  if (preview.type === 'video' && preview.frames) {
    return (
      <div className="flex gap-0.5 h-24 bg-slate-800 rounded overflow-hidden">
        {preview.frames.map((f, i) => <img key={i} src={`data:image/jpeg;base64,${f}`} alt="frame" className="flex-1 h-full object-contain" />)}
      </div>
    )
  }
  if (preview.type === 'pdf') {
    return <img src={preview.src} alt="pdf" className="h-40 w-full object-contain bg-slate-800 rounded" onError={() => setError(true)} />
  }
  if (preview.type === 'audio') {
    return <div className="p-2 bg-slate-800 rounded"><audio controls className="w-full" src={preview.src} /></div>
  }
  return null
}

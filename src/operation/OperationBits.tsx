import { Check, Copy, ImagePlus, RefreshCw, Share2, Trash2 } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import './operation-page.css'

/** Briefing longo: mostra 3 linhas e "Ver mais"; expande na propria pagina e oferece "Ver menos". */
export function ExpandableText({ text, className = '' }: { text: string; className?: string }) {
  const ref = useRef<HTMLParagraphElement>(null)
  const [open, setOpen] = useState(false)
  const [overflows, setOverflows] = useState(false)
  useLayoutEffect(() => {
    const element = ref.current
    if (!element || open) return
    const measure = () => setOverflows(element.scrollHeight > element.clientHeight + 2)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [text, open])
  return <div className={`expandable ${className}`}>
    <p ref={ref} className={open ? '' : 'is-clamped'}>{text}</p>
    {(overflows || open) && <button type="button" className="expandable-toggle" aria-expanded={open} onClick={() => setOpen(value => !value)}>{open ? 'Ver menos' : 'Ver mais'}</button>}
  </div>
}

/**
 * "Imagem de capa [+]": abre o seletor, mostra miniatura e permite trocar ou remover. Nao ocupa meia tela.
 * file/onFile: uso no formulario (a imagem sobe junto); previewUrl + onReplace/onRemove: capa ja enviada.
 */
export function CoverPicker({ previewUrl, file, onFile, onRemove, busy }: { previewUrl?: string | null; file?: File | null; onFile: (file: File) => void; onRemove?: () => void; busy?: boolean }) {
  const input = useRef<HTMLInputElement>(null)
  const [local, setLocal] = useState('')
  useEffect(() => {
    if (!file) { setLocal(''); return }
    const url = URL.createObjectURL(file)
    setLocal(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const shown = local || previewUrl
  return <div className="cover-picker">
    <span>Imagem de capa</span>
    <input ref={input} type="file" accept="image/png,image/jpeg" hidden onChange={event => { const picked = event.target.files?.[0]; event.target.value = ''; if (picked) onFile(picked) }}/>
    {shown ? <div className="cover-picker-thumb">
      <img src={shown} alt="Prévia da capa"/>
      <button type="button" disabled={busy} onClick={() => input.current?.click()} aria-label="Trocar imagem de capa"><RefreshCw/> Trocar</button>
      {onRemove && <button type="button" disabled={busy} onClick={onRemove} aria-label="Remover imagem de capa"><Trash2/> Remover</button>}
    </div> : <button type="button" className="cover-picker-add" disabled={busy} onClick={() => input.current?.click()} aria-label="Adicionar imagem de capa"><ImagePlus/> <b>+</b></button>}
    <small>PNG ou JPEG, até 2 MB.</small>
  </div>
}

/** Compartilhar: menu nativo do celular (WhatsApp e outros apps); no computador, copia o link. */
export function ShareButton({ url, title, text }: { url: string; title: string; text?: string }) {
  const [copied, setCopied] = useState(false)
  const share = async () => {
    if ('share' in navigator) {
      try { await navigator.share({ url, title, text }); return } catch (error) { if (error instanceof DOMException && error.name === 'AbortError') return }
    }
    try { await navigator.clipboard.writeText(url); setCopied(true); window.setTimeout(() => setCopied(false), 2000) }
    catch { window.prompt('Copie o link da operação:', url) }
  }
  return <button type="button" className="share-button" onClick={() => void share()} aria-label={copied ? 'Link copiado' : 'Compartilhar operação'} title="Compartilhar">
    {copied ? <Check/> : 'share' in navigator ? <Share2/> : <Copy/>}{copied && <span>Link copiado</span>}
  </button>
}

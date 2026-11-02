import { LoaderCircle, Plus } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { getReconBar, type ReconBarEntry } from '../api'
import { RankInsignia } from './RankInsignia'
import { uploadStore } from './uploadQueue'

/** Linha de Recons: o primeiro circulo e sempre "Seu Recon"; borda de radar dourado = ha Recon nao visto. */
export function ReconBar({ hidden, refreshKey, onOpen, onCreate }: { hidden: boolean; refreshKey: number; onOpen: (usernames: string[], start: number) => void; onCreate: () => void }) {
  const [items, setItems] = useState<ReconBarEntry[]>([])
  const [processing, setProcessing] = useState(false)
  const tasks = useSyncExternalStore(uploadStore.subscribe, uploadStore.getSnapshot)
  const finished = tasks.filter(task => task.purpose === 'RECON' && task.phase === 'done').length
  const pending = tasks.some(task => task.purpose === 'RECON' && !['done', 'error', 'cancelled'].includes(task.phase))
  const lastLoad = useRef(0)
  const load = useCallback(() => {
    lastLoad.current = Date.now()
    getReconBar().then(result => { setItems(result.items); setProcessing(result.ownProcessing) }).catch(() => undefined)
  }, [])
  useEffect(() => { load() }, [load, refreshKey, finished])
  // Recon de video em processamento: confere de novo em alguns segundos.
  useEffect(() => {
    if (!processing) return
    const timer = window.setTimeout(load, 5000)
    return () => window.clearTimeout(timer)
  }, [processing, load, items])
  const own = items.find(item => item.own)
  const others = items.filter(item => !item.own)
  const order = [...(own ? [own] : []), ...others].map(item => item.username)
  return <div className={`recon-bar ${hidden ? 'recon-bar--hidden' : ''}`} aria-hidden={hidden || undefined}>
    <div className="recon-bar-track" role="list" aria-label="Recons">
      <div className="recon-chip" role="listitem">
        <button type="button" className={`recon-ring ${own ? (own.hasUnseen ? 'recon-ring--unseen' : 'recon-ring--seen') : 'recon-ring--empty'}`}
          onClick={() => own ? onOpen(order, 0) : onCreate()} aria-label={own ? 'Ver seu Recon' : 'Criar seu Recon'} tabIndex={hidden ? -1 : 0}>
          <span className="recon-avatar">{own ? own.callsign.charAt(0).toUpperCase() : <Plus/>}</span>
        </button>
        {(own || processing || pending) && <button type="button" className="recon-add" onClick={onCreate} aria-label="Criar novo Recon" tabIndex={hidden ? -1 : 0}>
          {processing || pending ? <LoaderCircle className="spin"/> : <Plus/>}
        </button>}
        <span className="recon-name">Seu Recon</span>
      </div>
      {others.map(item => <div className="recon-chip" role="listitem" key={item.username}>
        <button type="button" className={`recon-ring ${item.hasUnseen ? 'recon-ring--unseen' : 'recon-ring--seen'}`} tabIndex={hidden ? -1 : 0}
          onClick={() => onOpen(order, order.indexOf(item.username))} aria-label={`Recon de @${item.username}${item.hasUnseen ? ', não visto' : ''}`}>
          <span className="recon-avatar">{item.callsign.charAt(0).toUpperCase()}<RankInsignia code={item.rankCode} size={14}/></span>
        </button>
        <span className="recon-name">{item.username}</span>
      </div>)}
    </div>
  </div>
}

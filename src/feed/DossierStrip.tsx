import { Archive, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { deleteDossier, getDossiers, mediaUrl, type DossierSummary } from '../api'
import { DossierViewer } from './ReconViewer'

/** Destaques fixos do perfil: cada circulo e um Dossie com Recons guardados. */
export function DossierStrip({ username, own }: { username: string; own: boolean }) {
  const [items, setItems] = useState<DossierSummary[] | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const load = useCallback(() => { getDossiers(username).then(setItems).catch(() => setItems([])) }, [username])
  useEffect(() => { load() }, [load])
  if (!items) return null
  if (!items.length) return own ? <section className="dossier-strip dossier-strip--empty"><Archive/><p>Guarde seus Recons em <b>Dossiês</b>: abra um Recon seu e toque em "Salvar no Dossiê".</p></section> : null
  return <section className="dossier-strip" aria-label="Dossiês">
    <h2>Dossiês</h2>
    <div className="dossier-track" role="list">{items.map(item => <div className="dossier-chip" role="listitem" key={item.id}>
      <button type="button" className="dossier-cover" onClick={() => setOpen(item.id)} aria-label={`Abrir Dossiê ${item.title}, ${item.count} Recons`}>
        {item.coverUrl ? <img src={mediaUrl(item.coverUrl)} alt=""/> : <Archive/>}
      </button>
      <span>{item.title}</span>
      {own && <button type="button" className="dossier-delete" aria-label={`Apagar Dossiê ${item.title}`}
        onClick={() => { if (window.confirm(`Apagar o Dossiê "${item.title}"? Os Recons já expirados saem do ar.`)) void deleteDossier(item.id).then(load) }}><Trash2/></button>}
    </div>)}</div>
    {open && <DossierViewer dossierId={open} onClose={() => setOpen(null)} onChanged={load}/>}
  </section>
}

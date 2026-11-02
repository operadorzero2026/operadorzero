import { ShieldAlert } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { getFeedReports, resolveFeedReport, type FeedReport } from '../api'
import { REPORT_REASONS } from './reportReasons'

const TARGET = { POST: 'Post', COMMENT: 'Comentário', RECON: 'Recon' }

/** Fila de denuncias do feed (posts, comentarios e Recons), so para moderadores e administradores. */
export function FeedModerationPanel() {
  const [reports, setReports] = useState<FeedReport[] | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(() => {
    getFeedReports().then(items => { setReports(items); setError('') })
      .catch(e => setError(e instanceof Error ? e.message : 'Não foi possível carregar as denúncias.'))
  }, [])
  useEffect(() => { load() }, [load])
  const resolve = async (report: FeedReport, action: 'DISMISS' | 'REMOVE') => {
    const resolution = action === 'REMOVE' ? window.prompt('Motivo da remoção (registrado na auditoria):') ?? undefined : undefined
    if (action === 'REMOVE' && resolution === undefined) return
    try { await resolveFeedReport(report.id, action, resolution); load() } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível concluir.') }
  }
  const open = reports?.filter(report => report.status === 'OPEN') ?? []
  return <section className="module-section feed-moderation" aria-labelledby="feed-moderation-title">
    <div className="module-section-title"><div><small>MODERAÇÃO</small><h2 id="feed-moderation-title">Denúncias do feed{open.length ? ` (${open.length})` : ''}</h2></div><ShieldAlert/></div>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}
    {reports && !open.length && <p className="module-empty">Nenhuma denúncia aberta.</p>}
    <ul>{open.map(report => <li key={report.id}>
      <p><b>{TARGET[report.targetType]}</b> · {REPORT_REASONS.find(reason => reason.value === report.reason)?.label ?? report.reason} · por @{report.reporterUsername}</p>
      {report.excerpt && <blockquote>{report.excerpt}</blockquote>}
      {report.details && <p className="feed-moderation-details">{report.details}</p>}
      <div><button type="button" className="module-secondary" onClick={() => void resolve(report, 'DISMISS')}>Manter</button>
        <button type="button" className="button button--small" onClick={() => void resolve(report, 'REMOVE')}>Remover conteúdo</button></div>
    </li>)}</ul>
  </section>
}

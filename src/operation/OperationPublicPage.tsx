import { ArrowRight, CalendarDays, Clock3, MapPin, Navigation, Target, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getOperationPage, publicMediaUrl, type OperationPage } from '../api'
import { ExpandableText, ShareButton } from './OperationBits'
import { OperationDocuments } from './OperationDocuments'
import { OperationRegistrationPanel } from './OperationRegistrationPanel'
import './operation-page.css'

const MODALITIES: Record<string, string> = {
  ELIMINATION: 'Mata-mata', CONQUEST: 'Conquista', CAPTURE_THE_FLAG: 'Captura de bandeira', BASE_DEFENSE: 'Defesa de base',
  ATTACK_DEFENSE: 'Ataque e defesa', ESCORT: 'Escolta', RESCUE: 'Resgate', DOMINATION: 'Dominação', OBJECTIVES: 'Missão por objetivos',
  CHAMPIONSHIP: 'Campeonato', THEMED: 'Operação temática', CUSTOM: 'Personalizada',
}
const day = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
const hour = (value?: string | null) => value ? value.slice(0, 5) : '—'
const money = (value?: number | null) => value ? value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : 'Gratuita'

/**
 * Pagina permanente da operacao (destino do link compartilhado). Funciona sem login; depois do jogo vira historico.
 * Logado, mostra a inscricao e os documentos. Ao entrar ou criar conta, a pessoa volta para esta mesma pagina.
 */
export function OperationPublicPage({ operationId, loggedIn, onLogin, onSignup, onOpenFeed }: {
  operationId: string; loggedIn: boolean; onLogin: () => void; onSignup: () => void; onOpenFeed: () => void
}) {
  const [page, setPage] = useState<OperationPage | null>(null)
  const [error, setError] = useState('')
  const [version, setVersion] = useState(0)
  useEffect(() => {
    let active = true
    getOperationPage(operationId).then(result => { if (active) { setPage(result); setError('') } })
      .catch(() => { if (active) setError('Operação não encontrada.') })
    return () => { active = false }
  }, [operationId, version])
  useEffect(() => { if (page) document.title = `${page.name} · Operador Zero` }, [page])

  const state = !page ? '' : page.status === 'CANCELLED' ? 'Operação cancelada' : page.finished ? 'Operação realizada' : page.registrationOpen ? 'Inscrições abertas' : 'Inscrições encerradas'
  return <main className="op-page">
    <header className="op-page-bar">
      <a className="feed-logo" href="/" aria-label="Operador Zero, início">OPERADOR <b>ZERO</b></a>
      <span/>
      {page && <ShareButton url={page.shareUrl} title={page.name} text={`${page.name} · ${day(page.date)}`}/>}
      {loggedIn ? <button type="button" className="button button--small" onClick={onOpenFeed}>Feed <ArrowRight/></button>
        : <button type="button" className="button button--small" onClick={onLogin}>Entrar</button>}
    </header>
    {error ? <section className="op-page-empty" role="alert"><h1>{error}</h1><a className="button button--small" href="/">Ver agenda</a></section>
      : !page ? <section className="op-page-empty"><p>Carregando…</p></section>
      : <article className="op-page-body">
        {page.coverUrl ? <img className="op-page-cover" src={publicMediaUrl(page.coverUrl)} alt={`Capa de ${page.name}`}/>
          : <div className="op-page-cover op-page-cover--empty"><Target/></div>}
        <p className={`op-page-state ${page.registrationOpen ? 'is-open' : ''}`}>{state}</p>
        <h1>{page.name}</h1>
        <p className="op-page-org">Organização: <b>{page.organizerCallsign}</b></p>
        <ul className="op-page-facts">
          <li><CalendarDays/> <span>{day(page.date)}</span></li>
          <li><Clock3/> <span>Concentração {hour(page.presentationTime)} · Início {hour(page.startTime)} · Término {hour(page.endTime)}</span></li>
          <li><MapPin/> <span>{page.fieldName} · {page.city}/{page.stateCode}{page.directionsUrl && <> · <a href={page.directionsUrl} target="_blank" rel="noopener noreferrer"><Navigation/> Como chegar</a></>}</span></li>
          <li><Users/> <span>{page.modality === 'CUSTOM' && page.customModality ? page.customModality : MODALITIES[page.modality] ?? page.modality} · {money(page.price)} · {page.participantCount} {page.participantCount === 1 ? 'inscrito' : 'inscritos'}</span></li>
        </ul>
        {(page.briefing || page.description) && <section className="op-page-briefing"><h2>Briefing</h2><ExpandableText text={page.briefing || page.description}/></section>}
        {loggedIn ? <>
          <OperationDocuments operationId={page.id}/>
          <OperationRegistrationPanel operationId={page.id} onChanged={() => setVersion(value => value + 1)}/>
        </> : page.registrationOpen ? <section className="op-page-join">
          <p>Entre na sua conta para se inscrever. Você volta para esta operação depois do login.</p>
          <div><button type="button" className="button" onClick={onLogin}>Entrar e me inscrever</button><button type="button" className="button button--ghost" onClick={onSignup}>Criar conta</button></div>
        </section> : null}
      </article>}
  </main>
}

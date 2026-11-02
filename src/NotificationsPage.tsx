import { Bell, CheckCheck, Lock, Medal, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { getMyAchievements, getNotifications, markAllNotificationsRead, markNotificationRead, type AchievementPage, type NotificationPage, type UserNotification } from './api'

export function NotificationsPage({ onOpen, onChanged }: { onOpen: (view: string) => void; onChanged: () => void }) {
  const [page, setPage] = useState<NotificationPage | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setError('')
    try { setPage(await getNotifications()) } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar as notificações.') }
  }, [])
  useEffect(() => { void load() }, [load])
  const open = async (item: UserNotification) => {
    if (!item.readAt) { try { await markNotificationRead(item.id); onChanged() } catch { /* a navegacao segue mesmo sem marcar */ } }
    if (item.targetView) onOpen(item.targetView)
    else void load()
  }
  const readAll = async () => {
    try { await markAllNotificationsRead(); onChanged(); await load() } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível concluir.') }
  }
  return <main className="module-page"><header><p>AVISOS</p><h1>Notificações</h1><span>Convites, pedidos de equipe e mudanças nas suas operações.</span></header>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error} <button type="button" onClick={() => void load()}><RefreshCw/> Tentar novamente</button></p>}
    <section className="module-section"><div className="module-section-title"><div><small>RECENTES</small><h2>{page ? (page.unread ? `${page.unread} não ${page.unread === 1 ? 'lida' : 'lidas'}` : 'Tudo em dia') : 'Carregando…'}</h2></div>{page && page.unread > 0 ? <button type="button" className="module-secondary" onClick={() => void readAll()}><CheckCheck/> Marcar todas como lidas</button> : <Bell/>}</div>
      {page && page.items.length === 0 && <p className="module-empty">Você ainda não recebeu notificações.</p>}
      {page && page.items.length > 0 && <ul className="notification-list">{page.items.map(item => <li key={item.id} className={item.readAt ? '' : 'unread'}>
        <button type="button" onClick={() => void open(item)}><b>{item.title}</b>{item.body && <span>{item.body}</span>}<small>{new Date(item.createdAt).toLocaleString('pt-BR')}</small></button>
      </li>)}</ul>}
    </section>
  </main>
}

export function AchievementsPage() {
  const [page, setPage] = useState<AchievementPage | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { getMyAchievements().then(setPage).catch(e => setError(e instanceof Error ? e.message : 'Não foi possível carregar as conquistas.')) }, [])
  const categories = page ? [...new Set(page.items.map(item => item.category))] : []
  return <main className="module-page"><header><p>PROGRESSO</p><h1>Conquistas</h1><span>Calculadas somente com participações confirmadas, desempenhos homologados e atividade real.</span></header>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}
    {!page && !error && <p className="module-empty">Carregando conquistas…</p>}
    {page && <section className="module-section"><div className="module-section-title"><div><small>RESUMO</small><h2>{page.unlocked} de {page.items.length} desbloqueadas</h2></div><Medal/></div></section>}
    {page && categories.map(category => <section className="module-section" key={category}><div className="module-section-title"><div><small>CATEGORIA</small><h2>{category}</h2></div></div>
      <div className="achievement-grid">{page.items.filter(item => item.category === category).map(item => <article key={item.code} className={item.unlocked ? 'unlocked' : ''}>
        <span className="achievement-icon">{item.unlocked ? <Medal/> : <Lock/>}</span>
        <b>{item.name}</b><p>{item.description}</p>
        <div className="achievement-progress" role="progressbar" aria-valuemin={0} aria-valuemax={item.target} aria-valuenow={item.current}><i style={{ width: `${Math.round(item.current / item.target * 100)}%` }}/></div>
        <small>{item.current}/{item.target}</small>
      </article>)}</div>
    </section>)}
  </main>
}

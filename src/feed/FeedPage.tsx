import { Bell } from 'lucide-react'
import { useState } from 'react'
import { FeedList } from './FeedList'

export type FeedTab = 'for-you' | 'following'

const tabs: { id: FeedTab; label: string }[] = [
  { id: 'for-you', label: 'Para você' },
  { id: 'following', label: 'Seguindo' },
]

export function FeedPage({ unread, onOpenNotifications, onFindOperators }: { unread: number; onOpenNotifications: () => void; onFindOperators: () => void }) {
  const [tab, setTab] = useState<FeedTab>('for-you')
  return <section className="feed-screen" aria-label="Feed">
    <div className="feed-column">
      <header className="feed-topbar">
        <span className="feed-logo" aria-label="OperadorZero">OPERADOR <b>ZERO</b></span>
        <nav className="feed-tabs" role="tablist" aria-label="Escolha do feed">
          {tabs.map(item => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}>{item.label}</button>)}
        </nav>
        <button type="button" className="feed-icon-button" onClick={onOpenNotifications} aria-label={unread > 0 ? `Notificações, ${unread} não lidas` : 'Notificações'}>
          <Bell/>{unread > 0 && <em>{unread > 99 ? '99+' : unread}</em>}
        </button>
      </header>
      <div role="tabpanel" aria-label={tabs.find(item => item.id === tab)?.label}>
        <FeedList key={tab} tab={tab} onFindOperators={onFindOperators}/>
      </div>
    </div>
  </section>
}

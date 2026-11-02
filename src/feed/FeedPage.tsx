import { Bell, Hash, X } from 'lucide-react'
import { useCallback, useState } from 'react'
import { FeedList } from './FeedList'
import { ReconBar } from './ReconBar'
import { ReconViewer } from './ReconViewer'

export type FeedTab = 'for-you' | 'following'

const tabs: { id: FeedTab; label: string }[] = [
  { id: 'for-you', label: 'Para você' },
  { id: 'following', label: 'Seguindo' },
]

export function FeedPage({ unread, onOpenNotifications, onFindOperators, onCreateRecon, onOpenHonorBoard }: { unread: number; onOpenNotifications: () => void; onFindOperators: () => void; onCreateRecon: () => void; onOpenHonorBoard: () => void }) {
  const [tab, setTab] = useState<FeedTab>('for-you')
  const [scrolled, setScrolled] = useState(false)
  const [viewer, setViewer] = useState<{ usernames: string[]; start: number } | null>(null)
  const [barKey, setBarKey] = useState(0)
  const [hashtag, setHashtag] = useState<string | null>(null)
  const refreshBar = useCallback(() => setBarKey(value => value + 1), [])
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
      {/* Recons so aparecem no topo do feed; somem assim que a pessoa rola para o proximo post. */}
      {hashtag && <div className="feed-hashtag-chip"><Hash/><b>{hashtag}</b><button type="button" onClick={() => setHashtag(null)} aria-label="Sair da hashtag"><X/></button></div>}
      <ReconBar hidden={scrolled || Boolean(hashtag)} refreshKey={barKey} onOpen={(usernames, start) => setViewer({ usernames, start })} onCreate={onCreateRecon}/>
      <div role="tabpanel" aria-label={tabs.find(item => item.id === tab)?.label}>
        <FeedList key={`${tab}:${hashtag ?? ''}`} tab={tab} hashtag={hashtag} onHashtag={setHashtag} onFindOperators={onFindOperators} onScrolledChange={setScrolled} onOpenHonorBoard={onOpenHonorBoard}/>
      </div>
    </div>
    {viewer && <ReconViewer usernames={viewer.usernames} start={viewer.start} onClose={() => setViewer(null)} onChanged={refreshBar}/>}
  </section>
}

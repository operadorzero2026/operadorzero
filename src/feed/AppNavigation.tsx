import { Building2, ClipboardCheck, Clapperboard, Compass, Film, Image as ImageIcon, LayoutDashboard, Map, Medal, Bell, Plus, Target, Trophy, UserRound, Users, X, type LucideIcon } from 'lucide-react'
import { useEffect, useRef } from 'react'

export type HubId = 'Explorar' | 'Jogos' | 'Perfil'

type HubLink = { view: string; label: string; description: string; icon: LucideIcon }

// Funcoes que ja existiam no painel, agrupadas nos icones da barra inferior.
const hubs: Record<HubId, { eyebrow: string; title: string; links: HubLink[] }> = {
  Explorar: { eyebrow: 'BUSCAR', title: 'Explorar', links: [
    { view: 'Operadores', label: 'Operadores', description: 'Encontre e siga operadores.', icon: UserRound },
    { view: 'Minha equipe', label: 'Equipes', description: 'Busque equipes e peça para entrar.', icon: Users },
    { view: 'Rankings', label: 'Rankings', description: 'Classificação geral dos operadores.', icon: Trophy },
    { view: 'Comunidade', label: 'Comunidade', description: 'Fórum de discussões do airsoft.', icon: Compass },
  ] },
  Jogos: { eyebrow: 'OPERAÇÕES', title: 'Jogos', links: [
    { view: 'Operações', label: 'Operações', description: 'Próximos jogos e inscrições.', icon: Target },
    { view: 'Campos', label: 'Campos', description: 'Campos de airsoft cadastrados.', icon: Building2 },
    { view: 'Mapas', label: 'Mapas', description: 'Mapas e cenários dos campos.', icon: Map },
    { view: 'Desempenho', label: 'Desempenho', description: 'Registre e confira seus resultados.', icon: ClipboardCheck },
  ] },
  Perfil: { eyebrow: 'OPERADOR', title: 'Perfil', links: [
    { view: 'Meu Operador', label: 'Meu Operador', description: 'Seu perfil, conta e segurança.', icon: UserRound },
    { view: 'Minha equipe', label: 'Minha equipe', description: 'Sua equipe, convites e pedidos.', icon: Users },
    { view: 'Conquistas', label: 'Conquistas', description: 'Seu progresso na plataforma.', icon: Medal },
    { view: 'Notificações', label: 'Notificações', description: 'Convites, pedidos e avisos.', icon: Bell },
    { view: 'Visão geral', label: 'Painel', description: 'Resumo da sua jornada.', icon: LayoutDashboard },
  ] },
}

const hubOf = (view: string): HubId | 'Feed' | null => {
  if (view === 'Feed') return 'Feed'
  if (view === 'Explorar' || view === 'Jogos' || view === 'Perfil') return view
  if (['Operadores', 'Rankings', 'Comunidade'].includes(view)) return 'Explorar'
  if (['Operações', 'Campos', 'Mapas', 'Desempenho'].includes(view)) return 'Jogos'
  return 'Perfil'
}

export function BottomNav({ activeView, onSelect, onCreate }: { activeView: string; onSelect: (view: string) => void; onCreate: () => void }) {
  const current = hubOf(activeView)
  const item = (view: 'Feed' | HubId, label: string, Icon: LucideIcon) =>
    <button type="button" className={current === view ? 'active' : ''} aria-current={current === view ? 'page' : undefined} onClick={() => onSelect(view)}><Icon/><span>{label}</span></button>
  return <nav className="feed-bottom-nav" aria-label="Navegação principal">
    {item('Feed', 'Feed', Film)}
    {item('Explorar', 'Explorar', Compass)}
    <button type="button" className="feed-create-button" onClick={onCreate} aria-label="Criar post ou Recon"><Plus/></button>
    {item('Jogos', 'Jogos', Target)}
    {item('Perfil', 'Perfil', UserRound)}
  </nav>
}

export function HubPage({ hub, onSelect }: { hub: HubId; onSelect: (view: string) => void }) {
  const config = hubs[hub]
  return <main className="module-page hub-page"><header><p>{config.eyebrow}</p><h1>{config.title}</h1></header>
    <div className="hub-grid">{config.links.map(({ view, label, description, icon: Icon }) => <button type="button" key={label} onClick={() => onSelect(view)}><Icon/><span><b>{label}</b><small>{description}</small></span></button>)}</div>
  </main>
}

export function CreateSheet({ notice, onClose, onPick }: { notice?: string; onClose: () => void; onPick: (kind: 'POST' | 'RECON') => void }) {
  const first = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    first.current?.focus()
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return <div className="create-sheet-backdrop" onClick={onClose}>
    <section className="create-sheet" role="dialog" aria-modal="true" aria-labelledby="create-sheet-title" onClick={event => event.stopPropagation()}>
      <header><h2 id="create-sheet-title">Criar</h2><button type="button" className="feed-icon-button" onClick={onClose} aria-label="Fechar"><X/></button></header>
      <button ref={first} type="button" className="create-choice" onClick={() => onPick('POST')}><ImageIcon/><span><b>POST</b><small>Foto ou vídeo no feed.</small></span></button>
      <button type="button" className="create-choice" onClick={() => onPick('RECON')}><Clapperboard/><span><b>RECON</b><small>Some em 24 horas.</small></span></button>
      {notice && <p className="create-notice" role="status">{notice}</p>}
    </section>
  </div>
}

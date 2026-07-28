import { FormEvent, useEffect, useState } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  Bell,
  Building2,
  CalendarDays,
  ChevronDown,
  ClipboardCheck,
  Eye,
  EyeOff,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Mail,
  Map,
  Medal,
  Menu,
  Search,
  ShieldAlert,
  Target,
  Trophy,
  UserRound,
  Users,
  X,
} from 'lucide-react'
import {
  getCurrentSession,
  getOperations,
  getOperatorProfile,
  getOperatorRanking,
  getTeamSummary,
  login,
  logout,
  prepareGoogleLogin,
  register,
  resendVerification,
  requestPasswordRecovery,
  resetPassword,
  SessionUser,
  Operation,
  RankingEntry,
  verifyEmail,
} from './api'
import { OperatorPage } from './OperatorPage'
import { OperatorSearch } from './OperatorSearch'
import { TeamPage } from './TeamPage'
import { OperationsPage } from './OperationsPage'
import { VenuesPage } from './VenuesPage'
import { RankingsPage } from './RankingsPage'
import { PerformancePage } from './PerformancePage'
import CommunityPage from './CommunityPage'

function Brand({ compact = false }: { compact?: boolean }) {
  return <a className={`brand ${compact ? 'brand--compact' : ''}`} href="#inicio" aria-label="Operador Zero, início"><span>Operador</span><strong>Zero</strong></a>
}

type AuthMode = 'login' | 'signup' | 'recovery' | 'reset'
const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED === 'true'
const IS_STAGING = import.meta.env.PROD && import.meta.env.VITE_APP_ENV !== 'production'

function GoogleMark() {
  return <svg aria-hidden="true" viewBox="0 0 24 24"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.91h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.4Z"/><path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.42l-3.24-2.5c-.9.6-2.05.96-3.38.96-2.6 0-4.81-1.76-5.6-4.13H3.05v2.6A10 10 0 0 0 12 22Z"/><path fill="#FBBC05" d="M6.4 13.91a6 6 0 0 1 0-3.82v-2.6H3.05a10 10 0 0 0 0 9.02l3.35-2.6Z"/><path fill="#EA4335" d="M12 5.96c1.47 0 2.79.5 3.83 1.5L18.7 4.6A9.64 9.64 0 0 0 12 2a10 10 0 0 0-8.95 5.49l3.35 2.6c.79-2.37 3-4.13 5.6-4.13Z"/></svg>
}

function AuthModal({ mode, onClose, onModeChange, onAuthenticated, resetToken, initialNotice = '' }: {
  mode: AuthMode
  onClose: () => void
  onModeChange: (mode: AuthMode) => void
  onAuthenticated: (user: SessionUser) => void
  resetToken?: string
  initialNotice?: string
}) {
  const [showPassword, setShowPassword] = useState(false)
  const [notice, setNotice] = useState(initialNotice)
  const [pendingAction, setPendingAction] = useState<'form' | 'google' | null>(null)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [verificationEmail, setVerificationEmail] = useState('')
  const isRecovery = mode === 'recovery'
  const isReset = mode === 'reset'
  const title = mode === 'login' ? 'Entre no Operador Zero' : mode === 'signup' ? 'Crie sua conta' : isReset ? 'Defina uma nova senha' : 'Recupere seu acesso'
  const description = mode === 'login' ? 'Acesse sua conta com segurança.' : mode === 'signup' ? 'Crie sua identidade de operador em poucos passos.' : isReset ? 'O link será invalidado após a troca da senha.' : 'Informe seu e-mail para receber as instruções.'

  useEffect(() => { setNotice(initialNotice); setShowPassword(false); setTermsAccepted(false); setVerificationEmail('') }, [mode, initialNotice])
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.body.classList.add('modal-open')
    window.addEventListener('keydown', onKeyDown)
    return () => { document.body.classList.remove('modal-open'); window.removeEventListener('keydown', onKeyDown) }
  }, [onClose])
  useEffect(() => {
    const resumeAfterGoogle = (event: PageTransitionEvent) => {
      if (!event.persisted) return
      setPendingAction(null)
      setNotice('O acesso com Google foi cancelado. Você pode tentar novamente.')
    }
    window.addEventListener('pageshow', resumeAfterGoogle)
    return () => window.removeEventListener('pageshow', resumeAfterGoogle)
  }, [])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!AUTH_ENABLED) { setNotice('Autenticação indisponível neste ambiente.'); return }
    const data = new FormData(event.currentTarget)
    const email = String(data.get('email') ?? '').trim().toLowerCase()
    const password = String(data.get('password') ?? '')
    setPendingAction('form')
    setNotice('')
    try {
      if (isRecovery) {
        await requestPasswordRecovery(email)
        setNotice('Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.')
      } else if (isReset) {
        if (!resetToken) throw new Error('Link de recuperação inválido.')
        await resetPassword(resetToken, password)
        setNotice('Senha alterada. Volte para entrar com a nova senha.')
      } else if (mode === 'signup') {
        await register(String(data.get('displayName') ?? '').trim(), email, password, termsAccepted)
        setVerificationEmail(email)
        setNotice('Cadastro recebido. Confira também o Lixo Eletrônico. Se a mensagem não aparecer, use Reenviar confirmação.')
      } else {
        onAuthenticated(await login(email, password))
      }
    } catch (error) {
      setNotice(isRecovery ? 'Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.' : error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.')
    } finally {
      setPendingAction(null)
    }
  }

  const resendConfirmation = async () => {
    if (!verificationEmail) return
    setPendingAction('form')
    try {
      await resendVerification(verificationEmail)
      setNotice('Se o endereço puder receber a confirmação, uma nova mensagem foi enviada. Confira também o Lixo Eletrônico.')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Não foi possível solicitar um novo envio.')
    } finally {
      setPendingAction(null)
    }
  }

  const continueWithGoogle = async () => {
    if (mode === 'signup' && !termsAccepted) { setNotice('Aceite os Termos de Uso e a Política de Privacidade para criar a conta.'); return }
    setPendingAction('google')
    setNotice('Abrindo o Google para continuar. Se o servidor estiver iniciando, isso pode levar até dois minutos.')
    try {
      window.location.assign(await prepareGoogleLogin(mode === 'signup' && termsAccepted))
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Login com Google indisponível.')
      setPendingAction(null)
    }
  }

  return <div className="auth-overlay" role="presentation" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      <div className="auth-visual" aria-hidden="true"><Brand/><p>Jogue. Registre.<br/>Ranqueie. Evolua.</p><span>OPERADOR ZERO</span></div>
      <div className="auth-content">
        <button className="auth-close" onClick={onClose} aria-label="Fechar"><X/></button>
        {(isRecovery || isReset) && <button className="auth-back" onClick={() => onModeChange('login')}><ArrowLeft size={16}/> Voltar para entrar</button>}
        <div className="auth-heading"><p className="eyebrow"><span/>{isRecovery || isReset ? 'Recuperação por e-mail' : 'Acesso à plataforma'}</p><h2 id="auth-title">{title}</h2><p>{description}</p></div>
        {!AUTH_ENABLED && <p className="auth-unavailable"><ShieldAlert size={17}/> Autenticação remota temporariamente indisponível.</p>}
        {!isRecovery && !isReset && <><button className="google-button" type="button" disabled={pendingAction !== null || !AUTH_ENABLED} aria-busy={pendingAction === 'google'} onClick={continueWithGoogle}><GoogleMark/> {pendingAction === 'google' ? 'Conectando ao Google...' : 'Continuar com Google'}</button><div className="auth-divider"><span/> ou use seu e-mail <span/></div></>}
        <form className="auth-form" onSubmit={submit}>
          {mode === 'signup' && <label><span>Nome de exibição</span><div><Users/><input name="displayName" autoComplete="name" maxLength={80} required placeholder="Como devemos chamar você?"/></div></label>}
          {!isReset && <label><span>E-mail</span><div><Mail/><input name="email" type="email" autoComplete="email" maxLength={254} required placeholder="voce@exemplo.com.br"/></div></label>}
          {!isRecovery && <label><span>{isReset ? 'Nova senha' : 'Senha'}</span><div><LockKeyhole/><input name="password" type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={12} maxLength={128} required placeholder={mode === 'signup' || isReset ? '12+ caracteres, maiúscula, minúscula e número' : 'Sua senha'}/><button type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}>{showPassword ? <EyeOff/> : <Eye/>}</button></div></label>}
          {mode === 'login' && <button className="forgot-link" type="button" onClick={() => onModeChange('recovery')}>Esqueci minha senha</button>}
          {mode === 'signup' && <label className="terms-check"><input type="checkbox" required checked={termsAccepted} onChange={event => setTermsAccepted(event.target.checked)}/><span>Li e aceito os Termos de Uso e a Política de Privacidade.</span></label>}
          <button className="button auth-submit" type="submit" disabled={pendingAction !== null || !AUTH_ENABLED}>{pendingAction === 'form' ? 'Aguarde...' : isRecovery ? 'Enviar instruções' : isReset ? 'Salvar nova senha' : mode === 'login' ? 'Entrar com e-mail' : 'Criar conta com e-mail'} {pendingAction === null && <ArrowRight size={17}/>}</button>
        </form>
        {notice && <p className="auth-notice" role="status">{notice}</p>}
        {mode === 'signup' && verificationEmail && <button className="forgot-link" type="button" disabled={pendingAction !== null} onClick={resendConfirmation}>Reenviar confirmação</button>}
        {!isRecovery && !isReset && <p className="auth-switch">{mode === 'login' ? 'Ainda não tem conta?' : 'Já possui uma conta?'} <button onClick={() => onModeChange(mode === 'login' ? 'signup' : 'login')}>{mode === 'login' ? 'Criar conta' : 'Entrar'}</button></p>}
      </div>
    </section>
  </div>
}

const dashboardNav = [
  { label: 'Visão geral', icon: LayoutDashboard },
  { label: 'Operações', icon: Target },
  { label: 'Campos', icon: Building2 },
  { label: 'Mapas', icon: Map },
  { label: 'Minha equipe', icon: Users },
  { label: 'Operadores', icon: UserRound },
  { label: 'Desempenho', icon: ClipboardCheck },
  { label: 'Rankings', icon: Trophy },
  { label: 'Comunidade', icon: Users },
  { label: 'Conquistas', icon: Medal },
  { label: 'Notificações', icon: Bell },
  { label: 'Meu Operador', icon: UserRound },
]

const emptyCopy: Record<string, { title: string; description: string }> = {
  'Operações': { title: 'Nenhuma operação encontrada', description: 'Não há operações para os filtros selecionados.' },
  'Minha equipe': { title: 'Você ainda não participa de uma equipe', description: 'Crie uma equipe, consulte seus convites ou encontre equipes da sua região.' },
  'Rankings': { title: 'Você ainda não possui resultados no ranking', description: 'Participe de operações válidas para começar seu histórico.' },
  'Classificados': { title: 'Nenhum item encontrado', description: 'Não há anúncios ou procuras disponíveis no momento.' },
  'Comunidade': { title: 'Ainda não existem publicações nesta categoria', description: 'Escolha outra categoria ou volte mais tarde.' },
  'Destaques': { title: 'Nenhum destaque ativo', description: 'Não há destaques da comunidade no momento.' },
  'Conquistas': { title: 'Nenhuma conquista registrada', description: 'Seu progresso aparecerá conforme você participar das atividades.' },
}

function EmptyWorkspace({ area, onHome }: { area: string; onHome: () => void }) {
  const copy = emptyCopy[area] ?? { title: 'Nenhum conteúdo disponível', description: 'Não há itens nesta área.' }
  return <main className="real-empty-page"><h1>{area}</h1><section className="real-empty-state"><Search/><h2>{copy.title}</h2><p>{copy.description}</p><button onClick={onHome}>Voltar à visão geral <ArrowRight/></button></section></main>
}

function Dashboard({ onLogout, onUserUpdated, onNavigateCommunity, user }: { onLogout: () => Promise<boolean>; onUserUpdated: (user: SessionUser) => void; onNavigateCommunity: () => void; user: SessionUser }) {
  const [profileOpen, setProfileOpen] = useState(false)
  const [activeView, setActiveView] = useState('Visão geral')
  const [logoutPending, setLogoutPending] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [overview, setOverview] = useState<{ operations: Operation[]; teamTotal: number; ranking: RankingEntry[] }>({ operations: [], teamTotal: 0, ranking: [] })
  const [overviewLoading, setOverviewLoading] = useState(true)
  const [overviewError, setOverviewError] = useState('')
  const callsign = user.callsign || user.displayName
  const initial = callsign.charAt(0).toUpperCase()
  useEffect(() => {
    let active = true
    Promise.all([getOperatorProfile(), getOperations(), getTeamSummary(), getOperatorRanking()])
      .then(([profile, operationResponse, teamSummary, rankingResponse]) => {
        if (!active) return
        const today = new Date().toISOString().slice(0, 10)
        const available = operationResponse.items.filter(operation => operation.operationDate >= today && !['DRAFT', 'FINISHED', 'CANCELLED'].includes(operation.status))
        const proximity = (operation: Operation) => operation.city === profile.city && operation.stateCode === profile.stateCode ? 0 : operation.stateCode === profile.stateCode ? 1 : 2
        available.sort((left, right) => proximity(left) - proximity(right) || `${left.operationDate}T${left.startTime}`.localeCompare(`${right.operationDate}T${right.startTime}`))
        setOverview({ operations: available.slice(0, 3), teamTotal: teamSummary.total, ranking: rankingResponse.items.slice(0, 3) })
      })
      .catch(() => active && setOverviewError('Não foi possível atualizar todos os dados da visão geral.'))
      .finally(() => active && setOverviewLoading(false))
    return () => { active = false }
  }, [])
  const operationDate = (value: string) => new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(new Date(`${value}T12:00:00`))
  return <div className="app-shell">
    <aside className="app-sidebar"><Brand compact/><nav aria-label="Navegação do operador">{dashboardNav.map(({ label, icon: Icon }) => <button onClick={() => label === 'Comunidade' ? onNavigateCommunity() : setActiveView(label)} className={activeView === label ? 'active' : ''} key={label}><Icon/><span>{label}</span>{activeView === label && <i/>}</button>)}</nav><div className="sidebar-footer"><p>Jogue. Registre.<br/><b>Evolua.</b></p></div></aside>
    <div className="app-main">
      <header className="app-topbar"><OperatorSearch/><div className="profile-control"><button onClick={() => setProfileOpen(!profileOpen)} aria-expanded={profileOpen}><span className="profile-avatar">{initial}</span><span><b>{callsign}</b><small>@{user.username}</small></span><ChevronDown/></button>{profileOpen && <div className="profile-menu"><p>{user.email}</p><button disabled={logoutPending} onClick={async () => { setLogoutPending(true); setLogoutError(''); const completed = await onLogout(); setLogoutPending(false); if (!completed) setLogoutError('Não foi possível encerrar a sessão. Verifique sua conexão e tente novamente.') }}><LogOut/> {logoutPending ? 'Encerrando...' : 'Sair da conta'}</button>{logoutError && <small className="logout-error" role="alert">{logoutError}</small>}</div>}</div></header>
      {activeView === 'Meu Operador' ? <OperatorPage onUserUpdated={updated => onUserUpdated({ ...user, ...updated })}/> : activeView === 'Minha equipe' ? <TeamPage/> : activeView === 'Operações' ? <OperationsPage/> : activeView === 'Campos' ? <VenuesPage mode="fields"/> : activeView === 'Mapas' ? <VenuesPage mode="maps"/> : activeView === 'Desempenho' ? <PerformancePage/> : activeView === 'Rankings' ? <RankingsPage/> : activeView !== 'Visão geral' ? <EmptyWorkspace area={activeView} onHome={() => setActiveView('Visão geral')}/> : <main className="dashboard real-dashboard">
        <section className="dashboard-welcome"><div><p>BEM-VINDO</p><h1>Olá, <em>{callsign}.</em></h1></div></section>
        <section className="operator-strip real-operator-strip"><div className="operator-identity"><span className="operator-avatar">{initial}</span><div><small>OPERADOR</small><h2>{callsign}</h2><p>{user.displayName} · @{user.username}</p></div></div><div><small>STATUS DO PERFIL</small><strong>ATIVO</strong><span>Conta disponível</span></div></section>
        <section className="real-dashboard-intro"><div><p className="eyebrow"><span/> SUA JORNADA</p><h2>Comece pelo que importa.</h2><p>Encontre operações, organize sua equipe e acompanhe sua participação na comunidade.</p></div></section>
        {overviewError && <p className="overview-warning" role="status">{overviewError}</p>}
        <div className="overview-grid">
          <section className="overview-card overview-operations"><header><div><small>PRÓXIMAS DATAS</small><h3>Operações</h3></div><button onClick={() => setActiveView('Operações')} aria-label="Ver todas as operações"><ArrowRight/></button></header>{overviewLoading ? <p className="overview-muted">Atualizando agenda...</p> : overview.operations.length ? <div className="overview-operation-list">{overview.operations.map(operation => <button key={operation.id} onClick={() => setActiveView('Operações')}><time>{operationDate(operation.operationDate)}</time><span><b>{operation.name}</b><small>{operation.city} · {operation.stateCode} · {operation.startTime.slice(0,5)}</small></span></button>)}</div> : <p className="overview-muted">Nenhuma operação disponível nas próximas datas.</p>}</section>
          <section className="overview-card overview-team"><header><div><small>COMUNIDADE ATIVA</small><h3>Equipes</h3></div><Users/></header><strong>{overviewLoading ? '—' : overview.teamTotal.toLocaleString('pt-BR')}</strong><p>equipes cadastradas no OperadorZero</p><button className="overview-link" onClick={() => setActiveView('Minha equipe')}>Acessar equipes <ArrowRight/></button></section>
          <section className="overview-card overview-ranking"><header><div><small>CLASSIFICAÇÃO GERAL</small><h3>Ranking</h3></div><Trophy/></header>{overviewLoading ? <p className="overview-muted">Atualizando ranking...</p> : overview.ranking.length ? <ol>{overview.ranking.map(entry => <li key={entry.operatorId}><em>{entry.position}</em><span><b>{entry.callsign || entry.displayName}</b><small>{entry.teamName || `${entry.city || 'Local não informado'} · ${entry.stateCode || 'BR'}`}</small></span><strong>{entry.finalScore.toLocaleString('pt-BR')} pts</strong></li>)}</ol> : <p className="overview-muted">O ranking ainda não possui resultados.</p>}<button className="overview-link" onClick={() => setActiveView('Rankings')}>Ver ranking completo <ArrowRight/></button></section>
          <button type="button" className="overview-card overview-community" onClick={onNavigateCommunity}><h3>Comunidade</h3><p>O ponto de encontro de operadores, equipes e organizadores do airsoft.</p></button>
        </div>
      </main>}
    </div>
    <nav className="mobile-app-nav" aria-label="Navegação mobile">{dashboardNav.filter(item => ['Visão geral','Operações','Campos','Notificações','Meu Operador'].includes(item.label)).map(({label,icon:Icon}) => <button onClick={() => setActiveView(label)} className={activeView === label ? 'active' : ''} key={label}><Icon/><span>{label}</span></button>)}</nav>
  </div>
}

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode | null>(null)
  const [currentUser, setCurrentUser] = useState<SessionUser | null>(null)
  const [sessionRestoring, setSessionRestoring] = useState(AUTH_ENABLED)
  const [authNotice, setAuthNotice] = useState('')
  const [resetTokenValue, setResetTokenValue] = useState('')
  const [currentPath, setCurrentPath] = useState(window.location.pathname)

  useEffect(() => {
    const close = () => setMenuOpen(false)
    window.addEventListener('resize', close)
    return () => window.removeEventListener('resize', close)
  }, [])
  useEffect(() => {
    const updatePath = () => setCurrentPath(window.location.pathname)
    window.addEventListener('popstate', updatePath)
    return () => window.removeEventListener('popstate', updatePath)
  }, [])
  useEffect(() => {
    if (!AUTH_ENABLED) return
    let active = true
    const restore = async () => {
      const params = new URLSearchParams(window.location.search)
      const action = params.get('action')
      const token = params.get('token')
      const oauth = params.get('auth')
      const oauthCode = params.get('code')
      if (action || oauth) window.history.replaceState({}, document.title, window.location.pathname + window.location.hash)
      try {
        if (action === 'verify-email' && token) {
          await verifyEmail(token)
          if (active) { setAuthNotice('E-mail confirmado. Entre com sua senha para continuar.'); setAuthMode('login') }
        } else if (action === 'reset-password' && token && active) {
          setResetTokenValue(token); setAuthMode('reset')
        } else if (oauth === 'error' && active) {
          const message = oauthCode === 'ACCOUNT_LINK_REQUIRED'
            ? 'Este e-mail já existe. Entre com senha para vincular o Google com segurança.'
            : oauthCode === 'TERMS_REQUIRED'
              ? 'Esta conta Google ainda não está cadastrada. Selecione Criar conta, aceite os Termos e tente novamente.'
              : 'Não foi possível concluir o acesso com Google.'
          setAuthNotice(message); setAuthMode('login')
        }
        const session = await getCurrentSession()
        if (active) {
          setCurrentUser(session)
          if (oauth === 'success' && !session) {
            setAuthNotice('O Google confirmou o acesso, mas a sessão não foi restaurada. Tente novamente ou use e-mail e senha.')
            setAuthMode('login')
          }
        }
      } catch (error) {
        if (active) { setAuthNotice(error instanceof Error ? error.message : 'Não foi possível validar o acesso.'); setAuthMode('login') }
      } finally {
        if (active) setSessionRestoring(false)
      }
    }
    void restore()
    return () => { active = false }
  }, [])

  const navigate = (path: string) => {
    if (window.location.pathname !== path) window.history.pushState({}, '', path)
    setCurrentPath(path)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const authenticated = (user: SessionUser) => { setAuthMode(null); setAuthNotice(''); setCurrentUser(user) }
  const authModal = authMode && <AuthModal mode={authMode} onClose={() => { setAuthMode(null); setAuthNotice('') }} onModeChange={setAuthMode} onAuthenticated={authenticated} resetToken={resetTokenValue} initialNotice={authNotice}/>

  if (currentPath === '/comunidade' || currentPath.startsWith('/comunidade/')) {
    return <>
      <CommunityPage path={currentPath} user={currentUser} onNavigate={navigate} onRequireAuth={() => setAuthMode('login')}/>
      {authModal}
    </>
  }

  if (currentUser) return <Dashboard user={currentUser} onUserUpdated={setCurrentUser} onNavigateCommunity={() => navigate('/comunidade')} onLogout={async () => {
    try {
      await logout()
      setCurrentUser(null)
      return true
    } catch {
      return false
    }
  }}/>

  const nav = ['Operações', 'Como funciona', 'Comunidade']
  return <div className={`site-shell ${IS_STAGING ? 'site-shell--staging' : ''}`}>
    {IS_STAGING && <div className="staging-banner" role="status"><ShieldAlert/> Ambiente de testes — não utilize informações pessoais reais.</div>}
    <header className="topbar"><Brand compact/><nav className="desktop-nav" aria-label="Navegação principal">{nav.map(item => item === 'Comunidade' ? <a key={item} href="/comunidade" onClick={event => { event.preventDefault(); navigate('/comunidade') }}>{item}</a> : <a key={item} href={`#${item.toLowerCase().replaceAll(' ', '-').normalize('NFD').replace(/[\u0300-\u036f]/g, '')}`}>{item}</a>)}</nav><div className="header-actions">{sessionRestoring && <span className="session-status" role="status">Verificando acesso...</span>}<button className="button button--small" onClick={() => setAuthMode('login')}>Entrar</button><button className="text-button" onClick={() => setAuthMode('signup')}>Criar perfil gratuito</button></div><button className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label="Abrir menu">{menuOpen ? <X/> : <Menu/>}</button>{menuOpen && <div className="mobile-menu">{nav.map(item => item === 'Comunidade' ? <a key={item} href="/comunidade" onClick={event => { event.preventDefault(); setMenuOpen(false); navigate('/comunidade') }}>{item}</a> : <a key={item} onClick={() => setMenuOpen(false)} href={`#${item.toLowerCase().replaceAll(' ', '-').normalize('NFD').replace(/[\u0300-\u036f]/g, '')}`}>{item}</a>)}<button onClick={() => { setMenuOpen(false); setAuthMode('login') }}>Entrar</button><button onClick={() => { setMenuOpen(false); setAuthMode('signup') }}>Criar perfil gratuito</button></div>}</header>
    <main>
      <section className="hero" id="inicio"><div className="hero-art" aria-hidden="true"><img src="/operador-zero-identity.jpeg" alt=""/></div><div className="hero-shade"/><div className="hero-content"><p className="eyebrow reveal reveal--1"><span/> A plataforma do airsoft brasileiro</p><h1 className="reveal reveal--2">O airsoft brasileiro<br/><em>em um só lugar.</em></h1><p className="hero-copy reveal reveal--3">Encontre eventos, equipes, campos e operadores de todo o Brasil. Crie seu perfil e participe da comunidade.</p><div className="hero-actions reveal reveal--4"><button className="button" onClick={() => setAuthMode('login')}>Entrar <ArrowRight size={18}/></button><button className="text-button link-button" onClick={() => setAuthMode('signup')}>Criar perfil gratuito</button></div></div><div className="hero-index"><span>01</span><i/><small>AIRSOFT BRASIL</small></div></section>
      <section className="operations section" id="operacoes"><div className="section-heading"><div><p className="eyebrow"><span/> Agenda pública</p><h2>Operações publicadas</h2></div></div><div className="public-empty-state"><CalendarDays/><h3>Nenhuma operação publicada ainda</h3><p>As próximas operações da comunidade aparecerão aqui.</p></div></section>
      <section className="manifesto" id="como-funciona"><div className="manifesto-copy"><p className="eyebrow"><span/> Sua jornada</p><h2>Jogue. Registre.<br/><em>Evolua.</em></h2><p>Crie sua identidade, encontre a comunidade da sua região e construa seu histórico no airsoft.</p></div><div className="steps"><div><UserRound/><span>01</span><div className="step-content"><h3>Cadastre-se</h3><p>Entre com Google ou use seu e-mail e senha.</p></div></div><div><CalendarDays/><span>02</span><div className="step-content"><h3>Participe</h3><p>Encontre equipes e operações perto de você.</p></div></div><div><Trophy/><span>03</span><div className="step-content"><h3>Evolua</h3><p>Acompanhe sua trajetória e suas conquistas.</p></div></div></div></section>
      <section className="community community--link" id="comunidade" role="link" tabIndex={0} onClick={() => navigate('/comunidade')} onKeyDown={event => (event.key === 'Enter' || event.key === ' ') && navigate('/comunidade')}><div><Users/><p className="eyebrow">Comunidade Operador Zero</p><h2>Conecte-se com quem vive o esporte.</h2><p>Compartilhe experiências, encontre equipes e acompanhe o airsoft da sua região.</p><span className="button">Abrir comunidade <ArrowRight size={18}/></span></div></section>
      <section className="trust-section" id="sobre"><div><p className="eyebrow"><span/> Plataforma gratuita</p><h2>Organize sua jornada no airsoft.</h2><p>Crie seu perfil, encontre operações, forme equipes e participe da comunidade sem cobrança pela plataforma.</p></div><div className="trust-grid"><article id="termos"><h3>Termos de Uso</h3><p>Ao criar uma conta, você concorda em usar a plataforma com respeito, legalidade e informações verdadeiras.</p></article><article id="privacidade"><h3>Privacidade</h3><p>Você controla as informações que compartilha com outros operadores e equipes.</p></article><article id="regras"><h3>Regras da Comunidade</h3><p>Fair play, convivência respeitosa, segurança e procedência legal dos equipamentos são obrigatórios.</p></article><article id="seguranca"><h3>Segurança e denúncias</h3><p>Suspeitas de abuso ou falha podem ser comunicadas pelo canal oficial de atendimento.</p><a href="mailto:operadorzerosac@gmail.com">operadorzerosac@gmail.com</a></article></div></section>
      <section className="cta" id="convite"><p className="eyebrow"><span/> Sua identidade</p><h2>Comece pelo<br/>seu cadastro.</h2><p>Entre com Google ou crie uma conta com e-mail e senha.</p><button className="button" onClick={() => setAuthMode('signup')}>Criar minha conta <ArrowRight size={18}/></button></section>
    </main>
    <footer><Brand compact/><p>Airsoft é esporte. Respeito, segurança e fair play sempre.</p><div><a href="#sobre">Sobre</a><a href="#operacoes">Eventos</a><a href="/comunidade" onClick={event => { event.preventDefault(); navigate('/comunidade') }}>Comunidade</a><a href="#termos">Termos</a><a href="#privacidade">Privacidade</a><a href="#seguranca">Segurança</a><a href="mailto:operadorzerosac@gmail.com">Contato</a><button onClick={() => setAuthMode('login')}>Entrar</button><button onClick={() => setAuthMode('signup')}>Criar conta</button></div><small>© 2026 OPERADOR ZERO</small></footer>
    {authModal}
  </div>
}

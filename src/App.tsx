import { FormEvent, useEffect, useState } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  ChevronDown,
  Eye,
  EyeOff,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Mail,
  Medal,
  Menu,
  MessageCircle,
  Search,
  ShieldAlert,
  ShieldCheck,
  Star,
  Tag,
  Target,
  Trophy,
  UserRound,
  Users,
  X,
} from 'lucide-react'
import {
  getCurrentSession,
  login,
  logout,
  prepareGoogleLogin,
  register,
  requestPasswordRecovery,
  resetPassword,
  SessionUser,
  verifyEmail,
} from './api'

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
  const [pending, setPending] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const isRecovery = mode === 'recovery'
  const isReset = mode === 'reset'
  const title = mode === 'login' ? 'Entre no Operador Zero' : mode === 'signup' ? 'Crie sua conta' : isReset ? 'Defina uma nova senha' : 'Recupere seu acesso'
  const description = mode === 'login' ? 'Acesse sua conta com segurança.' : mode === 'signup' ? 'Crie sua identidade de operador em poucos passos.' : isReset ? 'O link será invalidado após a troca da senha.' : 'Informe seu e-mail para receber as instruções.'

  useEffect(() => { setNotice(initialNotice); setShowPassword(false); setTermsAccepted(false) }, [mode, initialNotice])
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.body.classList.add('modal-open')
    window.addEventListener('keydown', onKeyDown)
    return () => { document.body.classList.remove('modal-open'); window.removeEventListener('keydown', onKeyDown) }
  }, [onClose])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!AUTH_ENABLED) { setNotice('Autenticação indisponível neste ambiente.'); return }
    const data = new FormData(event.currentTarget)
    const email = String(data.get('email') ?? '').trim().toLowerCase()
    const password = String(data.get('password') ?? '')
    setPending(true)
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
        setNotice('Cadastro recebido. Verifique seu e-mail para continuar.')
      } else {
        onAuthenticated(await login(email, password))
      }
    } catch (error) {
      setNotice(isRecovery ? 'Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.' : error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.')
    } finally {
      setPending(false)
    }
  }

  const continueWithGoogle = async () => {
    if (mode === 'signup' && !termsAccepted) { setNotice('Aceite os Termos de Uso e a Política de Privacidade para criar a conta.'); return }
    setPending(true)
    try {
      window.location.assign(await prepareGoogleLogin(mode === 'signup' && termsAccepted))
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Login com Google indisponível.')
      setPending(false)
    }
  }

  return <div className="auth-overlay" role="presentation" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      <div className="auth-visual" aria-hidden="true"><Brand/><p>Jogue. Registre.<br/>Ranqueie. Evolua.</p><span>ACESSO SEGURO · OPERADOR ZERO</span></div>
      <div className="auth-content">
        <button className="auth-close" onClick={onClose} aria-label="Fechar"><X/></button>
        {(isRecovery || isReset) && <button className="auth-back" onClick={() => onModeChange('login')}><ArrowLeft size={16}/> Voltar para entrar</button>}
        <div className="auth-heading"><p className="eyebrow"><span/>{isRecovery || isReset ? 'Recuperação por e-mail' : 'Acesso à plataforma'}</p><h2 id="auth-title">{title}</h2><p>{description}</p></div>
        {!AUTH_ENABLED && <p className="auth-unavailable"><ShieldAlert size={17}/> Autenticação remota temporariamente indisponível.</p>}
        {!isRecovery && !isReset && <><button className="google-button" type="button" disabled={pending || !AUTH_ENABLED} onClick={continueWithGoogle}><GoogleMark/> Continuar com Google</button><div className="auth-divider"><span/> ou use seu e-mail <span/></div></>}
        <form className="auth-form" onSubmit={submit}>
          {mode === 'signup' && <label><span>Nome de exibição</span><div><Users/><input name="displayName" autoComplete="name" maxLength={80} required placeholder="Como devemos chamar você?"/></div></label>}
          {!isReset && <label><span>E-mail</span><div><Mail/><input name="email" type="email" autoComplete="email" maxLength={254} required placeholder="voce@exemplo.com.br"/></div></label>}
          {!isRecovery && <label><span>{isReset ? 'Nova senha' : 'Senha'}</span><div><LockKeyhole/><input name="password" type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={12} maxLength={128} required placeholder={mode === 'signup' || isReset ? '12+ caracteres, maiúscula, minúscula e número' : 'Sua senha'}/><button type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}>{showPassword ? <EyeOff/> : <Eye/>}</button></div></label>}
          {mode === 'login' && <button className="forgot-link" type="button" onClick={() => onModeChange('recovery')}>Esqueci minha senha</button>}
          {mode === 'signup' && <label className="terms-check"><input type="checkbox" required checked={termsAccepted} onChange={event => setTermsAccepted(event.target.checked)}/><span>Li e aceito os Termos de Uso e a Política de Privacidade.</span></label>}
          <button className="button auth-submit" type="submit" disabled={pending || !AUTH_ENABLED}>{pending ? 'Aguarde...' : isRecovery ? 'Enviar instruções' : isReset ? 'Salvar nova senha' : mode === 'login' ? 'Entrar com e-mail' : 'Criar conta com e-mail'} {!pending && <ArrowRight size={17}/>}</button>
        </form>
        {notice && <p className="auth-notice" role="status">{notice}</p>}
        {!isRecovery && !isReset && <p className="auth-switch">{mode === 'login' ? 'Ainda não tem conta?' : 'Já possui uma conta?'} <button onClick={() => onModeChange(mode === 'login' ? 'signup' : 'login')}>{mode === 'login' ? 'Criar conta' : 'Entrar'}</button></p>}
        <p className="auth-security"><ShieldCheck size={16}/> Credenciais são enviadas somente à API e não ficam persistidas no navegador.</p>
      </div>
    </section>
  </div>
}

const dashboardNav = [
  { label: 'Visão geral', icon: LayoutDashboard },
  { label: 'Operações', icon: Target },
  { label: 'Minha equipe', icon: Users },
  { label: 'Rankings', icon: Trophy },
  { label: 'Classificados', icon: Tag },
  { label: 'Comunidade', icon: MessageCircle },
  { label: 'Destaques', icon: Star },
  { label: 'Conquistas', icon: Medal },
  { label: 'Meu Operador', icon: UserRound },
]

const emptyCopy: Record<string, { title: string; description: string }> = {
  'Operações': { title: 'Nenhuma operação publicada', description: 'Operações aparecerão aqui somente depois de cadastradas e persistidas por organizadores reais.' },
  'Minha equipe': { title: 'Você ainda não participa de uma equipe', description: 'Equipes e convites serão exibidos quando existirem registros reais vinculados à sua conta.' },
  'Rankings': { title: 'Ranking ainda sem resultados', description: 'A classificação começará após operações e resultados reais serem confirmados.' },
  'Classificados': { title: 'Nenhum classificado publicado', description: 'A área permanecerá vazia até que anúncios reais possam ser armazenados com segurança.' },
  'Comunidade': { title: 'Nenhuma publicação na comunidade', description: 'Publicações aparecerão somente quando forem criadas por contas reais e persistidas pela API.' },
  'Destaques': { title: 'Nenhum destaque ativo', description: 'A plataforma não cria destaques fictícios. Apenas conteúdo real e auditado será exibido.' },
  'Conquistas': { title: 'Nenhuma conquista registrada', description: 'Conquistas serão calculadas a partir de atividades reais confirmadas.' },
}

function EmptyWorkspace({ area, onHome }: { area: string; onHome: () => void }) {
  const copy = emptyCopy[area] ?? { title: 'Nenhum conteúdo disponível', description: 'Esta área aguarda dados reais da plataforma.' }
  return <main className="real-empty-page"><p className="eyebrow"><span/> CONTEÚDO REAL</p><h1>{area}</h1><section className="real-empty-state"><Search/><h2>{copy.title}</h2><p>{copy.description}</p><small>Nenhum dado demonstrativo ou gerado automaticamente é exibido.</small><button onClick={onHome}>Voltar à visão geral <ArrowRight/></button></section></main>
}

function OperatorAccount({ user }: { user: SessionUser }) {
  return <main className="real-empty-page"><p className="eyebrow"><span/> IDENTIDADE AUTENTICADA</p><h1>Meu Operador</h1><section className="real-account"><span className="operator-avatar">{(user.callsign || user.displayName).charAt(0).toUpperCase()}</span><div><small>CONTA REAL</small><h2>{user.callsign || user.displayName}</h2><p>{user.displayName} · @{user.username}</p><p>{user.email}</p></div></section><p className="real-data-note"><ShieldCheck/> Esta tela usa somente informações retornadas pela sessão autenticada. Pontuação, equipe, reputação e histórico não são inventados.</p></main>
}

function Dashboard({ onLogout, user }: { onLogout: () => void; user: SessionUser }) {
  const [profileOpen, setProfileOpen] = useState(false)
  const [activeView, setActiveView] = useState('Visão geral')
  const callsign = user.callsign || user.displayName
  const initial = callsign.charAt(0).toUpperCase()
  return <div className="app-shell">
    <aside className="app-sidebar"><Brand compact/><nav aria-label="Navegação do operador">{dashboardNav.map(({ label, icon: Icon }) => <button onClick={() => setActiveView(label)} className={activeView === label ? 'active' : ''} key={label}><Icon/><span>{label}</span>{activeView === label && <i/>}</button>)}</nav><div className="sidebar-footer"><p>Operador Zero <b>produção</b><br/>Conteúdo verificado</p></div></aside>
    <div className="app-main">
      <header className="app-topbar"><div className="real-search-status"><ShieldCheck/><span>Exibindo somente dados reais</span></div><div className="profile-control"><button onClick={() => setProfileOpen(!profileOpen)} aria-expanded={profileOpen}><span className="profile-avatar">{initial}</span><span><b>{callsign}</b><small>@{user.username}</small></span><ChevronDown/></button>{profileOpen && <div className="profile-menu"><p>{user.email}</p><button onClick={onLogout}><LogOut/> Sair da conta</button></div>}</div></header>
      {activeView === 'Meu Operador' ? <OperatorAccount user={user}/> : activeView !== 'Visão geral' ? <EmptyWorkspace area={activeView} onHome={() => setActiveView('Visão geral')}/> : <main className="dashboard real-dashboard">
        <section className="dashboard-welcome"><div><p>CONTA AUTENTICADA</p><h1>Olá, <em>{callsign}.</em></h1><span>Este painel não utiliza dados fictícios.</span></div></section>
        <section className="operator-strip real-operator-strip"><div className="operator-identity"><span className="operator-avatar">{initial}</span><div><small>IDENTIDADE DA SESSÃO</small><h2>{callsign}</h2><p>{user.displayName} · @{user.username}</p></div></div><div><small>CONTA</small><strong>ATIVA</strong><span>Autenticada pela API</span></div></section>
        <section className="real-dashboard-intro"><div><p className="eyebrow"><span/> ATIVIDADE</p><h2>Sua atividade começa vazia.</h2><p>Operações, equipes, rankings, classificados, comunidade e conquistas serão mostrados apenas quando houver dados reais persistidos e vinculados à sua conta.</p></div><ShieldCheck/></section>
        <div className="real-empty-grid">{['Operações','Equipe','Ranking','Comunidade'].map(area => <button key={area} onClick={() => setActiveView(area === 'Equipe' ? 'Minha equipe' : area === 'Ranking' ? 'Rankings' : area)}><span>{area}</span><strong>Nenhum registro</strong><ArrowRight/></button>)}</div>
      </main>}
    </div>
    <nav className="mobile-app-nav" aria-label="Navegação mobile">{dashboardNav.filter(item => ['Visão geral','Operações','Rankings','Comunidade','Meu Operador'].includes(item.label)).map(({label,icon:Icon}) => <button onClick={() => setActiveView(label)} className={activeView === label ? 'active' : ''} key={label}><Icon/><span>{label}</span></button>)}</nav>
  </div>
}

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode | null>(null)
  const [currentUser, setCurrentUser] = useState<SessionUser | null>(null)
  const [authChecking, setAuthChecking] = useState(AUTH_ENABLED)
  const [authNotice, setAuthNotice] = useState('')
  const [resetTokenValue, setResetTokenValue] = useState('')

  useEffect(() => {
    const close = () => setMenuOpen(false)
    window.addEventListener('resize', close)
    return () => window.removeEventListener('resize', close)
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
        if (active) setCurrentUser(session)
      } catch (error) {
        if (active) { setAuthNotice(error instanceof Error ? error.message : 'Não foi possível validar o acesso.'); setAuthMode('login') }
      } finally {
        if (active) setAuthChecking(false)
      }
    }
    void restore()
    return () => { active = false }
  }, [])

  if (authChecking) return <div className="auth-boot"><Brand/><span>Validando sessão segura...</span><button onClick={() => setAuthChecking(false)}>Continuar no site</button></div>
  if (currentUser) return <Dashboard user={currentUser} onLogout={() => { void logout().finally(() => setCurrentUser(null)) }}/>

  const authenticated = (user: SessionUser) => { setAuthMode(null); setAuthNotice(''); setCurrentUser(user) }
  const nav = ['Operações', 'Como funciona', 'Comunidade']
  return <div className={`site-shell ${IS_STAGING ? 'site-shell--staging' : ''}`}>
    {IS_STAGING && <div className="staging-banner" role="status"><ShieldAlert/> Ambiente de testes — não utilize informações pessoais reais.</div>}
    <header className="topbar"><Brand compact/><nav className="desktop-nav" aria-label="Navegação principal">{nav.map(item => <a key={item} href={`#${item.toLowerCase().replaceAll(' ', '-').normalize('NFD').replace(/[\u0300-\u036f]/g, '')}`}>{item}</a>)}</nav><div className="header-actions"><button className="text-button" onClick={() => setAuthMode('login')}>Entrar</button><button className="button button--small" onClick={() => setAuthMode('signup')}>Criar conta</button></div><button className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label="Abrir menu">{menuOpen ? <X/> : <Menu/>}</button>{menuOpen && <div className="mobile-menu">{nav.map(item => <a key={item} onClick={() => setMenuOpen(false)} href={`#${item.toLowerCase().replaceAll(' ', '-').normalize('NFD').replace(/[\u0300-\u036f]/g, '')}`}>{item}</a>)}<button onClick={() => { setMenuOpen(false); setAuthMode('signup') }}>Criar conta</button><button onClick={() => { setMenuOpen(false); setAuthMode('login') }}>Entrar</button></div>}</header>
    <main>
      <section className="hero" id="inicio"><div className="hero-art" aria-hidden="true"><img src="/operador-zero-identity.jpeg" alt=""/></div><div className="hero-shade"/><div className="hero-content"><p className="eyebrow reveal reveal--1"><span/> A plataforma do airsoft brasileiro</p><h1 className="reveal reveal--2">Sua próxima<br/><em>operação</em> começa aqui.</h1><p className="hero-copy reveal reveal--3">Uma plataforma construída para receber conteúdo verdadeiro de operadores, equipes e organizadores.</p><div className="hero-actions reveal reveal--4"><button className="button" onClick={() => setAuthMode('signup')}>Criar minha conta <ArrowRight size={18}/></button><a className="link-button" href="#como-funciona">Como funciona <ChevronDown size={17}/></a></div></div><div className="hero-index"><span>01</span><i/><small>CONTEÚDO REAL</small></div></section>
      <section className="operations section" id="operacoes"><div className="section-heading"><div><p className="eyebrow"><span/> Agenda pública</p><h2>Operações publicadas</h2></div></div><div className="public-empty-state"><CalendarDays/><h3>Nenhuma operação publicada ainda</h3><p>Esta área exibirá somente operações cadastradas por organizadores reais. Não usamos eventos de exemplo para preencher a agenda.</p></div></section>
      <section className="manifesto" id="como-funciona"><div className="manifesto-copy"><p className="eyebrow"><span/> Histórico confiável</p><h2>Dados reais.<br/><em>Sem simulação.</em></h2><p>A plataforma começa pela identidade autenticada. Cada novo módulo será liberado com persistência, autorização e auditoria no backend.</p></div><div className="steps"><div><UserRound/><span>01</span><div className="step-content"><h3>Cadastre-se</h3><p>Crie sua conta real com Google ou e-mail e senha.</p></div></div><div><ShieldCheck/><span>02</span><div className="step-content"><h3>Publique</h3><p>Conteúdo aparecerá somente após ser armazenado pela plataforma.</p></div></div><div><Trophy/><span>03</span><div className="step-content"><h3>Construa</h3><p>Histórico e ranking dependerão de atividades confirmadas.</p></div></div></div></section>
      <section className="community" id="comunidade"><div><Users/><p className="eyebrow">Comunidade em formação</p><h2>Primeiro as pessoas. Depois os números.</h2><p>Nomes, equipes, eventos, anúncios e estatísticas só serão exibidos quando forem criados por usuários reais.</p></div></section>
      <section className="cta" id="convite"><p className="eyebrow"><span/> Sua identidade</p><h2>Comece pelo<br/>seu cadastro.</h2><p>Entre com Google ou crie uma conta com e-mail e senha.</p><button className="button" onClick={() => setAuthMode('signup')}>Criar minha conta <ArrowRight size={18}/></button></section>
    </main>
    <footer><Brand compact/><p>Airsoft é esporte. Respeito, segurança e fair play sempre.</p><div><a href="#como-funciona">Funcionamento</a><a href="#comunidade">Comunidade</a><a href="mailto:operadorzerosac@gmail.com">Contato</a></div><small>© 2026 OPERADOR ZERO</small></footer>
    {authMode && <AuthModal mode={authMode} onClose={() => { setAuthMode(null); setAuthNotice('') }} onModeChange={setAuthMode} onAuthenticated={authenticated} resetToken={resetTokenValue} initialNotice={authNotice}/>}
  </div>
}

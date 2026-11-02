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
  Film,
  LayoutDashboard,
  MapPin,
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
  ApiClientError,
  getCurrentSession,
  getPublicAgenda,
  getUnreadNotifications,
  type PublicOperation,
  markAuthenticatedAreaUsable,
  prepareAuthentication,
  getOperations,
  getOperatorProfile,
  getOperatorRanking,
  getTeamSummary,
  login,
  startGoogle,
  unlinkGoogle,
  setupMfa,
  enableMfa,
  verifyMfa,
  disableMfa,
  logout,
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
import { AchievementsPage, NotificationsPage } from './NotificationsPage'
import { FeedPage } from './feed/FeedPage'
import { CreateFlow } from './feed/CreateFlow'
import { UploadStatus } from './feed/UploadStatus'
import { DossierStrip } from './feed/DossierStrip'
import { FeedModerationPanel } from './feed/FeedModerationPanel'
import { PublicPostPage } from './feed/PublicPostPage'
import { BottomNav, CreateSheet, HubPage, type HubId } from './feed/AppNavigation'
import { OperationsPage } from './OperationsPage'
import { VenuesPage } from './VenuesPage'
import { RankingsPage } from './RankingsPage'
import { PerformancePage } from './PerformancePage'
import CommunityPage from './CommunityPage'
import { OperatorsSocialPage } from './OperatorsSocialPage'

function Brand({ compact = false }: { compact?: boolean }) {
  return <a className={`brand ${compact ? 'brand--compact' : ''}`} href="#inicio" aria-label="Operador Zero, início"><span>Operador</span><strong>Zero</strong></a>
}

function GoogleMark() {
  return <svg aria-hidden="true" viewBox="0 0 24 24"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.91h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.4Z"/><path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.42l-3.24-2.5c-.9.6-2.05.96-3.38.96-2.6 0-4.81-1.76-5.6-4.13H3.05v2.6A10 10 0 0 0 12 22Z"/><path fill="#FBBC05" d="M6.4 13.91a6 6 0 0 1 0-3.82v-2.6H3.05a10 10 0 0 0 0 9.02l3.35-2.6Z"/><path fill="#EA4335" d="M12 5.96c1.47 0 2.79.5 3.83 1.5L18.7 4.6A9.64 9.64 0 0 0 12 2a10 10 0 0 0-8.95 5.49l3.35 2.6c.79-2.37 3-4.13 5.6-4.13Z"/></svg>
}

// Messages for the /?auth=google-STATUS&code=CODE redirect sent by the API after Google.
const GOOGLE_NOTICES: Record<string, string> = {
  'google-link-required': 'Este e-mail já tem uma conta no Operador Zero. Entre com sua senha para vincular o Google com segurança.',
  'google-signup-required': 'Esta conta Google ainda não está cadastrada. Aceite os Termos e clique em Continuar com Google para criar sua conta.',
  GOOGLE_CANCELLED: 'O acesso com Google foi cancelado. Você pode tentar novamente.',
  GOOGLE_FLOW_EXPIRED: 'O acesso com Google expirou. Tente novamente.',
  GOOGLE_EMAIL_NOT_VERIFIED: 'O Google não confirmou este e-mail. Use outra conta Google ou entre com e-mail e senha.',
  GOOGLE_ACCOUNT_UNAVAILABLE: 'Esta conta não está disponível. Fale com o suporte.',
  GOOGLE_ALREADY_LINKED: 'Esta conta Google já está vinculada a outro operador.',
  GOOGLE_ACCOUNT_HAS_LINK: 'Sua conta já está vinculada a outra conta Google. Desvincule antes de trocar.',
  GOOGLE_LINK_SESSION_EXPIRED: 'Sua sessão terminou antes de concluir o vínculo. Entre novamente e tente outra vez.',
}

type AuthMode = 'login' | 'signup' | 'recovery' | 'reset'
const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED === 'true'
const IS_STAGING = import.meta.env.PROD && import.meta.env.VITE_APP_ENV !== 'production'

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
  const [authPreparing, setAuthPreparing] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [verificationEmail, setVerificationEmail] = useState('')
  const isRecovery = mode === 'recovery'
  const isReset = mode === 'reset'
  const title = mode === 'login' ? 'Entre no Operador Zero' : mode === 'signup' ? 'Crie sua conta' : isReset ? 'Defina uma nova senha' : 'Recupere seu acesso'
  const description = mode === 'login' ? 'Acesse sua conta com segurança.' : mode === 'signup' ? 'Crie sua identidade de operador em poucos passos.' : isReset ? 'O link será invalidado após a troca da senha.' : 'Informe seu e-mail para receber as instruções.'

  useEffect(() => { setNotice(initialNotice); setShowPassword(false); setTermsAccepted(false); setVerificationEmail('') }, [mode, initialNotice])
  useEffect(() => {
    if (!AUTH_ENABLED) return
    // Wake the API and obtain CSRF while the user is reading/filling the form.
    // ensureCsrf deduplicates this with a submit that happens immediately.
    let active = true
    const delayedStatus = window.setTimeout(() => active && setAuthPreparing(true), 500)
    void prepareAuthentication()
      .catch(() => undefined)
      .finally(() => { window.clearTimeout(delayedStatus); if (active) setAuthPreparing(false) })
    return () => { active = false; window.clearTimeout(delayedStatus) }
  }, [mode])
  useEffect(() => {
    // Returning with the browser Back button restores this page from the cache with the button still busy.
    const resumeAfterGoogle = (event: PageTransitionEvent) => {
      if (!event.persisted) return
      setPendingAction(null)
      setNotice('O acesso com Google foi interrompido. Você pode tentar novamente.')
    }
    window.addEventListener('pageshow', resumeAfterGoogle)
    return () => window.removeEventListener('pageshow', resumeAfterGoogle)
  }, [])
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
    const passwordConfirmation = String(data.get('passwordConfirmation') ?? '')
    if ((mode === 'signup' || isReset) && password !== passwordConfirmation) {
      setNotice('As senhas devem ser iguais.')
      return
    }
    setPendingAction('form')
    setNotice('')
    try {
      if (isRecovery) {
        await requestPasswordRecovery(email)
        setNotice('Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.')
      } else if (isReset) {
        if (!resetToken) throw new Error('Link de recuperação inválido.')
        await resetPassword(resetToken, password, passwordConfirmation)
        setNotice('Senha alterada. Volte para entrar com a nova senha.')
      } else if (mode === 'signup') {
        await register(String(data.get('displayName') ?? '').trim(), email, password, passwordConfirmation, termsAccepted)
        setVerificationEmail(email)
        setNotice('Se este e-mail puder ser usado, enviamos um link de confirmação. Confira também o Lixo Eletrônico.')
      } else {
        onAuthenticated(await login(email, password))
      }
    } catch (error) {
      if (error instanceof ApiClientError && error.code === 'EMAIL_NOT_VERIFIED') setVerificationEmail(email)
      setNotice(isRecovery ? 'Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.' : error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.')
    } finally {
      setPendingAction(null)
    }
  }

  const continueWithGoogle = async () => {
    if (!AUTH_ENABLED) { setNotice('Autenticação indisponível neste ambiente.'); return }
    if (mode === 'signup' && !termsAccepted) { setNotice('Aceite os Termos de Uso e a Política de Privacidade para criar a conta.'); return }
    setPendingAction('google')
    setNotice('')
    try {
      window.location.assign(await startGoogle(mode === 'signup' ? 'register' : 'login', mode === 'signup' && termsAccepted))
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Entrar com Google está indisponível no momento.')
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

  return <div className="auth-overlay" role="presentation" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      <div className="auth-visual" aria-hidden="true"><Brand/><p>Jogue. Registre.<br/>Ranqueie. Evolua.</p><span>OPERADOR ZERO</span></div>
      <div className="auth-content">
        <button className="auth-close" onClick={onClose} aria-label="Fechar"><X/></button>
        {(isRecovery || isReset) && <button className="auth-back" onClick={() => onModeChange('login')}><ArrowLeft size={16}/> Voltar para entrar</button>}
        <div className="auth-heading"><p className="eyebrow"><span/>{isRecovery || isReset ? 'Recuperação por e-mail' : 'Acesso à plataforma'}</p><h2 id="auth-title">{title}</h2><p>{description}</p></div>
        {!AUTH_ENABLED && <p className="auth-unavailable"><ShieldAlert size={17}/> Autenticação remota temporariamente indisponível.</p>}
        {!isRecovery && !isReset && <><button className="google-button" type="button" disabled={pendingAction !== null || !AUTH_ENABLED} aria-busy={pendingAction === 'google'} onClick={continueWithGoogle}><GoogleMark/> {pendingAction === 'google' ? 'Conectando ao Google...' : 'Continuar com Google'}</button><div className="auth-divider"><span/> ou use seu e-mail <span/></div></>}
        <form className="auth-form" onSubmit={submit} onInput={() => notice && setNotice('')}>
          {mode === 'signup' && <label><span>Nome de exibição</span><div><Users/><input name="displayName" autoComplete="name" maxLength={80} required placeholder="Como devemos chamar você?"/></div></label>}
          {!isReset && <label><span>E-mail</span><div><Mail/><input name="email" type="email" autoComplete="email" maxLength={254} required placeholder="voce@exemplo.com.br"/></div></label>}
          {!isRecovery && <label><span>{isReset ? 'Nova senha' : 'Senha'}</span><div><LockKeyhole/><input name="password" type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={12} maxLength={128} required placeholder={mode === 'signup' || isReset ? '12+ caracteres, maiúscula, minúscula e número' : 'Sua senha'}/><button type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}>{showPassword ? <EyeOff/> : <Eye/>}</button></div></label>}
          {(mode === 'signup' || isReset) && <label><span>Confirmar senha</span><div><LockKeyhole/><input name="passwordConfirmation" type={showPassword ? 'text' : 'password'} autoComplete="new-password" minLength={12} maxLength={128} required placeholder="Digite a senha novamente"/></div></label>}
          {mode === 'login' && <button className="forgot-link" type="button" onClick={() => onModeChange('recovery')}>Esqueci minha senha</button>}
          {mode === 'signup' && <label className="terms-check"><input type="checkbox" required checked={termsAccepted} onChange={event => setTermsAccepted(event.target.checked)}/><span>Li e aceito os Termos de Uso e a Política de Privacidade.</span></label>}
          <button className="button auth-submit" type="submit" disabled={pendingAction !== null || !AUTH_ENABLED}>{pendingAction === 'form' ? 'Conectando com segurança...' : isRecovery ? 'Enviar instruções' : isReset ? 'Salvar nova senha' : mode === 'login' ? 'Entrar com e-mail' : 'Criar conta com e-mail'} {pendingAction === null && <ArrowRight size={17}/>}</button>
        </form>
        {authPreparing && pendingAction === null && <p className="auth-notice" role="status">Preparando conexão segura...</p>}
        {notice && <p className="auth-notice" role="status">{notice}</p>}
        {(mode === 'signup' || mode === 'login') && verificationEmail && <button className="forgot-link" type="button" disabled={pendingAction !== null} onClick={resendConfirmation}>Reenviar e-mail de confirmação</button>}
        {!isRecovery && !isReset && <p className="auth-switch">{mode === 'login' ? 'Ainda não tem conta?' : 'Já possui uma conta?'} <button onClick={() => onModeChange(mode === 'login' ? 'signup' : 'login')}>{mode === 'login' ? 'Criar conta' : 'Entrar'}</button></p>}
      </div>
    </section>
  </div>
}

const dashboardNav = [
  { label: 'Feed', icon: Film },
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

function GoogleAccountPanel({ user, onUserUpdated }: { user: SessionUser; onUserUpdated: (user: SessionUser) => void }) {
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const linked = Boolean(user.googleLinked)
  const canUnlink = linked && user.hasPassword !== false
  const link = async () => {
    setPending(true); setMessage('')
    try { window.location.assign(await startGoogle('link')) } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível abrir o Google.'); setPending(false) }
  }
  const unlink = async () => {
    setPending(true); setMessage('')
    try { await unlinkGoogle(); onUserUpdated({ ...user, googleLinked: false }); setMessage('Conta Google desvinculada.') } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível desvincular.') } finally { setPending(false) }
  }
  return <section className="google-account" aria-labelledby="google-account-title">
    <GoogleMark/>
    <div><h2 id="google-account-title">Conta Google</h2><p>{linked ? 'Vinculada. Você pode entrar com o Google ou com e-mail e senha.' : 'Não vinculada. Vincule para entrar com um clique.'}</p>
      {linked && !canUnlink && <p>Para desvincular, defina antes uma senha em Entrar &gt; Esqueci minha senha.</p>}
      {message && <p role="status">{message}</p>}</div>
    {linked ? <button className="text-button" type="button" disabled={pending || !canUnlink} onClick={unlink}>{pending ? 'Aguarde...' : 'Desvincular'}</button>
      : <button className="google-button" type="button" disabled={pending} aria-busy={pending} onClick={link}><GoogleMark/> {pending ? 'Conectando ao Google...' : 'Vincular Google'}</button>}
  </section>
}

function MfaPanel({ user, onUserUpdated }: { user: SessionUser; onUserUpdated: (user: SessionUser) => void }) {
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [code, setCode] = useState('')
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const refresh = async () => { const session = await getCurrentSession(); if (session) onUserUpdated(session) }
  const run = async (action: () => Promise<unknown>, success: string) => {
    setPending(true); setMessage('')
    try { await action(); setCode(''); setMessage(success); await refresh() } catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível concluir.') } finally { setPending(false) }
  }
  const codeField = <label><span>Código de 6 dígitos</span><input inputMode="numeric" autoComplete="one-time-code" pattern="\d{6}" maxLength={6} value={code} onChange={event => setCode(event.target.value.replace(/\D/g, ''))} placeholder="000000"/></label>
  return <section className="google-account mfa-panel" aria-labelledby="mfa-title">
    <ShieldAlert/>
    <div><h2 id="mfa-title">Verificação em duas etapas</h2>
      {user.mfaEnabled ? <p>Ativa. Ferramentas de moderação e administração pedem o código do aplicativo autenticador uma vez por sessão.</p>
        : <p>Proteja sua conta com um código do aplicativo autenticador (Google Authenticator, Microsoft Authenticator, Authy). É obrigatória para moderadores e administradores.</p>}
      {user.mfaRequired && user.mfaEnabled && <form onSubmit={event => { event.preventDefault(); void run(() => verifyMfa(code), 'Código confirmado. Ferramentas administrativas liberadas nesta sessão.') }}>{codeField}<button className="button button--small" disabled={pending || code.length !== 6}>Confirmar código</button></form>}
      {!user.mfaEnabled && !setup && <button className="button button--small" type="button" disabled={pending} onClick={() => { setPending(true); setMessage(''); setupMfa().then(setSetup).catch(error => setMessage(error instanceof Error ? error.message : 'Não foi possível iniciar.')).finally(() => setPending(false)) }}>Ativar</button>}
      {!user.mfaEnabled && setup && <form onSubmit={event => { event.preventDefault(); void run(() => enableMfa(code), 'Verificação em duas etapas ativada.').then(() => setSetup(null)) }}>
        <p>1. No aplicativo autenticador, adicione uma conta com a chave abaixo (ou <a href={setup.otpauthUri}>toque aqui no celular</a>).</p>
        <p className="mfa-secret"><code>{setup.secret.match(/.{1,4}/g)?.join(' ')}</code></p>
        <p>2. Digite o código que o aplicativo mostrar.</p>
        {codeField}<button className="button button--small" disabled={pending || code.length !== 6}>Confirmar e ativar</button>
      </form>}
      {user.mfaEnabled && !user.mfaRequired && <details><summary>Desativar</summary><form onSubmit={event => { event.preventDefault(); if (window.confirm('Desativar a verificação em duas etapas? Ferramentas administrativas ficarão bloqueadas.')) void run(() => disableMfa(code), 'Verificação em duas etapas desativada.') }}>{codeField}<button className="text-button" disabled={pending || code.length !== 6}>Desativar</button></form></details>}
      {message && <p role="status">{message}</p>}
    </div>
  </section>
}

function PublicAgenda({ onJoin }: { onJoin: () => void }) {
  const [items, setItems] = useState<PublicOperation[] | null>(null)
  useEffect(() => {
    let active = true
    getPublicAgenda().then(result => { if (active) setItems(result.items) }).catch(() => { if (active) setItems([]) })
    return () => { active = false }
  }, [])
  const price = (value?: number | null) => value ? value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : 'Gratuita'
  return <section className="operations section" id="operacoes"><div className="section-heading"><div><p className="eyebrow"><span/> Agenda pública</p><h2>Operações publicadas</h2></div></div>
    {items && items.length > 0 ? <div className="public-agenda">{items.map(item => <article key={item.id}>
      <time dateTime={item.operationDate}><b>{new Date(`${item.operationDate}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit' })}</b><span>{new Date(`${item.operationDate}T12:00:00`).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')}</span></time>
      <div><h3>{item.name}</h3><p><MapPin/> {item.fieldName} · {item.city}/{item.stateCode}</p><small>Início {item.startTime.slice(0, 5)} · {item.participantCount}/{item.participantLimit ?? '∞'} confirmados · {price(item.registrationPrice)}</small></div>
      <button type="button" onClick={onJoin}>Participar</button>
    </article>)}</div>
    : <div className="public-empty-state"><CalendarDays/><h3>{items ? 'Nenhuma operação publicada ainda' : 'Carregando agenda…'}</h3><p>As próximas operações da comunidade aparecerão aqui.</p></div>}
  </section>
}

function Dashboard({ path, onNavigate, onLogout, onUserUpdated, onNavigateCommunity, user, googleNotice = '', onDismissGoogleNotice }: { path:string; onNavigate:(path:string)=>void; onLogout: () => Promise<boolean>; onUserUpdated: (user: SessionUser) => void; onNavigateCommunity: () => void; user: SessionUser; googleNotice?: string; onDismissGoogleNotice?: () => void }) {
  const [profileOpen, setProfileOpen] = useState(false)
  const [activeView, setActiveView] = useState(path.startsWith('/operadores') ? 'Operadores' : path === '/feed' ? 'Feed' : 'Visão geral')
  const [createOpen, setCreateOpen] = useState(false)
  const [createNotice, setCreateNotice] = useState('')
  const [composer, setComposer] = useState<'POST' | 'RECON' | null>(null)
  // O feed tem rota propria; as demais telas do painel ficam em /painel ("/" logado leva ao feed).
  const select = (view: string) => {
    if (view === 'Comunidade') return onNavigateCommunity()
    if (view === 'Operadores') return onNavigate('/operadores')
    onNavigate(view === 'Feed' ? '/feed' : '/painel')
    setActiveView(view)
  }
  const [unread, setUnread] = useState(0)
  const refreshUnread = () => { getUnreadNotifications().then(result => setUnread(result.unread)).catch(() => undefined) }
  useEffect(() => { refreshUnread(); const timer = window.setInterval(refreshUnread, 60000); return () => window.clearInterval(timer) }, [])
  const [logoutPending, setLogoutPending] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [overview, setOverview] = useState<{ operations: Operation[]; teamTotal: number; ranking: RankingEntry[] }>({ operations: [], teamTotal: 0, ranking: [] })
  const [overviewLoading, setOverviewLoading] = useState(true)
  const [overviewError, setOverviewError] = useState('')
  const callsign = user.callsign || user.displayName
  const initial = callsign.charAt(0).toUpperCase()
  useEffect(() => { if (path.startsWith('/operadores')) setActiveView('Operadores'); else if (path === '/feed') setActiveView('Feed') }, [path])
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

  useEffect(() => {
    requestAnimationFrame(() => requestAnimationFrame(markAuthenticatedAreaUsable))
  }, [])
  const operationDate = (value: string) => new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(new Date(`${value}T12:00:00`))
  return <div className="app-shell">
    <aside className="app-sidebar"><Brand compact/><nav aria-label="Navegação do operador">{dashboardNav.map(({ label, icon: Icon }) => <button onClick={() => select(label)} className={activeView === label ? 'active' : ''} key={label}><Icon/><span>{label}</span>{label === 'Notificações' && unread > 0 && <em className="nav-badge" aria-label={`${unread} não lidas`}>{unread > 99 ? '99+' : unread}</em>}{activeView === label && <i/>}</button>)}</nav><div className="sidebar-footer"><p>Jogue. Registre.<br/><b>Evolua.</b></p></div></aside>
    <div className="app-main">
      {activeView === 'Feed' ? <FeedPage unread={unread} onOpenNotifications={() => select('Notificações')} onFindOperators={() => select('Operadores')} onCreateRecon={() => setComposer('RECON')}/> : <>
      <header className="app-topbar"><OperatorSearch/><div className="profile-control"><button onClick={() => setProfileOpen(!profileOpen)} aria-expanded={profileOpen}><span className="profile-avatar">{initial}</span><span><b>{callsign}</b><small>@{user.username}</small></span><ChevronDown/></button>{profileOpen && <div className="profile-menu"><p>{user.email}</p><button disabled={logoutPending} onClick={async () => { setLogoutPending(true); setLogoutError(''); const completed = await onLogout(); setLogoutPending(false); if (!completed) setLogoutError('Não foi possível encerrar a sessão. Verifique sua conexão e tente novamente.') }}><LogOut/> {logoutPending ? 'Encerrando...' : 'Sair da conta'}</button>{logoutError && <small className="logout-error" role="alert">{logoutError}</small>}</div>}</div></header>
      {user.mfaRequired && activeView !== 'Meu Operador' && <p className="auth-notice google-notice" role="status">{user.mfaEnabled ? 'Confirme o código da verificação em duas etapas para usar as ferramentas de moderação.' : 'Ative a verificação em duas etapas para usar as ferramentas de moderação.'} <button type="button" onClick={() => setActiveView('Meu Operador')}>Abrir Meu Operador</button></p>}
      {googleNotice && <p className="auth-notice google-notice" role="status">{googleNotice} <button type="button" onClick={onDismissGoogleNotice}>Fechar</button></p>}
      {activeView === 'Operadores' ? <OperatorsSocialPage path={path.startsWith('/operadores')?path:'/operadores'} currentUsername={user.username} onNavigate={onNavigate} onEditProfile={()=>setActiveView('Meu Operador')}/> : activeView === 'Meu Operador' ? <><OperatorPage onUserUpdated={updated => onUserUpdated({ ...user, ...updated })}/><GoogleAccountPanel user={user} onUserUpdated={onUserUpdated}/><MfaPanel user={user} onUserUpdated={onUserUpdated}/></> : activeView === 'Minha equipe' ? <TeamPage/> : activeView === 'Operações' ? <OperationsPage/> : activeView === 'Campos' ? <VenuesPage mode="fields"/> : activeView === 'Mapas' ? <VenuesPage mode="maps"/> : activeView === 'Desempenho' ? <PerformancePage/> : activeView === 'Rankings' ? <RankingsPage/> : activeView === 'Explorar' || activeView === 'Jogos' || activeView === 'Perfil' ? <HubPage hub={activeView as HubId} onSelect={select} extra={activeView === 'Perfil' ? <><DossierStrip username={user.username} own/>{user.roles.some(role => role === 'ADMIN' || role === 'MODERATOR') && <FeedModerationPanel/>}</> : undefined}/> : activeView === 'Notificações' ? <NotificationsPage onOpen={view => setActiveView(view)} onChanged={refreshUnread}/> : activeView === 'Conquistas' ? <AchievementsPage/> : activeView !== 'Visão geral' ? <EmptyWorkspace area={activeView} onHome={() => setActiveView('Visão geral')}/> : <main className="dashboard real-dashboard">
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
      </>}
    </div>
    <BottomNav activeView={activeView} onSelect={select} onCreate={() => { setCreateNotice(''); setCreateOpen(true) }}/>
    {createOpen && <CreateSheet notice={createNotice} onClose={() => setCreateOpen(false)} onPick={kind => { setCreateOpen(false); setComposer(kind) }}/>}
    {composer && <CreateFlow key={composer} mode={composer} onClose={() => setComposer(null)}/>}
    <UploadStatus/>
  </div>
}

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode | null>(null)
  const [currentUser, setCurrentUser] = useState<SessionUser | null>(null)
  const [sessionRestoring, setSessionRestoring] = useState(AUTH_ENABLED)
  const [authNotice, setAuthNotice] = useState('')
  const [googleNotice, setGoogleNotice] = useState('')
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
      const google = params.get('auth')
      const googleCode = params.get('code')
      if (action || google) window.history.replaceState({}, document.title, window.location.pathname + window.location.hash)
      try {
        if (action === 'verify-email' && token) {
          await verifyEmail(token)
          if (active) { setAuthNotice('E-mail confirmado. Entre com sua senha para continuar.'); setAuthMode('login') }
        } else if (action === 'reset-password' && token && active) {
          setResetTokenValue(token); setAuthMode('reset')
        }
        const session = await getCurrentSession()
        if (active) {
          setCurrentUser(session)
          if (google === 'google-success' && !session) {
            setAuthNotice('O Google confirmou o acesso, mas a sessão não foi aberta. Verifique se o navegador aceita cookies e tente novamente.')
            setAuthMode('login')
          } else if (google === 'google-linked' && session) {
            setGoogleNotice('Conta Google vinculada. Agora você também pode entrar com o Google.')
          } else if (google === 'google-link-required' || google === 'google-signup-required') {
            setAuthNotice(GOOGLE_NOTICES[google]); setAuthMode(google === 'google-signup-required' ? 'signup' : 'login')
          } else if (google === 'google-error') {
            const message = GOOGLE_NOTICES[googleCode ?? ''] ?? 'Não foi possível concluir o acesso com Google. Tente novamente ou use e-mail e senha.'
            if (session) setGoogleNotice(message)
            else { setAuthNotice(message); setAuthMode('login') }
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

  // Logado, "/" abre o feed; deslogado, "/feed" pede login e volta ao feed depois.
  useEffect(() => {
    if (sessionRestoring) return
    if (currentUser && currentPath === '/') { window.history.replaceState({}, '', '/feed'); setCurrentPath('/feed') }
    else if (!currentUser && currentPath === '/feed') setAuthMode(mode => mode ?? 'login')
  }, [currentUser, currentPath, sessionRestoring])
  const navigate = (path: string) => {
    if (window.location.pathname !== path) window.history.pushState({}, '', path)
    setCurrentPath(path)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const authenticated = (user: SessionUser) => {
    // A password login right after "link-required" completes the Google link on the server.
    if (user.googleLinked && authNotice === GOOGLE_NOTICES['google-link-required']) setGoogleNotice('Conta Google vinculada. Agora você também pode entrar com o Google.')
    setAuthMode(null); setAuthNotice(''); setCurrentUser(user)
  }
  const authModal = authMode && <AuthModal mode={authMode} onClose={() => { setAuthMode(null); setAuthNotice('') }} onModeChange={setAuthMode} onAuthenticated={authenticated} resetToken={resetTokenValue} initialNotice={authNotice}/>

  // Link compartilhado (/p/{id} redireciona para ca): funciona logado ou nao.
  const sharedPost = currentPath.match(/^\/post\/([0-9a-f-]{36})$/)?.[1]
  if (sharedPost && !sessionRestoring) {
    return <>
      <PublicPostPage postId={sharedPost} loggedIn={Boolean(currentUser)} onLogin={() => setAuthMode('login')} onSignup={() => setAuthMode('signup')} onOpenFeed={() => navigate('/feed')}/>
      {authModal}
    </>
  }

  if (currentPath === '/comunidade' || currentPath.startsWith('/comunidade/')) {
    return <>
      <CommunityPage path={currentPath} user={currentUser} onNavigate={navigate} onRequireAuth={() => setAuthMode('login')}/>
      {authModal}
    </>
  }

  if (currentUser) return <Dashboard path={currentPath} onNavigate={navigate} user={currentUser} onUserUpdated={setCurrentUser} googleNotice={googleNotice} onDismissGoogleNotice={() => setGoogleNotice('')} onNavigateCommunity={() => navigate('/comunidade')} onLogout={async () => {
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
      <PublicAgenda onJoin={() => setAuthMode('signup')}/>
      <section className="manifesto" id="como-funciona"><div className="manifesto-copy"><p className="eyebrow"><span/> Sua jornada</p><h2>Jogue. Registre.<br/><em>Evolua.</em></h2><p>Crie sua identidade, encontre a comunidade da sua região e construa seu histórico no airsoft.</p></div><div className="steps"><div><UserRound/><span>01</span><div className="step-content"><h3>Cadastre-se</h3><p>Crie sua conta com e-mail e senha e confirme seu endereço.</p></div></div><div><CalendarDays/><span>02</span><div className="step-content"><h3>Participe</h3><p>Encontre equipes e operações perto de você.</p></div></div><div><Trophy/><span>03</span><div className="step-content"><h3>Evolua</h3><p>Acompanhe sua trajetória e suas conquistas.</p></div></div></div></section>
      <section className="community community--link" id="comunidade" role="link" tabIndex={0} onClick={() => navigate('/comunidade')} onKeyDown={event => (event.key === 'Enter' || event.key === ' ') && navigate('/comunidade')}><div><Users/><p className="eyebrow">Comunidade Operador Zero</p><h2>Conecte-se com quem vive o esporte.</h2><p>Compartilhe experiências, encontre equipes e acompanhe o airsoft da sua região.</p><span className="button">Abrir comunidade <ArrowRight size={18}/></span></div></section>
      <section className="trust-section" id="sobre"><div><p className="eyebrow"><span/> Plataforma gratuita</p><h2>Organize sua jornada no airsoft.</h2><p>Crie seu perfil, encontre operações, forme equipes e participe da comunidade sem cobrança pela plataforma.</p></div><div className="trust-grid"><article id="termos"><h3>Termos de Uso</h3><p>Ao criar uma conta, você concorda em usar a plataforma com respeito, legalidade e informações verdadeiras.</p></article><article id="privacidade"><h3>Privacidade</h3><p>Você controla as informações que compartilha com outros operadores e equipes.</p></article><article id="regras"><h3>Regras da Comunidade</h3><p>Fair play, convivência respeitosa, segurança e procedência legal dos equipamentos são obrigatórios.</p></article><article id="seguranca"><h3>Segurança e denúncias</h3><p>Suspeitas de abuso ou falha podem ser comunicadas pelo canal oficial de atendimento.</p><a href="mailto:operadorzerosac@gmail.com">operadorzerosac@gmail.com</a></article></div></section>
      <section className="cta" id="convite"><p className="eyebrow"><span/> Sua identidade</p><h2>Comece pelo<br/>seu cadastro.</h2><p>Crie sua conta com e-mail e senha e confirme o cadastro pelo link recebido.</p><button className="button" onClick={() => setAuthMode('signup')}>Criar minha conta <ArrowRight size={18}/></button></section>
    </main>
    <footer><Brand compact/><p>Airsoft é esporte. Respeito, segurança e fair play sempre.</p><div><a href="#sobre">Sobre</a><a href="#operacoes">Eventos</a><a href="/comunidade" onClick={event => { event.preventDefault(); navigate('/comunidade') }}>Comunidade</a><a href="#termos">Termos</a><a href="#privacidade">Privacidade</a><a href="#seguranca">Segurança</a><a href="mailto:operadorzerosac@gmail.com">Contato</a><button onClick={() => setAuthMode('login')}>Entrar</button><button onClick={() => setAuthMode('signup')}>Criar conta</button></div><small>© 2026 OPERADOR ZERO</small></footer>
    {authModal}
  </div>
}

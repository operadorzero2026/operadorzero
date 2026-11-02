import { ArrowRight, X } from 'lucide-react'
import { useState } from 'react'
import { completeGoogleSignup, type SessionUser } from './api'

/**
 * Quem tocou em "Entrar com Google" sem ter conta volta aqui: o Google ja confirmou a identidade,
 * falta so aceitar os Termos para criar a conta (sem voltar ao Google nem preencher formulario).
 */
export function GoogleSignupPrompt({ onDone, onExpired, onClose }: { onDone: (user: SessionUser) => void; onExpired: () => void; onClose: () => void }) {
  const [accepted, setAccepted] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const create = async () => {
    if (!accepted) { setError('Aceite os Termos de Uso e a Política de Privacidade para continuar.'); return }
    setPending(true); setError('')
    try {
      onDone(await completeGoogleSignup())
    } catch (e) {
      const code = (e as { code?: string }).code
      if (code === 'GOOGLE_SIGNUP_EXPIRED') onExpired()
      else setError(e instanceof Error ? e.message : 'Não foi possível criar a conta.')
    } finally {
      setPending(false)
    }
  }
  return <div className="auth-overlay" role="presentation">
    <section className="auth-panel google-signup-prompt" role="dialog" aria-modal="true" aria-labelledby="google-signup-title">
      <div className="auth-content">
      <button className="auth-close" type="button" onClick={onClose} aria-label="Fechar"><X/></button>
      <p className="eyebrow"><span/> Primeiro acesso</p>
      <h2 id="google-signup-title">Criar sua conta com o Google</h2>
      <p>Sua conta Google foi confirmada, mas ainda não existe um perfil no Operador Zero com ela. Aceite os termos e pronto: a conta é criada agora.</p>
      <label className="terms-check"><input type="checkbox" checked={accepted} onChange={event => setAccepted(event.target.checked)}/>
        <span>Li e aceito os Termos de Uso e a Política de Privacidade.</span></label>
      {error && <p className="auth-notice" role="alert">{error}</p>}
      <button className="button auth-submit" type="button" disabled={pending} aria-busy={pending} onClick={() => void create()}>
        {pending ? 'Criando sua conta...' : 'Criar minha conta'} {!pending && <ArrowRight size={17}/>}
      </button>
      </div>
    </section>
  </div>
}

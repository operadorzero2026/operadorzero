import { ApiClientError } from './api'

export type AuthErrorInfo = { title: string; text: string; code?: string }

/**
 * Mensagens claras para o cadastro e o login, sem detalhes internos do servidor.
 * context: 'signup' ou 'login' (muda so a frase de falha de conexao).
 */
export function authError(error: unknown, context: 'signup' | 'login'): AuthErrorInfo {
  if (error instanceof ApiClientError) {
    switch (error.code) {
      case 'EMAIL_ALREADY_REGISTERED': return { code: error.code, title: 'E-mail já cadastrado', text: 'Este e-mail já possui uma conta. Faça login ou recupere sua senha.' }
      case 'INVALID_PASSWORD': return { code: error.code, title: 'Senha inválida', text: `Sua senha não atende aos requisitos de segurança. ${error.message}` }
      case 'EMAIL_NOT_VERIFIED': return { code: error.code, title: 'E-mail não confirmado', text: 'Seu e-mail ainda não foi confirmado.' }
      case 'INVALID_CREDENTIALS': return { code: error.code, title: 'Não foi possível entrar', text: 'E-mail ou senha incorretos.' }
      case 'RATE_LIMITED': return { code: error.code, title: 'Muitas tentativas', text: 'Aguarde alguns minutos e tente novamente.' }
      case 'TERMS_REQUIRED': return { code: error.code, title: 'Termos não aceitos', text: 'Aceite os Termos de Uso e a Política de Privacidade.' }
      case 'VALIDATION_ERROR': return { code: error.code, title: 'Revise os dados', text: error.field === 'email' || /e-?mail/i.test(error.message) ?'Informe um endereço de e-mail válido.' : error.message }
    }
    if (error.status && error.status >= 500) {
      return { title: 'Erro no servidor', text: context === 'signup' ? 'Ocorreu um problema ao criar sua conta.' : 'Ocorreu um problema ao entrar. Tente novamente.' }
    }
    if (error.code) return { code: error.code, title: 'Não foi possível concluir', text: error.message }
  }
  return { title: 'Falha de conexão', text: context === 'signup' ? 'Não foi possível concluir seu cadastro. Tente novamente.' : 'Não foi possível entrar agora. Verifique a conexão e tente novamente.' }
}

const RETURN_KEY = 'oz:return-to'
/** Guarda a pagina de onde a pessoa saiu para entrar/cadastrar (ex.: o link de uma operacao) e devolve uma vez. */
export function rememberReturn(path = window.location.pathname) {
  try { if (path && path !== '/' && path.startsWith('/') && !path.startsWith('//')) window.localStorage.setItem(RETURN_KEY, path) } catch { /* sem armazenamento */ }
}
export function takeReturn(): string | null {
  try {
    const value = window.localStorage.getItem(RETURN_KEY)
    window.localStorage.removeItem(RETURN_KEY)
    return value && value.startsWith('/') && !value.startsWith('//') ? value : null
  } catch { return null }
}

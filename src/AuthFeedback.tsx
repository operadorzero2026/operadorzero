import { CheckCircle2, LoaderCircle, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'
import type { AuthErrorInfo } from './authRules'
import './auth-feedback.css'

/** Erro logo abaixo do botao: icone de alerta, texto vermelho; rola para ficar visivel mesmo com o teclado aberto. */
export function AuthErrorBox({ error, children }: { error: AuthErrorInfo; children?: ReactNode }) {
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => { box.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }) }, [error])
  return <div ref={box} className="auth-error" role="alert">
    <TriangleAlert aria-hidden="true"/>
    <div><b>{error.title}</b><p>{error.text}</p>{children}</div>
  </div>
}

export function Spinner() {
  return <LoaderCircle className="auth-spinner" aria-hidden="true"/>
}

/** Pop-up centralizado de resultado (conta criada, e-mail confirmado, boas-vindas do Google). */
export function AuthResultDialog({ title, children, actions }: { title: string; children: ReactNode; actions: ReactNode }) {
  useEffect(() => {
    document.body.classList.add('modal-open')
    return () => document.body.classList.remove('modal-open')
  }, [])
  return <div className="auth-result-backdrop" role="presentation">
    <section className="auth-result" role="alertdialog" aria-modal="true" aria-labelledby="auth-result-title">
      <CheckCircle2 className="auth-result-icon" aria-hidden="true"/>
      <h2 id="auth-result-title">{title}</h2>
      <div className="auth-result-body">{children}</div>
      <div className="auth-result-actions">{actions}</div>
    </section>
  </div>
}

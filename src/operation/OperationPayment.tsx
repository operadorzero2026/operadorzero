import { BadgeCheck, Banknote, Copy, QrCode, RefreshCw, RotateCcw, Wallet } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  confirmPaymentManually, createPixPayment, getManagedPayments, getMyPayment, registerPaymentRefund,
  type ManagedPaymentRow, type ManagedPayments, type MyPayment,
} from '../api'
import './operation-payment.css'

const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

const MY_STATUS: Record<string, { label: string; tone: string }> = {
  UNPAID: { label: 'Pagamento pendente', tone: 'warn' },
  PENDING: { label: 'Aguardando o Pix', tone: 'warn' },
  CONFIRMED: { label: 'Pagamento confirmado', tone: 'ok' },
  REJECTED: { label: 'Pagamento recusado', tone: 'bad' },
  CANCELLED: { label: 'Pix cancelado', tone: 'bad' },
  EXPIRED: { label: 'Código Pix vencido', tone: 'bad' },
  REFUNDED: { label: 'Pagamento estornado', tone: 'bad' },
}
const ROW_STATUS: Record<ManagedPaymentRow['paymentStatus'], string> = {
  UNPAID: 'Não pago', PENDING: 'Pix aguardando', CONFIRMED: 'Pago', CANCELLED: 'Cancelado', REFUNDED: 'Estornado',
}

/**
 * Pagamento da propria inscricao. Estar inscrito nao e estar pago: o status vem do servidor, que consulta o Mercado Pago.
 * Enquanto o Pix esta aberto, a tela confere sozinha a cada poucos segundos.
 */
export function MyOperationPayment({ operationId, onPaid }: { operationId: string; onPaid?: () => void }) {
  const [data, setData] = useState<MyPayment | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [copied, setCopied] = useState(false)
  const lastStatus = useRef<string | null>(null)

  const load = useCallback(async () => {
    try {
      const next = await getMyPayment(operationId)
      if (lastStatus.current && lastStatus.current !== 'CONFIRMED' && next.status === 'CONFIRMED') onPaid?.()
      lastStatus.current = next.status
      setData(next)
    } catch { /* o painel de inscricao ja mostra erros de conexao */ }
  }, [operationId, onPaid])
  useEffect(() => { void load() }, [load])
  const waiting = data?.status === 'PENDING' && Boolean(data.pix)
  useEffect(() => {
    if (!waiting) return
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load() }, 8000)
    return () => window.clearInterval(timer)
  }, [waiting, load])

  if (!data || data.status === 'FREE' || data.status === 'NOT_REGISTERED') return null
  const status = MY_STATUS[data.status] ?? MY_STATUS.UNPAID

  const pay = async () => {
    setBusy(true); setMessage('')
    try { const next = await createPixPayment(operationId); lastStatus.current = next.status; setData(next) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível gerar o Pix.') }
    finally { setBusy(false) }
  }
  const copy = async () => {
    if (!data.pix) return
    try { await navigator.clipboard.writeText(data.pix.code); setCopied(true); window.setTimeout(() => setCopied(false), 2500) }
    catch { setMessage('Não foi possível copiar. Selecione o código e copie manualmente.') }
  }

  return <div className="op-payment" aria-live="polite">
    <div className="op-payment-head">
      <span><Wallet/> Inscrição: <b>{money(data.amount)}</b></span>
      <em className={`op-payment-tag op-payment-tag--${status.tone}`}>{data.status === 'CONFIRMED' && <BadgeCheck/>}{status.label}</em>
    </div>
    {data.status === 'CONFIRMED' && data.confirmedAt && <p className="op-payment-muted">Confirmado em {when(data.confirmedAt)}. Os documentos da operação ficam liberados para você.</p>}
    {data.pix && <div className="op-payment-pix">
      {data.pix.qrBase64 && <img src={`data:image/png;base64,${data.pix.qrBase64}`} alt="QR Code do Pix" width={184} height={184}/>}
      <div className="op-payment-code">
        <label><span>Pix copia e cola</span><textarea readOnly value={data.pix.code} rows={3} onFocus={event => event.currentTarget.select()}/></label>
        <button type="button" className="module-primary" onClick={() => void copy()}><Copy/> {copied ? 'Código copiado!' : 'Copiar código Pix'}</button>
        {data.pix.expiresAt && <small>Válido até {when(data.pix.expiresAt)}. A confirmação aparece aqui sozinha assim que o banco aprovar.</small>}
        <button type="button" className="module-secondary" onClick={() => void load()}><RefreshCw/> Já paguei, verificar</button>
      </div>
    </div>}
    {data.canPay && <button type="button" className="module-primary op-payment-pay" disabled={busy} onClick={() => void pay()}>
      <QrCode/> {busy ? 'Gerando Pix…' : data.status === 'UNPAID' ? 'Pagar com Pix' : 'Gerar novo Pix'}</button>}
    {!data.canPay && !data.pix && data.status !== 'CONFIRMED' && data.reason && <p className="op-payment-muted">{data.reason}</p>}
    {message && <p className="op-payment-error" role="alert">{message}</p>}
  </div>
}

/** Painel financeiro do organizador: resumo, situacao de cada inscrito, confirmacao manual e registro de estorno. */
export function OperationPaymentsAdmin({ operationId, refreshKey, onChanged }: { operationId: string; refreshKey?: unknown; onChanged?: () => void }) {
  const [data, setData] = useState<ManagedPayments | null>(null)
  const [acting, setActing] = useState<{ row: ManagedPaymentRow; kind: 'confirm' | 'refund' } | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    try { setData(await getManagedPayments(operationId)) } catch { setData(null) }
  }, [operationId])
  useEffect(() => { void load() }, [load, refreshKey])

  if (!data || data.price <= 0) return null
  const submit = async () => {
    if (!acting) return
    setBusy(true); setMessage('')
    try {
      const result = acting.kind === 'confirm'
        ? await confirmPaymentManually(operationId, acting.row.operatorId, note.trim())
        : await registerPaymentRefund(operationId, acting.row.operatorId, note.trim())
      setMessage(result.message); setActing(null); setNote('')
      await load(); onChanged?.()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível registrar.')
    } finally { setBusy(false) }
  }

  const s = data.summary
  return <details className="op-payments-admin">
    <summary><Banknote/> Pagamentos <small>{s.confirmed} pagos · {s.unpaid + s.pending} pendentes · {money(s.confirmedTotal)}</small></summary>
    <p className="op-payment-muted">{data.onlineEnabled
      ? 'Os jogadores aprovados pagam por Pix no app; a confirmação chega sozinha. Você também pode confirmar pagamentos recebidos por fora.'
      : 'Pix pelo app desligado: confirme aqui os pagamentos recebidos por fora.'} Estornos não são automáticos: devolva o dinheiro e registre aqui.</p>
    <ul className="op-payments-summary">
      <li><b>{s.confirmed}</b> Pagos</li><li><b>{s.pending}</b> Pix aguardando</li><li><b>{s.unpaid}</b> Não pagos</li>
      <li><b>{s.cancelled}</b> Cancelados</li><li><b>{s.refunded}</b> Estornados</li>
    </ul>
    {message && <p className="registration-notice" role="status">{message}</p>}
    {data.rows.length === 0 ? <p className="op-payment-muted">Ainda não há inscritos.</p> : <ul className="op-payments-list">
      {data.rows.map(row => <li key={row.operatorId}>
        <span><b>{row.callsign}</b><small>{row.method === 'MANUAL' ? 'Confirmado pelo organizador' : row.method === 'PIX' ? 'Pix' : ''}{row.updatedAt && ` · ${when(row.updatedAt)}`}{row.note && ` · ${row.note}`}</small></span>
        <em className={`op-payment-tag op-payment-tag--${row.paymentStatus === 'CONFIRMED' ? 'ok' : row.paymentStatus === 'PENDING' || row.paymentStatus === 'UNPAID' ? 'warn' : 'bad'}`}>{ROW_STATUS[row.paymentStatus]}</em>
        {row.paymentStatus === 'CONFIRMED'
          ? <button type="button" className="module-secondary" onClick={() => { setActing({ row, kind: 'refund' }); setNote('') }}><RotateCcw/> Registrar estorno</button>
          : <button type="button" className="module-secondary" onClick={() => { setActing({ row, kind: 'confirm' }); setNote('') }}><BadgeCheck/> Confirmar pagamento</button>}
      </li>)}
    </ul>}
    {acting && <div className="registration-modal-backdrop" role="presentation" onClick={() => setActing(null)}>
      <section className="registration-modal" role="dialog" aria-modal="true" aria-labelledby="payment-title" onClick={event => event.stopPropagation()}>
        <h4 id="payment-title">{acting.kind === 'confirm' ? `Confirmar pagamento de ${acting.row.callsign}` : `Registrar estorno de ${acting.row.callsign}`}</h4>
        <p className="op-payment-muted">{acting.kind === 'confirm'
          ? `Use quando receber ${money(data.price)} por fora do app (dinheiro, Pix direto…). O jogador é avisado e os documentos são liberados.`
          : 'O sistema não devolve dinheiro. Faça a devolução (no Mercado Pago ou em mãos) e registre aqui. Os documentos voltam a ficar bloqueados.'}</p>
        <label><span>{acting.kind === 'confirm' ? 'Como foi pago' : 'Como foi devolvido'}</span>
          <textarea value={note} onChange={event => setNote(event.target.value)} maxLength={300} rows={2} required
            placeholder={acting.kind === 'confirm' ? 'Ex.: pago em dinheiro no dia 10' : 'Ex.: devolvido pelo painel do Mercado Pago'}/></label>
        <div className="registration-row">
          <button type="button" className="module-secondary" onClick={() => setActing(null)}>Cancelar</button>
          <button type="button" className={acting.kind === 'confirm' ? 'module-primary' : 'module-danger'} disabled={note.trim().length < 3 || busy} onClick={() => void submit()}>
            {busy ? 'Registrando…' : acting.kind === 'confirm' ? 'Confirmar pagamento' : 'Registrar estorno'}</button>
        </div>
      </section>
    </div>}
  </details>
}

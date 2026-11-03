import { BadgeCheck, Banknote, Copy, ExternalLink, QrCode, RefreshCw, RotateCcw, Wallet } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  confirmPaymentManually, createCheckoutPayment, createPixPayment, getManagedPayments, getMyPayment, refundViaMercadoPago, registerPaymentRefund,
  type ManagedPaymentRow, type ManagedPayments, type MyPayment,
} from '../api'
import { money, PAYMENT_STATUS_LABEL, paymentMethodLabel } from './paymentLabels'
import './operation-payment.css'

const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

const MY_STATUS: Record<string, { label: string; tone: string }> = {
  UNPAID: { label: 'Pagamento pendente', tone: 'warn' },
  PENDING: { label: 'Aguardando o pagamento', tone: 'warn' },
  CONFIRMED: { label: 'Pagamento confirmado', tone: 'ok' },
  PARTIALLY_REFUNDED: { label: 'Pago (reembolso parcial)', tone: 'ok' },
  REJECTED: { label: 'Pagamento recusado', tone: 'bad' },
  CANCELLED: { label: 'Pagamento cancelado', tone: 'bad' },
  EXPIRED: { label: 'Prazo do pagamento vencido', tone: 'bad' },
  REFUNDED: { label: 'Pagamento estornado', tone: 'bad' },
  CHARGEBACK: { label: 'Pagamento contestado', tone: 'bad' },
}
const ROW_STATUS: Record<ManagedPaymentRow['paymentStatus'], string> = PAYMENT_STATUS_LABEL
/**
 * Pagamento da propria inscricao. Estar inscrito nao e estar pago: o status vem do servidor, que consulta o Mercado Pago.
 * Com o organizador conectado, o jogador paga no Mercado Pago (Pix, cartao e outros meios habilitados) e a divisao com a
 * plataforma acontece no proprio pagamento. Sem conexao, vale o Pix direto (se ligado) ou o combinado com o organizador.
 */
export function MyOperationPayment({ operationId, onPaid }: { operationId: string; onPaid?: () => void }) {
  const [data, setData] = useState<MyPayment | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [copied, setCopied] = useState(false)
  const [returned] = useState(() => new URLSearchParams(window.location.search).get('pagamento') === 'retorno')
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
  useEffect(() => {
    if (!returned) return
    // Volta do Mercado Pago: tira o parametro do endereco; a confirmacao vem do servidor, nunca da URL.
    const url = new URL(window.location.href)
    url.searchParams.delete('pagamento')
    window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash)
  }, [returned])
  const waiting = data?.status === 'PENDING' && (Boolean(data.pix) || Boolean(data.checkoutUrl) || returned)
  useEffect(() => {
    if (!waiting) return
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load() }, 8000)
    return () => window.clearInterval(timer)
  }, [waiting, load])

  if (!data || data.status === 'FREE' || data.status === 'NOT_REGISTERED') return null
  const status = MY_STATUS[data.status] ?? MY_STATUS.UNPAID
  const paid = data.status === 'CONFIRMED' || data.status === 'PARTIALLY_REFUNDED'

  const payPix = async () => {
    setBusy(true); setMessage('')
    try { const next = await createPixPayment(operationId); lastStatus.current = next.status; setData(next) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível gerar o Pix.') }
    finally { setBusy(false) }
  }
  const payCheckout = async () => {
    setBusy(true); setMessage('')
    try { window.location.assign((await createCheckoutPayment(operationId)).url) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível abrir o Mercado Pago.'); setBusy(false) }
  }
  const copy = async () => {
    if (!data.pix) return
    try { await navigator.clipboard.writeText(data.pix.code); setCopied(true); window.setTimeout(() => setCopied(false), 2500) }
    catch { setMessage('Não foi possível copiar. Selecione o código e copie manualmente.') }
  }

  return <div className="op-payment" aria-live="polite">
    <div className="op-payment-head">
      <span><Wallet/> Inscrição: <b>{money(data.amount)}</b></span>
      <em className={`op-payment-tag op-payment-tag--${status.tone}`}>{paid && <BadgeCheck/>}{status.label}</em>
    </div>
    {paid && data.confirmedAt && <p className="op-payment-muted">Confirmado em {when(data.confirmedAt)}. Os documentos da operação ficam liberados para você.
      {data.providerPaymentId && <> Comprovante no Mercado Pago: operação nº <b>{data.providerPaymentId}</b>.</>}</p>}
    {returned && !paid && data.status === 'PENDING' && <p className="op-payment-muted">Confirmando seu pagamento com o Mercado Pago… Pix costuma confirmar em segundos; aguarde nesta tela.</p>}
    {data.pix && <div className="op-payment-pix">
      {data.pix.qrBase64 && <img src={`data:image/png;base64,${data.pix.qrBase64}`} alt="QR Code do Pix" width={184} height={184}/>}
      <div className="op-payment-code">
        <label><span>Pix copia e cola</span><textarea readOnly value={data.pix.code} rows={3} onFocus={event => event.currentTarget.select()}/></label>
        <button type="button" className="module-primary" onClick={() => void copy()}><Copy/> {copied ? 'Código copiado!' : 'Copiar código Pix'}</button>
        {data.pix.expiresAt && <small>Válido até {when(data.pix.expiresAt)}. A confirmação aparece aqui sozinha assim que o banco aprovar.</small>}
        <button type="button" className="module-secondary" onClick={() => void load()}><RefreshCw/> Já paguei, verificar</button>
      </div>
    </div>}
    {data.checkoutUrl && !paid && <div className="op-payment-code">
      <a className="module-primary op-payment-pay" href={data.checkoutUrl}><ExternalLink/> Continuar pagamento no Mercado Pago</a>
      <button type="button" className="module-secondary op-payment-pay" onClick={() => void load()}><RefreshCw/> Já paguei, verificar</button>
    </div>}
    {data.canPay && !data.checkoutUrl && <div className="op-payment-choose">
      <p className="op-payment-muted">Escolha como deseja pagar sua inscrição</p>
      {data.checkoutEnabled && <button type="button" className="module-primary op-payment-pay" disabled={busy} onClick={() => void payCheckout()}>
        <ExternalLink/> {busy ? 'Abrindo o Mercado Pago…' : 'Pagar com Mercado Pago'}</button>}
      {data.checkoutEnabled && <small className="op-payment-muted">Pix, cartão e os demais meios disponíveis são escolhidos no Mercado Pago.</small>}
      {data.pixEnabled && <button type="button" className="module-primary op-payment-pay" disabled={busy} onClick={() => void payPix()}>
        <QrCode/> {busy ? 'Gerando Pix…' : data.status === 'UNPAID' ? 'Pagar com Pix' : 'Gerar novo Pix'}</button>}
    </div>}
    {!data.canPay && !data.pix && !paid && data.reason && <p className="op-payment-muted">{data.reason}</p>}
    {message && <p className="op-payment-error" role="alert">{message}</p>}
  </div>
}

/** Painel financeiro da operacao: resumo, situacao de cada inscrito, confirmacao manual, reembolso e estorno. */
export function OperationPaymentsAdmin({ operationId, refreshKey, onChanged }: { operationId: string; refreshKey?: unknown; onChanged?: () => void }) {
  const [data, setData] = useState<ManagedPayments | null>(null)
  const [acting, setActing] = useState<{ row: ManagedPaymentRow; kind: 'confirm' | 'refund' | 'online-refund' } | null>(null)
  const [note, setNote] = useState('')
  const [amount, setAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    try { setData(await getManagedPayments(operationId)) } catch { setData(null) }
  }, [operationId])
  useEffect(() => { void load() }, [load, refreshKey])

  if (!data || data.price <= 0) return null
  const partial = amount.trim() ? Number(amount.replace(',', '.')) : null
  const submit = async () => {
    if (!acting) return
    setBusy(true); setMessage('')
    try {
      const result = acting.kind === 'confirm'
        ? await confirmPaymentManually(operationId, acting.row.operatorId, note.trim())
        : acting.kind === 'online-refund'
          ? await refundViaMercadoPago(operationId, acting.row.operatorId, note.trim(), partial)
          : await registerPaymentRefund(operationId, acting.row.operatorId, note.trim())
      setMessage(result.message); setActing(null); setNote(''); setAmount('')
      await load(); onChanged?.()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível registrar.')
    } finally { setBusy(false) }
  }

  const s = data.summary
  const estimated = s.feesEstimated ? ' (estimado)' : ''
  return <details className="op-payments-admin">
    <summary><Banknote/> Pagamentos <small>{s.confirmed} pagos · {s.unpaid + s.pending} pendentes · {money(s.confirmedTotal)}</small></summary>
    <p className="op-payment-muted">{data.marketplace
      ? `Os jogadores aprovados pagam no seu Mercado Pago; a comissão da plataforma (${data.commissionPercent ?? 0}% do valor) sai no próprio pagamento e a confirmação chega sozinha.`
      : data.onlineEnabled
        ? 'Os jogadores aprovados pagam por Pix na conta da plataforma (sem divisão automática). Conecte sua conta em Financeiro para receber direto.'
        : 'Pagamento online desligado: conecte sua conta Mercado Pago em Financeiro, ou confirme aqui os pagamentos recebidos por fora.'}
      {' '}Reembolsos nunca são automáticos.</p>
    <ul className="op-payments-summary">
      <li><b>{s.confirmed}</b> Pagos</li><li><b>{s.pending}</b> Aguardando</li><li><b>{s.unpaid}</b> Não pagos</li>
      <li><b>{s.cancelled}</b> Cancelados</li><li><b>{s.refunded}</b> Estornados</li>
    </ul>
    <ul className="op-payments-summary">
      <li>Bruto <b>{money(s.confirmedTotal)}</b></li>
      <li>Tarifas Mercado Pago <b>{money(s.providerFees)}</b>{estimated}</li>
      <li>Comissão Operador Zero <b>{money(s.commission)}</b></li>
      <li>Líquido estimado do organizador <b>{money(s.netOrganizer)}</b>{estimated}</li>
      {s.refundedTotal > 0 && <li>Reembolsado <b>{money(s.refundedTotal)}</b></li>}
    </ul>
    <p className="op-payment-muted">Valores aprovados não são o mesmo que saldo disponível para saque: o prazo de liberação segue as condições da sua conta Mercado Pago.</p>
    {message && <p className="registration-notice" role="status">{message}</p>}
    {data.rows.length === 0 ? <p className="op-payment-muted">Ainda não há inscritos.</p> : <ul className="op-payments-list">
      {data.rows.map(row => <li key={row.operatorId}>
        <span><b>{row.callsign}</b><small>{paymentMethodLabel(row)}{row.updatedAt && ` · ${when(row.updatedAt)}`}{row.transactionId && ` · nº ${row.transactionId}`}{row.note && ` · ${row.note}`}</small></span>
        <em className={`op-payment-tag op-payment-tag--${row.paymentStatus === 'CONFIRMED' || row.paymentStatus === 'PARTIALLY_REFUNDED' ? 'ok' : row.paymentStatus === 'PENDING' || row.paymentStatus === 'UNPAID' ? 'warn' : 'bad'}`}>{ROW_STATUS[row.paymentStatus]}</em>
        {row.refundableOnline
          ? <button type="button" className="module-secondary" onClick={() => { setActing({ row, kind: 'online-refund' }); setNote(''); setAmount('') }}><RotateCcw/> Reembolsar pelo Mercado Pago</button>
          : row.paymentStatus === 'CONFIRMED' && row.provider !== 'MERCADO_PAGO_MARKETPLACE'
            ? <button type="button" className="module-secondary" onClick={() => { setActing({ row, kind: 'refund' }); setNote('') }}><RotateCcw/> Registrar estorno</button>
            : !['CONFIRMED', 'PARTIALLY_REFUNDED'].includes(row.paymentStatus) && <button type="button" className="module-secondary" onClick={() => { setActing({ row, kind: 'confirm' }); setNote('') }}><BadgeCheck/> Confirmar pagamento</button>}
      </li>)}
    </ul>}
    {acting && <div className="registration-modal-backdrop" role="presentation" onClick={() => setActing(null)}>
      <section className="registration-modal" role="dialog" aria-modal="true" aria-labelledby="payment-title" onClick={event => event.stopPropagation()}>
        <h4 id="payment-title">{acting.kind === 'confirm' ? `Confirmar pagamento de ${acting.row.callsign}`
          : acting.kind === 'online-refund' ? `Reembolsar ${acting.row.callsign} pelo Mercado Pago` : `Registrar estorno de ${acting.row.callsign}`}</h4>
        <p className="op-payment-muted">{acting.kind === 'confirm'
          ? `Use quando receber ${money(data.price)} por fora do app (dinheiro, Pix direto…). O jogador é avisado e os documentos são liberados.`
          : acting.kind === 'online-refund'
            ? 'O Mercado Pago devolve o valor ao jogador pelo mesmo meio e desconta da sua conta e da plataforma, na proporção de cada um. Sem saldo suficiente na sua conta, o Mercado Pago recusa.'
            : 'O sistema não devolve dinheiro. Faça a devolução (no Mercado Pago ou em mãos) e registre aqui. Os documentos voltam a ficar bloqueados.'}</p>
        {acting.kind === 'online-refund' && <label><span>Valor (vazio = total de {money(acting.row.amount ?? data.price)})</span>
          <input inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} placeholder="Ex.: 40,00"/></label>}
        <label><span>{acting.kind === 'confirm' ? 'Como foi pago' : 'Motivo'}</span>
          <textarea value={note} onChange={event => setNote(event.target.value)} maxLength={300} rows={2} required
            placeholder={acting.kind === 'confirm' ? 'Ex.: pago em dinheiro no dia 10' : 'Ex.: jogo cancelado pela chuva'}/></label>
        <div className="registration-row">
          <button type="button" className="module-secondary" onClick={() => setActing(null)}>Cancelar</button>
          <button type="button" className={acting.kind === 'confirm' ? 'module-primary' : 'module-danger'}
            disabled={note.trim().length < 3 || busy || (partial !== null && !(partial > 0))} onClick={() => void submit()}>
            {busy ? 'Enviando…' : acting.kind === 'confirm' ? 'Confirmar pagamento' : acting.kind === 'online-refund' ? 'Confirmar reembolso' : 'Registrar estorno'}</button>
        </div>
      </section>
    </div>}
  </details>
}

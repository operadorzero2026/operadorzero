import { AlertTriangle, BadgeCheck, Download, Link2, Link2Off, Percent, RefreshCw, Wallet } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import {
  connectMercadoPago, disconnectMercadoPago, downloadFinanceCsv, getAdminFinance, getCommissionSettings, getMyFinance, saveCommission,
  type AdminFinance, type CommissionSettings, type FinanceFilters, type FinancePayment, type OrganizerFinance,
} from './api'
import { money, PAYMENT_STATUS_LABEL, paymentMethodLabel } from './operation/paymentLabels'
import './finance.css'

const when = (value?: string | null) => value ? new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'

const CONNECTION: Record<string, { label: string; tone: string }> = {
  NOT_CONNECTED: { label: 'Não conectado', tone: 'warn' },
  CONNECTING: { label: 'Conectando…', tone: 'warn' },
  CONNECTED: { label: 'Conectado', tone: 'ok' },
  NEEDS_RECONNECT: { label: 'Necessita reconexão', tone: 'bad' },
  DISCONNECTED: { label: 'Não conectado', tone: 'warn' },
  ERROR: { label: 'Erro de autorização', tone: 'bad' },
}
/** Resultado do retorno do Mercado Pago (/financeiro?mercadopago=...). */
const RESULT: Record<string, { text: string; ok?: boolean }> = {
  connected: { text: 'Conta Mercado Pago conectada. Os próximos pagamentos das suas operações caem direto na sua conta.', ok: true },
  cancelled: { text: 'Você cancelou a autorização no Mercado Pago. Nada foi alterado.' },
  invalid_state: { text: 'O pedido de conexão expirou ou já foi usado. Tente conectar de novo.' },
  session_mismatch: { text: 'A conexão foi iniciada em outra sessão. Entre com a sua conta e tente de novo.' },
  code_expired: { text: 'A autorização do Mercado Pago expirou. Tente conectar de novo.' },
  invalid_code: { text: 'O Mercado Pago não devolveu a autorização. Tente de novo.' },
  account_in_use: { text: 'Esta conta Mercado Pago já recebe por outro organizador do Operador Zero.' },
  authorization_error: { text: 'O Mercado Pago recusou a autorização. Confira se a conta é de vendedor e tente de novo.' },
  unavailable: { text: 'O Mercado Pago não respondeu. Tente de novo em alguns minutos.' },
}

/** Financeiro: configuracoes de recebimento (Mercado Pago), relatorio por jogo e historico; area da plataforma para administradores. */
export function FinancePage({ isAdmin }: { isAdmin: boolean }) {
  const [tab, setTab] = useState<'mine' | 'platform'>('mine')
  return <main className="finance-page">
    <header className="finance-head"><small>FINANCEIRO</small><h1>{tab === 'mine' ? 'Recebimentos' : 'Plataforma'}</h1>
      {isAdmin && <div className="finance-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'mine'} className={tab === 'mine' ? 'active' : ''} onClick={() => setTab('mine')}>Minhas operações</button>
        <button type="button" role="tab" aria-selected={tab === 'platform'} className={tab === 'platform' ? 'active' : ''} onClick={() => setTab('platform')}>Comissões e plataforma</button>
      </div>}
    </header>
    {tab === 'mine' ? <OrganizerFinancePanel/> : <PlatformFinancePanel/>}
  </main>
}

function OrganizerFinancePanel() {
  const [data, setData] = useState<OrganizerFinance | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ text: string; ok?: boolean } | null>(() => {
    const code = new URLSearchParams(window.location.search).get('mercadopago')
    return code ? RESULT[code] ?? { text: 'Não foi possível concluir a conexão.' } : null
  })
  const load = useCallback(async () => {
    try { setData(await getMyFinance()); setError('') } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar o financeiro.') }
  }, [])
  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!new URLSearchParams(window.location.search).has('mercadopago')) return
    window.history.replaceState(window.history.state, '', window.location.pathname)
  }, [])

  const connect = async () => {
    setBusy(true); setNotice(null)
    try { window.location.assign((await connectMercadoPago()).authorizationUrl) }
    catch (e) { setNotice({ text: e instanceof Error ? e.message : 'Não foi possível iniciar a conexão.' }); setBusy(false) }
  }
  const disconnect = async () => {
    if (!window.confirm('Desconectar sua conta Mercado Pago? As inscrições das suas operações deixam de ser pagas pelo app até você conectar de novo.')) return
    setBusy(true)
    try { setNotice({ text: (await disconnectMercadoPago()).message }); await load() }
    catch (e) { setNotice({ text: e instanceof Error ? e.message : 'Não foi possível desconectar.' }) }
    finally { setBusy(false) }
  }

  if (error) return <p className="finance-error" role="alert">{error} <button type="button" onClick={() => void load()}>Tentar novamente</button></p>
  if (!data) return <p className="finance-muted">Carregando…</p>
  const c = data.connection
  const state = busy && c.status !== 'CONNECTED' ? CONNECTION.CONNECTING : CONNECTION[c.status] ?? CONNECTION.NOT_CONNECTED

  return <>
    <section className="finance-card" aria-labelledby="mp-title">
      <h2 id="mp-title"><Wallet/> Configurações de recebimento</h2>
      <div className="finance-provider">
        <div><b>MERCADO PAGO</b><em className={`finance-tag finance-tag--${state.tone}`}>{c.status === 'CONNECTED' && <BadgeCheck/>}{state.label}</em></div>
        {c.testEnvironment && <span className="finance-tag finance-tag--warn">Ambiente de testes</span>}
      </div>
      {notice && <p className={notice.ok ? 'finance-ok' : 'finance-error'} role="status">{notice.text}</p>}
      {!c.available && <p className="finance-muted">A conexão com o Mercado Pago ainda não foi liberada pela plataforma.</p>}
      {c.status === 'CONNECTED' && <p className="finance-muted">Conta nº {c.accountId} · conectada em {when(c.connectedAt)}. As inscrições pagas caem direto na sua conta;
        a comissão do Operador Zero ({c.commissionPercent}% do valor da inscrição) e a tarifa do Mercado Pago saem no próprio pagamento.</p>}
      {(c.status === 'NEEDS_RECONNECT' || c.status === 'ERROR') && <p className="finance-error"><AlertTriangle/> {c.lastError ?? 'Conecte a conta novamente para voltar a receber pelo app.'}</p>}
      <div className="finance-row">
        {c.available && c.status !== 'CONNECTED' && <button type="button" className="module-primary" disabled={busy} onClick={() => void connect()}>
          <Link2/> {busy ? 'Abrindo o Mercado Pago…' : c.status === 'NEEDS_RECONNECT' || c.status === 'ERROR' ? 'Reconectar minha conta Mercado Pago' : 'Conectar minha conta Mercado Pago'}</button>}
        {c.status === 'CONNECTED' && <button type="button" className="module-danger" disabled={busy} onClick={() => void disconnect()}><Link2Off/> Desconectar</button>}
      </div>
      <p className="finance-muted">Você autoriza no site do Mercado Pago; o Operador Zero nunca vê sua senha. Valores aprovados não são o mesmo que saldo disponível para saque.</p>
    </section>

    <section className="finance-card" aria-labelledby="ops-title">
      <h2 id="ops-title">Resumo por jogo</h2>
      {data.operations.length === 0 ? <p className="finance-muted">Nenhuma operação paga ainda.</p> : <div className="finance-table-wrap"><table className="finance-table">
        <thead><tr><th>Jogo</th><th>Inscrições</th><th>Pagas</th><th>Pendentes</th><th>Canceladas</th><th>Bruto</th><th>Tarifas MP</th><th>Comissão OZ</th><th>Líquido</th><th>Reembolsos</th></tr></thead>
        <tbody>{data.operations.map(o => <tr key={o.operationId}>
          <td><a href={`/convocacao/${o.operationId}`}>{o.name}</a><small>{o.date}</small></td>
          <td>{o.registrations}</td><td>{o.paid}</td><td>{o.pending}</td><td>{o.cancelled}</td><td>{money(o.gross)}</td>
          <td>{money(o.providerFees)}{o.estimated && <sup title="Tarifa ainda não confirmada pelo Mercado Pago">*</sup>}</td>
          <td>{money(o.commission)}</td><td>{money(o.net)}{o.estimated && <sup>*</sup>}</td><td>{money(o.refunded)}</td>
        </tr>)}</tbody>
      </table></div>}
      <p className="finance-muted">* estimado: o Mercado Pago ainda não informou a tarifa efetiva. Os valores são conciliados automaticamente.</p>
    </section>

    <section className="finance-card" aria-labelledby="hist-title">
      <h2 id="hist-title">Histórico de pagamentos</h2>
      <PaymentsTable rows={data.payments}/>
    </section>
  </>
}

function PlatformFinancePanel() {
  const [settings, setSettings] = useState<CommissionSettings | null>(null)
  const [overview, setOverview] = useState<AdminFinance | null>(null)
  const [filters, setFilters] = useState<FinanceFilters>({})
  const [applied, setApplied] = useState<FinanceFilters>({})
  const [defaultPercent, setDefaultPercent] = useState('')
  const [organizer, setOrganizer] = useState('')
  const [organizerPercent, setOrganizerPercent] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const loadSettings = useCallback(async () => {
    try { const s = await getCommissionSettings(); setSettings(s); setDefaultPercent(String(s.defaultPercent)) } catch (e) { setMessage(e instanceof Error ? e.message : 'Erro ao carregar comissões.') }
  }, [])
  const loadOverview = useCallback(async () => {
    try { setOverview(await getAdminFinance(applied)) } catch (e) { setMessage(e instanceof Error ? e.message : 'Erro ao carregar a visão geral.') }
  }, [applied])
  useEffect(() => { void loadSettings() }, [loadSettings])
  useEffect(() => { void loadOverview() }, [loadOverview])

  const save = async (percent: number | null, username?: string) => {
    setBusy(true); setMessage('')
    try { setMessage((await saveCommission(percent, username)).message); await loadSettings(); if (username) { setOrganizer(''); setOrganizerPercent('') } }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Não foi possível salvar.') }
    finally { setBusy(false) }
  }
  const exportCsv = async () => {
    try {
      const blob = await downloadFinanceCsv(applied)
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob); link.download = 'pagamentos-operador-zero.csv'; link.click()
      URL.revokeObjectURL(link.href)
    } catch (e) { setMessage(e instanceof Error ? e.message : 'Não foi possível exportar.') }
  }
  const number = (value: string) => Number(value.replace(',', '.'))

  return <>
    {message && <p className="finance-ok" role="status">{message}</p>}
    <section className="finance-card" aria-labelledby="comm-title">
      <h2 id="comm-title"><Percent/> Comissões da plataforma</h2>
      <p className="finance-muted">A comissão é um valor fixo calculado sobre o valor bruto da inscrição no momento da cobrança (o Mercado Pago não informa
        a tarifa antes de o jogador escolher o meio de pagamento). Alterações valem só para pagamentos novos.</p>
      <div className="finance-form">
        <label><span>Comissão padrão (%)</span><input inputMode="decimal" value={defaultPercent} onChange={e => setDefaultPercent(e.target.value)}/></label>
        <button type="button" className="module-primary" disabled={busy || !(number(defaultPercent) >= 0)} onClick={() => void save(number(defaultPercent))}>Salvar padrão</button>
      </div>
      <div className="finance-form">
        <label><span>Organizador (@usuário)</span><input value={organizer} onChange={e => setOrganizer(e.target.value)} placeholder="@organizador"/></label>
        <label><span>Comissão individual (%)</span><input inputMode="decimal" value={organizerPercent} onChange={e => setOrganizerPercent(e.target.value)} placeholder="Ex.: 8"/></label>
        <button type="button" className="module-primary" disabled={busy || !organizer.trim() || !(number(organizerPercent) >= 0)} onClick={() => void save(number(organizerPercent), organizer)}>Salvar individual</button>
      </div>
      {settings && settings.organizers.length > 0 && <ul className="finance-list">{settings.organizers.map(row => <li key={row.userId}>
        <span><b>{row.callsign ?? row.username}</b><small>@{row.username} · desde {when(row.effectiveFrom)}{row.changedBy && ` · por ${row.changedBy}`}</small></span>
        <em className="finance-tag">{row.percent == null ? `Padrão (${settings.defaultPercent}%)` : `${row.percent}%`}</em>
        {row.percent != null && <button type="button" className="module-secondary" disabled={busy} onClick={() => void save(null, row.username ?? '')}>Voltar ao padrão</button>}
      </li>)}</ul>}
    </section>

    <section className="finance-card" aria-labelledby="plat-title">
      <h2 id="plat-title">Visão consolidada</h2>
      <div className="finance-form finance-filters">
        <label><span>Organizador</span><input value={filters.organizer ?? ''} onChange={e => setFilters(f => ({ ...f, organizer: e.target.value }))} placeholder="@usuário"/></label>
        <label><span>Jogo (id)</span><input value={filters.operation ?? ''} onChange={e => setFilters(f => ({ ...f, operation: e.target.value }))}/></label>
        <label><span>De</span><input type="date" value={filters.from ?? ''} onChange={e => setFilters(f => ({ ...f, from: e.target.value }))}/></label>
        <label><span>Até</span><input type="date" value={filters.to ?? ''} onChange={e => setFilters(f => ({ ...f, to: e.target.value }))}/></label>
        <label><span>Meio</span><select value={filters.method ?? ''} onChange={e => setFilters(f => ({ ...f, method: e.target.value }))}>
          <option value="">Todos</option><option value="CHECKOUT">Mercado Pago (marketplace)</option><option value="pix">Pix</option>
          <option value="credit_card">Cartão de crédito</option><option value="PIX">Pix direto (plataforma)</option><option value="MANUAL">Manual</option></select></label>
        <label><span>Status</span><select value={filters.status ?? ''} onChange={e => setFilters(f => ({ ...f, status: e.target.value }))}>
          <option value="">Todos</option>{Object.entries(PAYMENT_STATUS_LABEL).filter(([k]) => k !== 'UNPAID').map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <div className="finance-row">
          <button type="button" className="module-primary" onClick={() => setApplied({ ...filters })}><RefreshCw/> Filtrar</button>
          <button type="button" className="module-secondary" onClick={() => void exportCsv()}><Download/> Exportar CSV</button>
        </div>
      </div>
      {overview && <>
        <ul className="finance-stats">
          <li><b>{overview.connections.organizers}</b> organizadores com jogos pagos</li>
          <li><b>{overview.connections.connected}</b> com Mercado Pago conectado</li>
          <li><b>{overview.connections.notConnected}</b> sem conta conectada</li>
          <li><b>{overview.connections.needsReconnect}</b> com conexão expirada</li>
        </ul>
        <ul className="finance-stats">
          <li><b>{money(overview.totals.gross)}</b> bruto aprovado</li>
          <li><b>{money(overview.totals.commission)}</b> comissão (marketplace){overview.totals.estimated && '*'}</li>
          <li><b>{money(overview.totals.providerFees)}</b> tarifas Mercado Pago{overview.totals.estimated && '*'}</li>
          <li><b>{overview.totals.approved}</b> aprovados · <b>{overview.totals.pending}</b> pendentes</li>
          <li><b>{overview.totals.refunded}</b> reembolsos ({money(overview.totals.refundedAmount)})</li>
          <li><b>{overview.totals.chargebacks}</b> contestações</li>
        </ul>
        {overview.totals.estimated && <p className="finance-muted">* inclui pagamentos com tarifa ainda não confirmada pelo Mercado Pago.</p>}
        <PaymentsTable rows={overview.payments} showOrganizer/>
      </>}
    </section>
  </>
}

function PaymentsTable({ rows, showOrganizer = false }: { rows: FinancePayment[]; showOrganizer?: boolean }) {
  if (rows.length === 0) return <p className="finance-muted">Nenhum pagamento.</p>
  return <div className="finance-table-wrap"><table className="finance-table">
    <thead><tr><th>Data</th><th>Jogo</th>{showOrganizer && <th>Organizador</th>}<th>Jogador</th><th>Meio</th><th>Status</th><th>Valor</th><th>Comissão</th><th>Tarifa MP</th><th>Líquido</th><th>Nº MP</th></tr></thead>
    <tbody>{rows.map(r => <tr key={r.id}>
      <td>{when(r.createdAt)}</td><td>{r.operationName}</td>{showOrganizer && <td>{r.organizer ?? '—'}</td>}<td>{r.player ?? '—'}</td>
      <td>{paymentMethodLabel(r)}</td><td>{PAYMENT_STATUS_LABEL[r.status] ?? r.status}</td><td>{money(r.amount)}</td>
      <td>{r.commission != null ? money(r.commission) : '—'}</td>
      <td>{r.providerFee != null ? money(r.providerFee) : '—'}{r.provider === 'MERCADO_PAGO_MARKETPLACE' && !r.feesConfirmed && r.providerFee == null && <small> a confirmar</small>}</td>
      <td>{r.net != null ? money(r.net) : '—'}</td><td>{r.transactionId ?? '—'}</td>
    </tr>)}</tbody>
  </table></div>
}

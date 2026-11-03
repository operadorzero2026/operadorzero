/** Rotulos de pagamento usados no painel da operacao e na tela Financeiro. */
const METHOD_LABEL: Record<string, string> = {
  pix: 'Pix', credit_card: 'Cartão de crédito', debit_card: 'Cartão de débito', account_money: 'Saldo Mercado Pago', ticket: 'Boleto',
  bank_transfer: 'Pix', digital_currency: 'Mercado Crédito',
}

export const paymentMethodLabel = (row: { method?: string | null; paymentType?: string | null }) =>
  row.method === 'MANUAL' ? 'Confirmado pelo organizador'
    : row.paymentType ? METHOD_LABEL[row.paymentType] ?? row.paymentType
    : row.method === 'CHECKOUT' ? 'Mercado Pago' : row.method === 'PIX' ? 'Pix (conta da plataforma)' : ''

export const PAYMENT_STATUS_LABEL: Record<string, string> = {
  UNPAID: 'Não pago', PENDING: 'Aguardando', CONFIRMED: 'Pago', PARTIALLY_REFUNDED: 'Pago (reemb. parcial)', CANCELLED: 'Cancelado',
  EXPIRED: 'Vencido', REJECTED: 'Recusado', REFUNDED: 'Estornado', CHARGEBACK: 'Contestado',
}

export const money = (value: number | null | undefined) => (value ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

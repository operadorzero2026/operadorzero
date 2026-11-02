import type { ReportReason } from '../api'

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: 'Spam ou propaganda' },
  { value: 'HARASSMENT', label: 'Assédio ou ofensa' },
  { value: 'HATE', label: 'Discurso de ódio' },
  { value: 'THREAT', label: 'Ameaça ou violência real' },
  { value: 'PERSONAL_DATA', label: 'Expõe dados pessoais ou localização' },
  { value: 'FRAUD', label: 'Golpe ou fraude' },
  { value: 'ILLEGAL', label: 'Conteúdo ilegal' },
  { value: 'OTHER', label: 'Outro motivo' },
]

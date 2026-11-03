import { Eye, FileText, Lock, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { deleteOperationDocument, getOperationDocuments, operationDocumentUrl, replaceOperationDocument, uploadOperationDocument, type DocumentListing } from '../api'
import './operation-page.css'

const LOCKED: Record<string, string> = {
  NOT_REGISTERED: 'Exclusivos para jogadores inscritos e aprovados.',
  PAYMENT_PENDING: 'Liberados após a confirmação do pagamento.',
  STARTED: 'O acesso aos documentos foi encerrado no início do jogo.',
  CANCELLED: 'Operação cancelada.',
}
const size = (bytes: number) => bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

/** "Missoes e documentos": PDFs em linhas compactas. Visualizar abre a leitura na plataforma (o servidor confere o acesso a cada abertura). */
export function OperationDocuments({ operationId }: { operationId: string }) {
  const [data, setData] = useState<DocumentListing | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')
  const add = useRef<HTMLInputElement>(null)
  const replaceInput = useRef<HTMLInputElement>(null)
  const replacing = useRef<string>('')

  const load = useCallback(() => getOperationDocuments(operationId).then(setData).catch(() => setData(null)), [operationId])
  useEffect(() => { void load() }, [load])

  const run = async (key: string, action: () => Promise<unknown>, done: string) => {
    setBusy(key); setNotice('')
    try { await action(); setNotice(done); await load() }
    catch (error) { setNotice(error instanceof Error ? error.message : 'Não foi possível concluir.') }
    finally { setBusy('') }
  }
  const check = (file: File) => {
    if (file.type && file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) { setNotice('Envie um arquivo PDF.'); return false }
    if (file.size > 9 * 1024 * 1024) { setNotice('O PDF pode ter no máximo 9 MB.'); return false }
    return true
  }

  if (!data) return null
  const updated = data.items.filter(item => item.recentlyUpdated)
  const canSee = data.manager || data.access === 'ALLOWED'
  if (!canSee && data.count === 0) return null
  return <section className="op-docs" aria-label="Missões e documentos">
    <header>
      <h4><FileText/> Missões e documentos</h4>
      {data.manager && <>
        <input ref={add} type="file" accept="application/pdf,.pdf" hidden onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file && check(file)) void run('add', () => uploadOperationDocument(operationId, file), 'Documento enviado.') }}/>
        <button type="button" className="op-docs-add" disabled={busy === 'add'} onClick={() => add.current?.click()}><Plus/> PDF</button>
      </>}
    </header>
    {updated.length > 0 && !data.manager && <p className="op-docs-updated" role="status">Documento atualizado: {updated.map(item => `${item.title} (${when(item.updatedAt)})`).join(', ')}</p>}
    {!canSee ? <p className="op-docs-locked"><Lock/> {data.count} {data.count === 1 ? 'documento' : 'documentos'} · {LOCKED[data.access] ?? 'Indisponível.'}</p>
      : data.items.length === 0 ? <p className="op-docs-empty">Nenhum documento enviado.</p>
      : <ul>{data.items.map(item => <li key={item.id}>
        <span className="op-docs-icon">PDF</span>
        <span className="op-docs-name"><b>{item.title}</b><small>{size(item.sizeBytes)}{item.version > 1 && <> · <em>Documento atualizado</em> {when(item.updatedAt)}{data.manager && item.updatedBy && ` por ${item.updatedBy}`}</>}</small></span>
        <a className="op-docs-btn" href={operationDocumentUrl(operationId, item.id)} target="_blank" rel="noopener" aria-label={`Visualizar ${item.title}`}><Eye/><span>Visualizar</span></a>
        {data.manager && <>
          <button type="button" className="op-docs-btn" disabled={busy === item.id} onClick={() => { replacing.current = item.id; replaceInput.current?.click() }} aria-label={`Substituir ${item.title}`}><RefreshCw/><span>Substituir</span></button>
          <button type="button" className="op-docs-btn op-docs-btn--danger" disabled={busy === item.id} onClick={() => { if (window.confirm(`Remover "${item.title}"?`)) void run(item.id, () => deleteOperationDocument(operationId, item.id), 'Documento removido.') }} aria-label={`Remover ${item.title}`}><Trash2/><span>Remover</span></button>
        </>}
      </li>)}</ul>}
    {data.manager && <input ref={replaceInput} type="file" accept="application/pdf,.pdf" hidden onChange={event => {
      const file = event.target.files?.[0]; event.target.value = ''
      const id = replacing.current
      if (file && id && check(file)) void run(id, () => replaceOperationDocument(operationId, id, file), 'Documento atualizado. Os inscritos verão o aviso na operação.')
    }}/>}
    {canSee && !data.manager && <small className="op-docs-note">Disponíveis até o início do jogo ({when(data.availableUntil)}).</small>}
    {data.manager && <small className="op-docs-note">Os inscritos aprovados acessam até o início do jogo{'.'} Em operação paga, só após a confirmação do pagamento.</small>}
    {notice && <p className="op-docs-notice" role="status">{notice}</p>}
  </section>
}

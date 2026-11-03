import { CalendarClock, CalendarDays, MapPin, Pencil, Plus, Search, Target, Trash2, Users, X } from 'lucide-react'
import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import {
  updateOperationStatus,
  createOperation,
  deleteOperation,
  getFields,
  getMaps,
  getOperationRoster,
  getOperationStructure,
  getOperations,
  getOperatorProfile,
  removeOperationCover,
  Operation,
  OperationRoster,
  OperationStructure,
  operationCoverUrl,
  publishOperation,
  updateOperation,
  uploadOperationCover,
  VenueField,
  VenueMap,
} from './api'
import { OperationRegistrationPanel } from './operation/OperationRegistrationPanel'
import { OperationCommandCenter } from './OperationCommandCenter'

const modalityLabels: Record<string, string> = {
  ELIMINATION: 'Mata-mata',
  CONQUEST: 'Conquista',
  CAPTURE_THE_FLAG: 'Captura de bandeira',
  BASE_DEFENSE: 'Defesa de base',
  ATTACK_DEFENSE: 'Ataque e defesa',
  ESCORT: 'Escolta',
  RESCUE: 'Resgate',
  DOMINATION: 'Dominação',
  OBJECTIVES: 'Missão por objetivos',
  CHAMPIONSHIP: 'Campeonato',
  THEMED: 'Operação temática',
  CUSTOM: 'Personalizada',
}
const statusLabels: Record<string, string> = {
  DRAFT: 'Rascunho',
  PUBLISHED: 'Publicada',
  REGISTRATION_OPEN: 'Inscrições abertas',
  FULL: 'Lotada',
  CONFIRMATION_PENDING: 'Confirmação pendente',
  IN_PROGRESS: 'Em andamento',
  FINISHED: 'Finalizada',
  CANCELLED: 'Cancelada',
}



// Próximos status que o organizador pode escolher a partir do status atual (o backend valida de novo).
const statusActions: Record<string, Array<{ status: string; label: string; danger?: boolean }>> = {
  PUBLISHED: [{ status: 'IN_PROGRESS', label: 'Iniciar operação' }],
  REGISTRATION_OPEN: [{ status: 'IN_PROGRESS', label: 'Iniciar operação' }],
  FULL: [{ status: 'REGISTRATION_OPEN', label: 'Reabrir inscrições' }, { status: 'IN_PROGRESS', label: 'Iniciar operação' }],
  CONFIRMATION_PENDING: [{ status: 'REGISTRATION_OPEN', label: 'Reabrir inscrições' }, { status: 'IN_PROGRESS', label: 'Iniciar operação' }],
  IN_PROGRESS: [{ status: 'FINISHED', label: 'Finalizar operação' }],
}
const CANCELLABLE_STATUSES = ['PUBLISHED', 'REGISTRATION_OPEN', 'FULL', 'CONFIRMATION_PENDING', 'IN_PROGRESS']

export function OperationsPage() {
  const [items, setItems] = useState<Operation[]>([]),
    [fields, setFields] = useState<VenueField[]>([]),
    [maps, setMaps] = useState<VenueMap[]>([]),
    [query, setQuery] = useState(''),
    [showCreate, setShowCreate] = useState(false),
    [feedback, setFeedback] = useState(''),
    [pending, setPending] = useState(''),
    [selected, setSelected] = useState<Operation | null>(null),
    [roster, setRoster] = useState<OperationRoster | null>(null),
    [structure, setStructure] = useState<OperationStructure | null>(null),
    [rosterError, setRosterError] = useState(''),
    [structureError, setStructureError] = useState(''),
    [registrationModel, setRegistrationModel] = useState<'SINGLE_LIST' | 'TEAMS'>('SINGLE_LIST'),
    [coverPreview, setCoverPreview] = useState(''),
    [deleteReason, setDeleteReason] = useState(''),
    [operationEditMode, setOperationEditMode] = useState(false),
    [operatorLocation, setOperatorLocation] = useState({ city: '', stateCode: '' })
  const load = useCallback(async (q = '') => {
    try {
      const [operations, availableFields] = await Promise.all([getOperations(q), getFields()])
      setItems(operations.items)
      setFields(availableFields.items)
      try {
        const profile = await getOperatorProfile()
        setOperatorLocation({ city: profile.city?.trim() || '', stateCode: profile.stateCode?.trim() || '' })
      } catch {
        setOperatorLocation({ city: '', stateCode: '' })
      }
    } catch {
      setFeedback('Não foi possível carregar as operações.')
    }
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  const orderedItems = useMemo(() => {
    const city = operatorLocation.city.toLocaleLowerCase('pt-BR')
    const stateCode = operatorLocation.stateCode.toLocaleUpperCase('pt-BR')
    const proximity = (item: Operation) => {
      if (city && item.city.trim().toLocaleLowerCase('pt-BR') === city) return 0
      if (stateCode && item.stateCode.trim().toLocaleUpperCase('pt-BR') === stateCode) return 1
      return 2
    }
    return [...items].sort((left, right) => {
      const region = proximity(left) - proximity(right)
      if (region) return region
      const date = `${left.operationDate}T${left.presentationTime}`.localeCompare(`${right.operationDate}T${right.presentationTime}`)
      return date || left.name.localeCompare(right.name, 'pt-BR')
    })
  }, [items, operatorLocation])
  const selectField = async (id: string) =>
    setMaps(id ? (await getMaps(id)).items : [])
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const d = new FormData(e.currentTarget)
    const cover = d.get('cover')
    setPending('create')
    setFeedback('')
    try {
      const created = await createOperation({
        name: d.get('name'),
        description: d.get('description'),
        fieldId: d.get('fieldId'),
        mapId: d.get('mapId') || null,
        operationDate: d.get('operationDate'),
        presentationTime: d.get('presentationTime'),
        startTime: d.get('startTime'),
        endTime: d.get('endTime'),
        modality: d.get('modality'),
        customModality: d.get('customModality'),
        rules: d.get('rules'),
        registrationModel,
        participantLimit: d.get('participantLimit') ? Number(d.get('participantLimit')) : null,
        teamLimit: registrationModel === 'TEAMS' ? Number(d.get('teamLimit') || 2) : null,
        registrationPrice: Number(d.get('registrationPrice') || 0),
        paymentMethods: d.get('paymentMethods'),
        minimumAge: Number(d.get('minimumAge')),
        requiredEquipment: d.get('requiredEquipment'),
        fpsLimit: d.get('fpsLimit') ? Number(d.get('fpsLimit')) : null,
        entryMode: d.get('entryMode'),
        approvalRequired: d.get('approvalRequired') === 'on',
        waitingListEnabled: d.get('waitingListEnabled') === 'on',
      })
      if (cover instanceof File && cover.size > 0) await uploadOperationCover(created.id, cover)
      setFeedback(registrationModel === 'TEAMS' ? 'Rascunho salvo. Ajuste nomes e cores das equipes e publique.' : 'Rascunho salvo. Revise e publique para abrir as inscrições.')
      setShowCreate(false)
      setCoverPreview('')
      await load('')
      await openOperation(created, true)
    } catch (err) {
      setFeedback(
        err instanceof Error
          ? err.message
          : 'Não foi possível criar a operação.',
      )
    } finally {
      setPending('')
    }
  }
  const publish = async (item: Operation) => {
    setPending(item.id)
    setFeedback('')
    try {
      const published = await publishOperation(item.id)
      setFeedback('Operação publicada com inscrições abertas.')
      await load(query)
      if (selected?.id === item.id) await openOperation(published)
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : 'Não foi possível publicar a operação.')
    } finally {
      setPending('')
    }
  }
  const openOperation = async (item: Operation, startEditing = false) => {
    setSelected(item)
    setOperationEditMode(startEditing && item.managedByCurrentUser)
    setRoster(null)
    setStructure(null)
    setRosterError('')
    setStructureError('')
    const [rosterResult, structureResult] = await Promise.allSettled([
      getOperationRoster(item.id),
      getOperationStructure(item.id),
    ])
    if (rosterResult.status === 'fulfilled') {
      setRoster(rosterResult.value)
    } else {
      setRosterError(rosterResult.reason instanceof Error ? rosterResult.reason.message : 'Não foi possível carregar os times e participantes.')
    }
    if (structureResult.status === 'fulfilled') setStructure(structureResult.value)
    else setStructureError(structureResult.reason instanceof Error ? structureResult.reason.message : 'Não foi possível carregar a organização da operação.')
  }
  const updateManagedOperation = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selected) return
    const data = new FormData(event.currentTarget)
    setPending(`edit-${selected.id}`)
    setFeedback('')
    try {
      const updated = await updateOperation(selected.id, {
        operationDate: data.get('operationDate'), presentationTime: data.get('presentationTime'),
        startTime: data.get('startTime'), endTime: data.get('endTime'), briefing: data.get('briefing'),
        reason: data.get('reason'), version: selected.version ?? 0,
      })
      setSelected(updated)
      setOperationEditMode(false)
      setFeedback('Data, horários e briefing atualizados.')
      await load(query)
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : 'Não foi possível atualizar a operação.')
    } finally { setPending('') }
  }
  const removeManagedOperation = async () => {
    if (!selected || !deleteReason.trim()) { setFeedback('Informe o motivo da exclusão.'); return }
    if (!window.confirm(`Excluir a operação "${selected.name}"? Ela deixará de aparecer para os usuários.`)) return
    setPending(`delete-${selected.id}`)
    setFeedback('')
    try {
      await deleteOperation(selected.id, deleteReason.trim())
      setSelected(null)
      setOperationEditMode(false)
      setDeleteReason('')
      setFeedback('Operação excluída com segurança.')
      await load(query)
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : 'Não foi possível excluir a operação.')
    } finally { setPending('') }
  }
  const runOperationAction = async (key: string, action: () => Promise<unknown>, success: string) => {
    if (!selected) return
    setPending(key)
    setFeedback('')
    try {
      await action()
      setFeedback(success)
      await load(query)
      await openOperation(selected)
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : 'Não foi possível concluir.')
    } finally { setPending('') }
  }
  const changeStatus = (status: string, label: string) => {
    if (!selected) return
    let reason: string | undefined
    if (status === 'CANCELLED') {
      reason = window.prompt('Motivo do cancelamento (será registrado):')?.trim()
      if (!reason) return
    } else if (!window.confirm(`${label}?`)) return
    void runOperationAction(`status-${status}`, async () => setSelected(await updateOperationStatus(selected.id, status, reason)), 'Status da operação atualizado.')
  }
  return (
    <main className="module-page">
      <header>
        <p>AGENDA</p>
        <h1>Operações</h1>
        <span>
          Encontre operações reais, acompanhe sua inscrição e organize novos
          eventos.
        </span>
      </header>
      {feedback && (
        <p className="module-feedback" role="status">
          {feedback}
        </p>
      )}
      <section className="module-section">
        <div className="module-section-title">
          <div>
            <small>BUSCA E FILTROS</small>
            <h2>Agenda da comunidade</h2>
          </div>
          <button
            className="module-primary"
            onClick={() => setShowCreate(!showCreate)}
          >
            <Plus /> Criar operação
          </button>
        </div>
        <form
          className="module-search"
          onSubmit={(e) => {
            e.preventDefault()
            void load(query)
          }}
        >
          <Search />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={80}
            placeholder="Nome da operação ou campo"
          />
          <button>Pesquisar</button>
        </form>
      </section>
      {showCreate && (
        <section className="module-section">
          <div className="module-section-title">
            <div>
              <small>NOVA OPERAÇÃO</small>
              <h2>Informações essenciais</h2>
              <p>A capa aparecerá na agenda e no detalhe da operação.</p>
            </div>
            <Target />
          </div>
          <form className="module-form" onSubmit={submit}>
            <label className="operation-cover-field">
              <span>Imagem de capa</span>
              <input name="cover" type="file" accept="image/png,image/jpeg" />
              <small>PNG ou JPEG, até 2 MB e 2048 × 2048 pixels.</small>
            </label>
            <div className="form-grid">
              <label>
                <span>Nome</span>
                <input name="name" maxLength={120} required />
              </label>
              <label>
                <span>Campo</span>
                <select
                  name="fieldId"
                  required
                  onChange={(e) => void selectField(e.target.value)}
                >
                  <option value="">Selecione</option>
                  {fields.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name} · {f.city}/{f.stateCode}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Mapa</span>
                <select name="mapId">
                  <option value="">Sem mapa definido</option>
                  {maps.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Data</span>
                <input type="date" name="operationDate" required />
              </label>
              <label>
                <span>Apresentação</span>
                <input type="time" name="presentationTime" required />
              </label>
              <label>
                <span>Início</span>
                <input type="time" name="startTime" required />
              </label>
              <label>
                <span>Término</span>
                <input type="time" name="endTime" required />
              </label>
              <label>
                <span>Modalidade</span>
                <select name="modality">
                  {Object.entries(modalityLabels).map(([v, l]) => (
                    <option value={v} key={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Modalidade personalizada</span>
                <input name="customModality" maxLength={100} />
              </label>
              <label>
                <span>Imagem de capa</span>
                <input type="file" name="cover" accept="image/png,image/jpeg" onChange={event=>{const file=event.target.files?.[0];setCoverPreview(file?URL.createObjectURL(file):'')}} />
                {coverPreview&&<img className="operation-cover-preview" src={coverPreview} alt="Prévia da capa"/>}
              </label>
              <label>
                <span>Modelo de inscrição</span>
                <select name="registrationModel" value={registrationModel} onChange={event => setRegistrationModel(event.target.value === 'TEAMS' ? 'TEAMS' : 'SINGLE_LIST')}>
                  <option value="SINGLE_LIST">Lista única (padrão) · times definidos no dia</option>
                  <option value="TEAMS">Por equipes · o jogador escolhe a equipe</option>
                </select>
              </label>
              {registrationModel === 'TEAMS' && <label>
                <span>Quantidade de equipes</span>
                <input type="number" name="teamLimit" min={2} max={100} defaultValue={2} required />
              </label>}
              <label>
                <span>Vagas (opcional)</span>
                <input type="number" name="participantLimit" min={1} placeholder="Sem limite" />
              </label>
              <label>
                <span>Inscrição informada (R$)</span>
                <input
                  type="number"
                  name="registrationPrice"
                  min={0}
                  step="0.01"
                  defaultValue="0"
                />
              </label>
              <label>
                <span>Idade mínima</span>
                <input
                  type="number"
                  name="minimumAge"
                  min={12}
                  max={99}
                  defaultValue={18}
                />
              </label>
              <label>
                <span>Limite de FPS</span>
                <input type="number" name="fpsLimit" min={100} max={1000} />
              </label>
              <label>
                <span>Entrada</span>
                <select name="entryMode">
                  <option value="INDIVIDUAL">Individual</option>
                  <option value="TEAM">Equipe</option>
                  <option value="BOTH">Individual ou equipe</option>
                  <option value="INVITATION">Convite</option>
                </select>
              </label>
            </div>
            <label>
              <span>Descrição</span>
              <textarea name="description" maxLength={4000} required />
            </label>
            <label>
              <span>Regras</span>
              <textarea name="rules" maxLength={6000} />
            </label>
            <label>
              <span>Equipamentos obrigatórios</span>
              <textarea name="requiredEquipment" maxLength={2000} />
            </label>
            <label>
              <span>Formas de pagamento (informativo)</span>
              <input name="paymentMethods" maxLength={500} />
            </label>
            <label className="check-field">
              <input type="checkbox" name="approvalRequired" defaultChecked />{' '}
              Participação exige aprovação
            </label>
            <label className="check-field">
              <input type="checkbox" name="waitingListEnabled" defaultChecked />{' '}
              Permitir lista de espera
            </label>
            <button type="submit" className="module-primary" disabled={pending === 'create'}>
              {pending === 'create' ? 'Salvando…' : 'Salvar rascunho'}
            </button>
          </form>
        </section>
      )}
      <section className="module-section">
        <div className="module-section-title">
          <div>
            <small>OPERAÇÕES PUBLICADAS</small>
            <h2>
              {items.length
                ? `${items.length} encontradas`
                : 'Nenhuma operação encontrada'}
            </h2>
          </div>
          <CalendarDays />
        </div>
        {items.length === 0 ? (
          <div className="module-state">
            <p>Não há operações para os filtros selecionados.</p>
          </div>
        ) : (
          <div className="operation-list">
            {orderedItems.map((item) => (
              <button className="operation-list-row" key={item.id} type="button" onClick={() => void openOperation(item)}>
                <time dateTime={item.operationDate}>
                  <strong>{new Date(`${item.operationDate}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit' })}</strong>
                  <span>{new Date(`${item.operationDate}T12:00:00`).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase()}</span>
                </time>
                <span>
                  <b>{item.name}</b>
                  <small>{item.city} · {item.stateCode} · {item.presentationTime.slice(0, 5)}</small>
                </span>
              </button>
            ))}
          </div>
        )}
      </section>
      {selected && (
        <div className="classified-modal-backdrop" role="presentation" onClick={() => { setSelected(null); setOperationEditMode(false) }}>
          <section className="classified-modal operation-detail-modal" role="dialog" aria-modal="true" aria-label={`Detalhes de ${selected.name}`} onClick={(event) => event.stopPropagation()}>
            <button type="button" className="classified-modal-close" aria-label="Fechar" onClick={() => { setSelected(null); setOperationEditMode(false) }}><X /></button>
            {selected.managedByCurrentUser && <button type="button" className="module-secondary operation-edit-toggle" aria-pressed={operationEditMode} onClick={() => setOperationEditMode(active => !active)}><Pencil /> {operationEditMode ? 'Encerrar edição' : 'Editar operação'}</button>}
            {selected.hasCover ? <img className="operation-detail-cover" src={operationCoverUrl(selected.id,selected.coverVersion)} alt={`Capa de ${selected.name}`} />:<div className="operation-detail-cover operation-cover-placeholder"><Target/><span>Operador Zero</span></div>}
            <small>{statusLabels[selected.status] || selected.status}</small>
            <h2>{selected.name}</h2>
            <p>{selected.description}</p>
            {selected.briefing && <section className="operation-briefing"><small>BRIEFING DO JOGO</small><p>{selected.briefing}</p></section>}
            <div className="operation-roster-summary"><span><MapPin /> {selected.fieldName} · {selected.city}/{selected.stateCode}</span><span><Users /> {selected.participantCount}/{selected.participantLimit ?? 'sem limite'} participantes</span></div>
            {selected.managedByCurrentUser && (statusActions[selected.status] || CANCELLABLE_STATUSES.includes(selected.status)) && <div className="operation-status-actions" aria-label="Status da operação">
              {(statusActions[selected.status] || []).map(item => <button key={item.status} type="button" className="module-secondary" disabled={pending.startsWith('status-')} onClick={() => changeStatus(item.status, item.label)}>{item.label}</button>)}
              {CANCELLABLE_STATUSES.includes(selected.status) && <button type="button" className="module-danger" disabled={pending.startsWith('status-')} onClick={() => changeStatus('CANCELLED', 'Cancelar operação')}>Cancelar operação</button>}
            </div>}
            {selected.managedByCurrentUser && operationEditMode && <section className="operation-organizer-tools">
              <header><CalendarClock/><div><small>ORGANIZAÇÃO</small><h3>Editar data e briefing</h3><p>Remarque a operação ou atualize as instruções apresentadas aos jogadores.</p></div></header>
              <form className="module-form" onSubmit={updateManagedOperation} key={`${selected.id}-${selected.version ?? 0}`}>
                <div className="form-grid">
                  <label><span>Nova data</span><input name="operationDate" type="date" defaultValue={selected.operationDate} required/></label>
                  <label><span>Apresentação</span><input name="presentationTime" type="time" defaultValue={selected.presentationTime.slice(0,5)} required/></label>
                  <label><span>Início</span><input name="startTime" type="time" defaultValue={selected.startTime.slice(0,5)} required/></label>
                  <label><span>Término</span><input name="endTime" type="time" defaultValue={selected.endTime.slice(0,5)} required/></label>
                </div>
                <label><span>Briefing do jogo</span><textarea name="briefing" maxLength={12000} defaultValue={selected.briefing ?? ''} placeholder="Objetivos, contexto, cronograma, regras de respawn e orientações para os participantes."/></label>
                <label><span>Motivo da alteração</span><input name="reason" maxLength={500} required placeholder="Ex.: remarcação por condições climáticas"/></label>
                <button className="module-primary" disabled={pending === `edit-${selected.id}`}>{pending === `edit-${selected.id}` ? 'Salvando…' : 'Salvar alterações'}</button>
              </form>
              <div className="operation-cover-tools">
                <label className="module-secondary"><input type="file" accept="image/png,image/jpeg" hidden onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) void runOperationAction('cover', async () => setSelected(await uploadOperationCover(selected.id, file)), 'Capa atualizada.') }}/>{selected.hasCover ? 'Trocar capa' : 'Adicionar capa'}</label>
                {selected.hasCover && <button type="button" className="module-secondary" disabled={pending === 'cover'} onClick={() => { if (window.confirm('Remover a capa desta operação?')) void runOperationAction('cover', () => removeOperationCover(selected.id), 'Capa removida.') }}>Remover capa</button>}
              </div>
              <div className="operation-delete-zone">
                <label><span>Motivo para excluir</span><textarea value={deleteReason} onChange={event=>setDeleteReason(event.target.value)} maxLength={500} placeholder="Explique por que esta operação será removida."/></label>
                <button type="button" onClick={()=>void removeManagedOperation()} disabled={pending === `delete-${selected.id}`}><Trash2/>{pending === `delete-${selected.id}` ? 'Excluindo…' : 'Excluir operação'}</button>
                <small>A exclusão remove a operação das telas, mas preserva auditoria e registros relacionados.</small>
              </div>
            </section>}
            {rosterError && <p className="module-feedback" role="alert">{rosterError}</p>}
            {selected.status !== 'DRAFT' && <OperationRegistrationPanel key={selected.id} operationId={selected.id} structure={structure} onChanged={() => void load(query)}/>}
            {structureError ? <p className="module-feedback" role="alert">{structureError} <button type="button" onClick={() => void openOperation(selected)}>Tentar novamente</button></p> : <OperationCommandCenter operation={selected} initialStructure={structure} editMode={operationEditMode} onPublish={publish} participants={roster?.participants ?? []} onRosterChanged={() => void openOperation(selected, true)}/>}
          </section>
        </div>
      )}
    </main>
  )
}

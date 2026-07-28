import { CalendarDays, MapPin, Plus, Search, Target, Users } from 'lucide-react'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import {
  cancelOperationParticipation,
  createOperation,
  getFields,
  getMaps,
  getOperations,
  Operation,
  publishOperation,
  requestOperationParticipation,
  VenueField,
  VenueMap,
} from './api'

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

export function OperationsPage() {
  const [items, setItems] = useState<Operation[]>([]),
    [fields, setFields] = useState<VenueField[]>([]),
    [maps, setMaps] = useState<VenueMap[]>([]),
    [query, setQuery] = useState(''),
    [showCreate, setShowCreate] = useState(false),
    [feedback, setFeedback] = useState(''),
    [pending, setPending] = useState('')
  const load = useCallback(async (q = '') => {
    try {
      setItems((await getOperations(q)).items)
      setFields((await getFields()).items)
    } catch {
      setFeedback('Não foi possível carregar as operações.')
    }
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  const selectField = async (id: string) =>
    setMaps(id ? (await getMaps(id)).items : [])
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const submitter = (e.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null
    const publishNow = submitter?.value === 'publish'
    const d = new FormData(e.currentTarget)
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
        participantLimit: Number(d.get('participantLimit')),
        teamLimit: d.get('teamLimit') ? Number(d.get('teamLimit')) : null,
        registrationPrice: Number(d.get('registrationPrice') || 0),
        paymentMethods: d.get('paymentMethods'),
        minimumAge: Number(d.get('minimumAge')),
        requiredEquipment: d.get('requiredEquipment'),
        fpsLimit: d.get('fpsLimit') ? Number(d.get('fpsLimit')) : null,
        entryMode: d.get('entryMode'),
        approvalRequired: d.get('approvalRequired') === 'on',
        waitingListEnabled: d.get('waitingListEnabled') === 'on',
      })
      if (publishNow) await publishOperation(created.id)
      setFeedback(publishNow ? 'Operação publicada com inscrições abertas.' : 'Operação salva como rascunho.')
      setShowCreate(false)
      await load('')
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
      await publishOperation(item.id)
      setFeedback('Operação publicada com inscrições abertas.')
      await load(query)
    } catch (err) {
      setFeedback(err instanceof Error ? err.message : 'Não foi possível publicar a operação.')
    } finally {
      setPending('')
    }
  }
  const participate = async (item: Operation, cancel = false) => {
    setPending(item.id)
    try {
      if (cancel) await cancelOperationParticipation(item.id)
      else await requestOperationParticipation(item.id)
      setFeedback(
        cancel ? 'Participação cancelada.' : 'Solicitação registrada.',
      )
      await load(query)
    } catch (err) {
      setFeedback(
        err instanceof Error ? err.message : 'Não foi possível concluir.',
      )
    } finally {
      setPending('')
    }
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
              <p>
                Imagens serão habilitadas quando o armazenamento seguro estiver
                disponível.
              </p>
            </div>
            <Target />
          </div>
          <form className="module-form" onSubmit={submit}>
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
                <span>Vagas</span>
                <input type="number" name="participantLimit" min={1} required />
              </label>
              <label>
                <span>Limite de equipes</span>
                <input type="number" name="teamLimit" min={1} />
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
            <button type="submit" value="draft" className="module-secondary" disabled={pending === 'create'}>
              Salvar rascunho
            </button>
            <button type="submit" value="publish" className="module-primary" disabled={pending === 'create'}>
              {pending === 'create' ? 'Salvando…' : 'Publicar operação'}
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
            {items.map((item) => (
              <article key={item.id}>
                <div>
                  <small>{statusLabels[item.status] || item.status}</small>
                  <h3>{item.name}</h3>
                  <p>
                    <MapPin /> {item.fieldName} · {item.city}/{item.stateCode}
                  </p>
                  <p>
                    <CalendarDays />{' '}
                    {new Date(
                      `${item.operationDate}T12:00:00`,
                    ).toLocaleDateString('pt-BR')}{' '}
                    · {item.presentationTime.slice(0, 5)}
                  </p>
                  <p>
                    <Users /> {item.participantCount}/{item.participantLimit}{' '}
                    participantes
                  </p>
                </div>
                <div>
                  <strong>
                    {modalityLabels[item.modality] || item.modality}
                  </strong>
                  {item.registrationPrice > 0 && (
                    <span>
                      R${' '}
                      {Number(item.registrationPrice)
                        .toFixed(2)
                        .replace('.', ',')}
                    </span>
                  )}
                  {item.managedByCurrentUser && item.status === 'DRAFT' ? (
                    <button className="module-primary" disabled={pending === item.id} onClick={() => void publish(item)}>
                      {pending === item.id ? 'Publicando…' : 'Publicar operação'}
                    </button>
                  ) : item.managedByCurrentUser ? (
                    <span>Você organiza esta operação</span>
                  ) : item.participantStatus &&
                  item.participantStatus !== 'CANCELLED' ? (
                    <button
                      disabled={pending === item.id}
                      onClick={() => void participate(item, true)}
                    >
                      Cancelar participação
                    </button>
                  ) : (
                    <button
                      disabled={pending === item.id}
                      onClick={() => void participate(item)}
                    >
                      Solicitar participação
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}

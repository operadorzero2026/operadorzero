import { useEffect, useId, useState } from 'react'

type City = { id: number; nome: string }

const BRAZIL_STATES = [
  ['AC', 'Acre'], ['AL', 'Alagoas'], ['AP', 'Amapá'], ['AM', 'Amazonas'],
  ['BA', 'Bahia'], ['CE', 'Ceará'], ['DF', 'Distrito Federal'], ['ES', 'Espírito Santo'],
  ['GO', 'Goiás'], ['MA', 'Maranhão'], ['MT', 'Mato Grosso'], ['MS', 'Mato Grosso do Sul'],
  ['MG', 'Minas Gerais'], ['PA', 'Pará'], ['PB', 'Paraíba'], ['PR', 'Paraná'],
  ['PE', 'Pernambuco'], ['PI', 'Piauí'], ['RJ', 'Rio de Janeiro'], ['RN', 'Rio Grande do Norte'],
  ['RS', 'Rio Grande do Sul'], ['RO', 'Rondônia'], ['RR', 'Roraima'], ['SC', 'Santa Catarina'],
  ['SP', 'São Paulo'], ['SE', 'Sergipe'], ['TO', 'Tocantins'],
] as const

const cityCache = new Map<string, City[]>()

type BrazilLocationFieldsProps = {
  defaultState?: string
  defaultCity?: string
  required?: boolean
  namePrefix?: string
}

export function BrazilLocationFields({
  defaultState = '',
  defaultCity = '',
  required = true,
  namePrefix = 'location',
}: BrazilLocationFieldsProps) {
  const id = useId()
  const [state, setState] = useState(defaultState)
  const [city, setCity] = useState(defaultCity)
  const [cities, setCities] = useState<City[]>([])
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!state) {
      setCities([])
      setCity('')
      setStatus('idle')
      return
    }

    const cached = cityCache.get(state)
    if (cached) {
      setCities(cached)
      setCity(current => cached.some(item => item.nome === current) ? current : '')
      setStatus('ready')
      return
    }

    const controller = new AbortController()
    setStatus('loading')
    fetch(`https://servicodados.ibge.gov.br/api/v1/localidades/estados/${state}/municipios?orderBy=nome`, { signal: controller.signal })
      .then(response => {
        if (!response.ok) throw new Error('Não foi possível consultar as cidades.')
        return response.json() as Promise<City[]>
      })
      .then(data => {
        const normalized = data.map(({ id: cityId, nome }) => ({ id: cityId, nome }))
        cityCache.set(state, normalized)
        setCities(normalized)
        setCity(current => normalized.some(item => item.nome === current) ? current : '')
        setStatus('ready')
      })
      .catch(error => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setCities([])
        setCity('')
        setStatus('error')
      })

    return () => controller.abort()
  }, [state, attempt])

  return <>
    <label htmlFor={`${id}-state`}>
      <span>Estado</span>
      <select id={`${id}-state`} name={`${namePrefix}State`} required={required} value={state} onChange={event => { setState(event.target.value); setCity('') }}>
        <option value="">Selecione o estado</option>
        {BRAZIL_STATES.map(([uf, name]) => <option value={uf} key={uf}>{name} ({uf})</option>)}
      </select>
    </label>
    <label htmlFor={`${id}-city`}>
      <span>Cidade</span>
      <select id={`${id}-city`} name={`${namePrefix}City`} required={required} value={city} disabled={!state || status !== 'ready'} aria-busy={status === 'loading'} onChange={event => setCity(event.target.value)}>
        <option value="">{!state ? 'Escolha primeiro o estado' : status === 'loading' ? 'Carregando cidades…' : status === 'error' ? 'Cidades indisponíveis' : 'Selecione a cidade'}</option>
        {cities.map(item => <option value={item.nome} key={item.id}>{item.nome}</option>)}
      </select>
      {status === 'error' && <small>Falha ao consultar o IBGE. <button type="button" className="location-retry" onClick={() => setAttempt(value => value + 1)}>Tentar novamente</button></small>}
    </label>
  </>
}


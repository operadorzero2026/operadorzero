import { Medal, Trophy } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getHonorBoard, mediaUrl, type HonorBoard as Board } from '../api'

const range = (board: Board) => {
  const format = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
  return `${format(board.weekStart)} a ${format(board.weekEnd)}`
}
const thumbnail = (entry: Board['items'][number]) => {
  const media = entry.post.media[0]
  return mediaUrl(media?.posterUrl ?? media?.images[0]?.url)
}

/** Quadro de Honra: os posts mais condecorados da semana (a atual ou a que acabou). */
export function HonorBoardPage({ onOpenPost }: { onOpenPost: (postId: string) => void }) {
  const [previous, setPrevious] = useState(false)
  const [board, setBoard] = useState<Board | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setBoard(null); setError('')
    getHonorBoard(previous).then(result => { if (active) setBoard(result) })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível carregar o Quadro de Honra.') })
    return () => { active = false }
  }, [previous])
  return <main className="module-page honor-page"><header><p>CONDECORAÇÕES</p><h1>Quadro de Honra</h1><span>Os posts que mais receberam condecorações dos operadores.</span></header>
    <div className="create-segmented" role="radiogroup" aria-label="Semana">
      <button type="button" role="radio" aria-checked={!previous} className={!previous ? 'active' : ''} onClick={() => setPrevious(false)}>Esta semana</button>
      <button type="button" role="radio" aria-checked={previous} className={previous ? 'active' : ''} onClick={() => setPrevious(true)}>Semana passada</button>
    </div>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}
    {board && <p className="honor-range">{range(board)}</p>}
    {board && !board.items.length && <p className="module-empty">Nenhum post condecorado {previous ? 'na semana passada' : 'nesta semana ainda'}.</p>}
    <ol className="honor-list">{board?.items.map(entry => <li key={entry.post.id}>
      <button type="button" onClick={() => onOpenPost(entry.post.id)} aria-label={`${entry.position}º lugar, @${entry.post.author.username}, ${entry.decorations} condecorações`}>
        <span className={`honor-position honor-position--${Math.min(entry.position, 4)}`}>{entry.position}º</span>
        {thumbnail(entry) ? <img src={thumbnail(entry)} alt=""/> : <span className="honor-thumb"/>}
        <span className="honor-text"><b>@{entry.post.author.username}</b><small>{entry.post.caption?.slice(0, 90) ?? ''}</small></span>
        <span className="honor-count"><Medal/>{entry.decorations}</span>
      </button>
    </li>)}</ol>
  </main>
}

/** Destaque das segundas-feiras no topo do feed: o podio da semana que terminou. */
export function HonorBoardCard({ board, onOpen }: { board: Board; onOpen: () => void }) {
  return <article className="callup-card honor-card" aria-label="Quadro de Honra da semana passada">
    <div className="callup-inner">
      <p className="callup-eyebrow"><Trophy/> QUADRO DE HONRA</p>
      <h2>Os mais condecorados da semana</h2>
      <p className="honor-range">{range(board)}</p>
      <ol className="honor-podium">{board.items.slice(0, 3).map(entry => <li key={entry.post.id}>
        <span className={`honor-position honor-position--${entry.position}`}>{entry.position}º</span>
        {thumbnail(entry) ? <img src={thumbnail(entry)} alt=""/> : <span className="honor-thumb"/>}
        <b>@{entry.post.author.username}</b>
        <span className="honor-count"><Medal/>{entry.decorations}</span>
      </li>)}</ol>
      <button type="button" className="callup-enlist" onClick={onOpen}>VER QUADRO DE HONRA</button>
    </div>
  </article>
}

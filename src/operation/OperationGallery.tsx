import { Images, Play, Unlink } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { getOperationGallery, getPublicOperationGallery, mediaUrl, unlinkGalleryPost, type OperationGallery as Gallery } from '../api'
import './operation-page.css'

/**
 * "Fotos e videos": publicacoes do feed marcadas com a operacao, inclusive depois do jogo. Cada uma abre o post original.
 * Logado, segue as regras do feed (bloqueios etc.); sem login, so posts compartilhaveis. O organizador pode desvincular.
 */
export function OperationGallery({ operationId, loggedIn = true }: { operationId: string; loggedIn?: boolean }) {
  const [data, setData] = useState<Gallery | null>(null)
  const [notice, setNotice] = useState('')
  const load = useCallback(() => (loggedIn ? getOperationGallery(operationId) : getPublicOperationGallery(operationId)).then(setData).catch(() => setData(null)),
    [operationId, loggedIn])
  useEffect(() => { void load() }, [load])
  if (!data) return null
  const unlink = async (postId: string) => {
    if (!window.confirm('Desvincular esta publicação da operação? O post continua no feed do autor.')) return
    try { await unlinkGalleryPost(operationId, postId); setNotice('Publicação desvinculada.'); await load() }
    catch (error) { setNotice(error instanceof Error ? error.message : 'Não foi possível desvincular.') }
  }
  return <section className="op-gallery" aria-label="Fotos e vídeos">
    <h4><Images/> Fotos e vídeos <small>{data.items.length}</small></h4>
    {data.items.length === 0 ? <p className="op-gallery-empty">Ainda não há publicações. Ao postar no feed, use “Marcar operação” para a foto ou vídeo aparecer aqui.</p>
      : <ul>{data.items.map(post => {
        const media = post.media[0]
        const thumb = media?.kind === 'VIDEO' ? media.posterUrl : media?.images.find(image => image.width >= 300)?.url ?? media?.images.at(-1)?.url
        return <li key={post.id}>
          <a href={`/post/${post.id}`} aria-label={`Abrir publicação de @${post.author.username}`}>
            {thumb ? <img src={mediaUrl(thumb)} alt="" loading="lazy"/> : <span className="op-gallery-blank"/>}
            {media?.kind === 'VIDEO' && <Play className="op-gallery-play"/>}
            <small>@{post.author.username}</small>
          </a>
          {data.manager && <button type="button" onClick={() => void unlink(post.id)} aria-label="Desvincular da operação" title="Desvincular"><Unlink/></button>}
        </li>
      })}</ul>}
    {notice && <p className="op-gallery-empty" role="status">{notice}</p>}
  </section>
}

import { FormEvent, ReactNode, useCallback, useDeferredValue, useEffect, useState } from 'react'
import {
  ArrowLeft, Bookmark, CheckCircle2, ChevronRight, Flag, ImagePlus, LockKeyhole, MessageCircle,
  MoreHorizontal, Plus, Search, Send, ShieldAlert, ShieldCheck, ThumbsUp, Trash2, UserRound, X,
} from 'lucide-react'
import {
  CommunityAuthor,
  CommunityCategory,
  CommunityComment,
  CommunityPost,
  CommunityPostSummary,
  CommunityReport,
  SessionUser,
  communityImageUrl,
  createCommunityComment,
  createCommunityPost,
  deleteCommunityComment,
  deleteCommunityPost,
  getCommunityAuthor,
  getCommunityCategories,
  getCommunityComments,
  getCommunityPost,
  getCommunityPosts,
  getCommunityReports,
  moderateCommunityComment,
  moderateCommunityPost,
  reportCommunityComment,
  reportCommunityPost,
  resolveCommunityReport,
  setCommunityCommentsLocked,
  toggleCommunityBookmark,
  toggleCommunityVote,
  uploadCommunityImage,
} from './api'

type CommunityProps = {
  path: string
  user: SessionUser | null
  onNavigate: (path: string) => void
  onRequireAuth: () => void
}

type LoadState = 'loading' | 'ready' | 'error'
type SortMode = 'RECENT' | 'MOST_COMMENTED' | 'TOP'

const sortOptions: Array<{ value: SortMode; label: string }> = [
  { value: 'RECENT', label: 'Mais recentes' },
  { value: 'MOST_COMMENTED', label: 'Mais comentadas' },
  { value: 'TOP', label: 'Mais votadas' },
]

const reportReasons = [
  ['SPAM', 'Spam'],
  ['HARASSMENT', 'Assédio ou ataque pessoal'],
  ['HATE', 'Discriminação ou ódio'],
  ['THREAT', 'Ameaça'],
  ['PERSONAL_DATA', 'Exposição de dados pessoais'],
  ['FRAUD', 'Fraude ou golpe'],
  ['ILLEGAL', 'Conteúdo ilegal'],
  ['OTHER', 'Outro motivo'],
]

function CommunityFrame({ children, user, onNavigate, onRequireAuth }: CommunityProps & { children: ReactNode }) {
  return <div className="oz-community-shell">
    <header className="oz-community-topbar">
      <button className="oz-community-brand" onClick={() => onNavigate('/')} aria-label="Voltar ao início">
        <span>Operador</span><strong>Zero</strong>
      </button>
      <nav aria-label="Navegação da Comunidade">
        <button onClick={() => onNavigate('/comunidade')}>Publicações</button>
        {user && <button onClick={() => onNavigate('/comunidade/nova')}>Criar publicação</button>}
        {user?.roles.some(role => role === 'ADMIN' || role === 'MODERATOR') &&
          <button onClick={() => onNavigate('/comunidade/moderacao')}>Moderação</button>}
      </nav>
      {user
        ? <button className="oz-community-account" onClick={() => onNavigate('/')}><span>{(user.callsign || user.displayName).charAt(0)}</span>{user.callsign || user.displayName}</button>
        : <button className="oz-community-login" onClick={onRequireAuth}>Entrar para participar</button>}
    </header>
    {children}
  </div>
}

function Avatar({ author, large = false }: { author: CommunityAuthor; large?: boolean }) {
  const name = author.callsign || author.displayName
  return <span className={`oz-community-avatar ${large ? 'large' : ''}`} aria-label={`Avatar de ${name}`}>
    {name.charAt(0).toUpperCase()}
  </span>
}

function relativeDate(value: string) {
  const date = new Date(value)
  const difference = Date.now() - date.getTime()
  const minutes = Math.max(0, Math.floor(difference / 60000))
  if (minutes < 1) return 'agora'
  if (minutes < 60) return `há ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `há ${hours} h`
  const days = Math.floor(hours / 24)
  if (days < 7) return `há ${days} d`
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

async function sharePost(post: Pick<CommunityPostSummary, 'id' | 'title'>) {
  const url = `${window.location.origin}/comunidade/publicacoes/${post.id}`
  if (navigator.share) {
    await navigator.share({ title: post.title, url })
  } else {
    await navigator.clipboard.writeText(url)
  }
}

function InteractionButton({ children, active, onClick, label }: {
  children: ReactNode
  active?: boolean
  onClick: () => void
  label: string
}) {
  return <button className={active ? 'active' : ''} onClick={event => { event.stopPropagation(); onClick() }} aria-label={label}>
    {children}
  </button>
}

function ReportDialog({ target, onClose }: {
  target: { type: 'post' | 'comment'; id: string }
  onClose: () => void
}) {
  const [reason, setReason] = useState('SPAM')
  const [details, setDetails] = useState('')
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setNotice('')
    try {
      if (target.type === 'post') await reportCommunityPost(target.id, { reason, details })
      else await reportCommunityComment(target.id, { reason, details })
      setNotice('Denúncia recebida. A moderação analisará o conteúdo.')
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Não foi possível enviar a denúncia.')
    } finally {
      setPending(false)
    }
  }

  return <div className="oz-community-dialog-backdrop" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="oz-community-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title">
      <button className="oz-community-dialog-close" onClick={onClose} aria-label="Fechar"><X /></button>
      <p className="oz-community-kicker">DENÚNCIA CONFIDENCIAL</p>
      <h2 id="report-title">Analisar conteúdo</h2>
      {notice
        ? <div className="oz-community-dialog-result"><CheckCircle2 /><p>{notice}</p><button onClick={onClose}>Concluir</button></div>
        : <form onSubmit={submit}>
          <label><span>Motivo</span><select value={reason} onChange={event => setReason(event.target.value)}>
            {reportReasons.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select></label>
          <label><span>Contexto adicional</span><textarea value={details} onChange={event => setDetails(event.target.value)} maxLength={1000} placeholder="Descreva objetivamente o problema." /></label>
          <p><LockKeyhole /> Sua identidade não será exibida ao autor.</p>
          <footer><button type="button" onClick={onClose}>Cancelar</button><button className="oz-community-primary" disabled={pending}>{pending ? 'Enviando…' : 'Enviar denúncia'}</button></footer>
        </form>}
    </section>
  </div>
}

function FeedView({ user, onNavigate, onRequireAuth }: Omit<CommunityProps, 'path'>) {
  const [categories, setCategories] = useState<CommunityCategory[]>([])
  const [posts, setPosts] = useState<CommunityPostSummary[]>([])
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const [category, setCategory] = useState('')
  const [sort, setSort] = useState<SortMode>('RECENT')
  const [state, setState] = useState<LoadState>('loading')
  const [error, setError] = useState('')
  const [total, setTotal] = useState(0)
  const [reportTarget, setReportTarget] = useState<{ type: 'post'; id: string } | null>(null)
  const [shareNotice, setShareNotice] = useState('')

  useEffect(() => {
    getCommunityCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    let active = true
    setState('loading')
    setError('')
    getCommunityPosts({ query: deferredQuery, category, sort })
      .then(response => {
        if (!active) return
        setPosts(response.items)
        setTotal(response.total)
        setState('ready')
      })
      .catch(caught => {
        if (!active) return
        setError(caught instanceof Error ? caught.message : 'Não foi possível carregar a comunidade.')
        setState('error')
      })
    return () => { active = false }
  }, [deferredQuery, category, sort])

  const requireUser = (action: () => void) => {
    if (!user) onRequireAuth()
    else action()
  }

  const updatePost = (id: string, change: Partial<CommunityPostSummary>) => {
    setPosts(current => current.map(post => post.id === id ? { ...post, ...change } : post))
  }

  const vote = (post: CommunityPostSummary) => requireUser(async () => {
    try {
      const result = await toggleCommunityVote(post.id)
      updatePost(post.id, { votedByCurrentUser: result.active, voteCount: result.count })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível registrar o voto.')
    }
  })

  const save = (post: CommunityPostSummary) => requireUser(async () => {
    try {
      const result = await toggleCommunityBookmark(post.id)
      updatePost(post.id, { savedByCurrentUser: result.active })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível salvar a publicação.')
    }
  })

  return <main className="oz-community-page">
    <section className="oz-community-hero">
      <div>
        <p className="oz-community-kicker">CONVERSE · APRENDA · CONTRIBUA</p>
        <h1>Comunidade<br /><em>Operador Zero.</em></h1>
        <span>Discussões reais do airsoft brasileiro, organizadas por assunto e conduzidas com respeito.</span>
      </div>
      <button className="oz-community-primary" onClick={() => requireUser(() => onNavigate('/comunidade/nova'))}><Plus /> Criar publicação</button>
    </section>

    <section className="oz-community-toolbar" aria-label="Filtros das publicações">
      <label><Search /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Pesquisar publicações ou operadores" /></label>
      <select value={sort} onChange={event => setSort(event.target.value as SortMode)} aria-label="Ordenar publicações">
        {sortOptions.map(option => <option value={option.value} key={option.value}>{option.label}</option>)}
      </select>
    </section>

    <div className="oz-community-workspace">
      <aside className="oz-community-categories">
        <p className="oz-community-kicker">CATEGORIAS</p>
        <button className={!category ? 'active' : ''} onClick={() => setCategory('')}>Todas</button>
        {categories.map(item => <button className={category === item.slug ? 'active' : ''} onClick={() => setCategory(item.slug)} key={item.id}>{item.name}</button>)}
      </aside>

      <section className="oz-community-feed">
        <header><div><p className="oz-community-kicker">PUBLICAÇÕES</p><h2>{category ? categories.find(item => item.slug === category)?.name : 'Todas as conversas'}</h2></div><span>{total} {total === 1 ? 'publicação' : 'publicações'}</span></header>
        {error && <p className="oz-community-alert" role="alert">{error}</p>}
        {shareNotice && <p className="oz-community-toast" role="status">{shareNotice}</p>}
        {state === 'loading' && <div className="oz-community-loading"><span /><span /><span /></div>}
        {state === 'ready' && posts.length === 0 && <div className="oz-community-empty"><MessageCircle /><h3>Nenhuma publicação encontrada</h3><p>Seja a primeira pessoa a iniciar uma conversa nesta categoria.</p><button onClick={() => requireUser(() => onNavigate('/comunidade/nova'))}>Criar publicação</button></div>}
        {posts.map(post => <article className="oz-community-post" key={post.id} onClick={() => onNavigate(`/comunidade/publicacoes/${post.id}`)}>
          <div className="oz-community-post-vote">
            <InteractionButton active={post.votedByCurrentUser} onClick={() => vote(post)} label="Votar na publicação"><ThumbsUp /><strong>{post.voteCount}</strong></InteractionButton>
          </div>
          <div className="oz-community-post-content">
            <div className="oz-community-post-meta">
              <button onClick={event => { event.stopPropagation(); onNavigate(`/comunidade/operadores/${post.author.username}`) }}><Avatar author={post.author} /><b>{post.author.callsign || post.author.displayName}</b></button>
              <span>{post.category.name}</span><time>{relativeDate(post.createdAt)}</time>
            </div>
            <h3>{post.title}</h3>
            <p>{post.description}</p>
            {post.coverImageId && <img src={communityImageUrl(post.coverImageId)} alt="" loading="lazy" />}
            <footer>
              <button><MessageCircle /> {post.commentCount} comentários</button>
              <InteractionButton active={post.savedByCurrentUser} onClick={() => save(post)} label="Salvar publicação"><Bookmark /> {post.savedByCurrentUser ? 'Salva' : 'Salvar'}</InteractionButton>
              <InteractionButton onClick={() => sharePost(post).then(() => setShareNotice('Link da publicação compartilhado.')).catch(() => setShareNotice('Não foi possível compartilhar.'))} label="Compartilhar publicação"><MoreHorizontal /> Compartilhar</InteractionButton>
              <InteractionButton onClick={() => requireUser(() => setReportTarget({ type: 'post', id: post.id }))} label="Denunciar publicação"><Flag /> Denunciar</InteractionButton>
            </footer>
          </div>
        </article>)}
      </section>
    </div>
    <button className="oz-community-fab" onClick={() => requireUser(() => onNavigate('/comunidade/nova'))} aria-label="Criar publicação"><Plus /></button>
    {reportTarget && <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />}
  </main>
}

function CreatePostView({ user, onNavigate, onRequireAuth }: Omit<CommunityProps, 'path'>) {
  const [categories, setCategories] = useState<CommunityCategory[]>([])
  const [categorySlug, setCategorySlug] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState('')
  const [idempotencyKey] = useState(() => crypto.randomUUID())

  useEffect(() => {
    getCommunityCategories().then(items => {
      setCategories(items)
      if (items.length) setCategorySlug(items[0].slug)
    }).catch(error => setNotice(error instanceof Error ? error.message : 'Não foi possível carregar as categorias.'))
  }, [])

  if (!user) return <main className="oz-community-page"><div className="oz-community-auth-required"><LockKeyhole /><h1>Entre para publicar</h1><p>Você pode ler a comunidade sem uma conta. Para criar conteúdo, entre com Google ou e-mail.</p><button className="oz-community-primary" onClick={onRequireAuth}>Entrar para continuar</button></div></main>

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setNotice('')
    try {
      const post = await createCommunityPost({ categorySlug, title, description, idempotencyKey })
      for (const file of files) await uploadCommunityImage(post.id, file)
      onNavigate(`/comunidade/publicacoes/${post.id}`)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Não foi possível publicar.')
      setPending(false)
    }
  }

  const selectFiles = (selected: FileList | null) => {
    const next = Array.from(selected || [])
    if (next.length > 4) { setNotice('Selecione no máximo quatro imagens.'); return }
    if (next.some(file => !['image/png', 'image/jpeg'].includes(file.type) || file.size > 2 * 1024 * 1024)) {
      setNotice('Use somente imagens PNG ou JPEG de até 2 MB cada.')
      return
    }
    setNotice('')
    setFiles(next)
  }

  return <main className="oz-community-page oz-community-editor-page">
    <button className="oz-community-back" onClick={() => onNavigate('/comunidade')}><ArrowLeft /> Voltar para a comunidade</button>
    <header><p className="oz-community-kicker">NOVA CONVERSA</p><h1>Criar publicação</h1><p>Compartilhe uma experiência, dúvida ou informação útil para a comunidade.</p></header>
    <form className="oz-community-editor" onSubmit={submit}>
      <label><span>Título</span><input required minLength={3} maxLength={160} value={title} onChange={event => setTitle(event.target.value)} placeholder="Um título claro ajuda outros operadores a encontrar sua publicação" /></label>
      <label><span>Categoria</span><select required value={categorySlug} onChange={event => setCategorySlug(event.target.value)}>
        {categories.map(category => <option value={category.slug} key={category.id}>{category.name}</option>)}
      </select></label>
      <label className="wide"><span>Descrição</span><textarea required maxLength={10000} value={description} onChange={event => setDescription(event.target.value)} placeholder="Escreva sua publicação. HTML não é renderizado." /></label>
      <label className="oz-community-upload wide">
        <span><ImagePlus /> Imagens opcionais</span>
        <input type="file" accept="image/png,image/jpeg" multiple onChange={event => selectFiles(event.target.files)} />
        <small>Até 4 imagens PNG ou JPEG, com no máximo 2 MB cada. Os arquivos são validados e reprocessados pelo servidor.</small>
        {files.length > 0 && <b>{files.length} {files.length === 1 ? 'imagem selecionada' : 'imagens selecionadas'}</b>}
      </label>
      <div className="oz-community-safety wide"><ShieldCheck /><p>Publique somente conteúdo relacionado ao airsoft. Não exponha dados pessoais, documentos ou localização exata de terceiros.</p></div>
      {notice && <p className="oz-community-alert wide" role="alert">{notice}</p>}
      <footer className="wide"><button type="button" onClick={() => onNavigate('/comunidade')}>Cancelar</button><button className="oz-community-primary" disabled={pending || !categorySlug}>{pending ? 'Publicando…' : <><Send /> Publicar</>}</button></footer>
    </form>
  </main>
}

function CommentThread({ comments, parentId, user, onReply, onReport, onDelete, onAuthor }: {
  comments: CommunityComment[]
  parentId: string | null
  user: SessionUser | null
  onReply: (comment: CommunityComment) => void
  onReport: (comment: CommunityComment) => void
  onDelete: (comment: CommunityComment) => void
  onAuthor: (author: CommunityAuthor) => void
}) {
  const children = comments.filter(comment => (comment.parentCommentId || null) === parentId)
  if (!children.length) return null
  return <div className={parentId ? 'oz-community-replies' : 'oz-community-comment-list'}>
    {children.map(comment => <article className="oz-community-comment" key={comment.id}>
      <Avatar author={comment.author} />
      <div>
        <header><button onClick={() => onAuthor(comment.author)}>{comment.author.callsign || comment.author.displayName}</button><time>{relativeDate(comment.createdAt)}</time></header>
        <p>{comment.description}</p>
        <footer>
          <button onClick={() => onReply(comment)}><MessageCircle /> Responder</button>
          <button onClick={() => onReport(comment)}><Flag /> Denunciar</button>
          {comment.canManage && <button onClick={() => onDelete(comment)}><Trash2 /> Excluir</button>}
        </footer>
        <CommentThread comments={comments} parentId={comment.id} user={user} onReply={onReply} onReport={onReport} onDelete={onDelete} onAuthor={onAuthor} />
      </div>
    </article>)}
  </div>
}

function PostDetailView({ postId, user, onNavigate, onRequireAuth }: Omit<CommunityProps, 'path'> & { postId: string }) {
  const [post, setPost] = useState<CommunityPost | null>(null)
  const [comments, setComments] = useState<CommunityComment[]>([])
  const [state, setState] = useState<LoadState>('loading')
  const [error, setError] = useState('')
  const [comment, setComment] = useState('')
  const [replyTo, setReplyTo] = useState<CommunityComment | null>(null)
  const [pending, setPending] = useState(false)
  const [reportTarget, setReportTarget] = useState<{ type: 'post' | 'comment'; id: string } | null>(null)
  const moderator = user?.roles.some(role => role === 'ADMIN' || role === 'MODERATOR') || false

  const load = useCallback(async () => {
    setState('loading')
    try {
      const [postResponse, commentResponse] = await Promise.all([getCommunityPost(postId), getCommunityComments(postId)])
      setPost(postResponse)
      setComments(commentResponse.items)
      setState('ready')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível carregar a publicação.')
      setState('error')
    }
  }, [postId])

  useEffect(() => { void load() }, [load])

  const requireUser = (action: () => void) => {
    if (!user) onRequireAuth()
    else action()
  }

  const submitComment = async (event: FormEvent) => {
    event.preventDefault()
    if (!user) { onRequireAuth(); return }
    if (!comment.trim() || pending) return
    setPending(true)
    setError('')
    try {
      await createCommunityComment(postId, {
        parentCommentId: replyTo?.id,
        description: comment,
        idempotencyKey: crypto.randomUUID(),
      })
      setComment('')
      setReplyTo(null)
      const response = await getCommunityComments(postId)
      setComments(response.items)
      setPost(current => current ? { ...current, commentCount: response.items.length } : current)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível publicar o comentário.')
    } finally {
      setPending(false)
    }
  }

  const vote = () => requireUser(async () => {
    try {
      const result = await toggleCommunityVote(postId)
      setPost(current => current ? { ...current, votedByCurrentUser: result.active, voteCount: result.count } : current)
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível registrar o voto.') }
  })

  const save = () => requireUser(async () => {
    try {
      const result = await toggleCommunityBookmark(postId)
      setPost(current => current ? { ...current, savedByCurrentUser: result.active } : current)
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível salvar a publicação.') }
  })

  const removeComment = async (target: CommunityComment) => {
    if (!window.confirm('Excluir este comentário?')) return
    try {
      await deleteCommunityComment(target.id)
      setComments(current => current.filter(item => item.id !== target.id && item.parentCommentId !== target.id))
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível excluir o comentário.') }
  }

  if (state === 'loading') return <main className="oz-community-page"><div className="oz-community-loading"><span /><span /><span /></div></main>
  if (state === 'error' || !post) return <main className="oz-community-page"><div className="oz-community-empty"><ShieldAlert /><h1>Publicação indisponível</h1><p>{error}</p><button onClick={() => onNavigate('/comunidade')}>Voltar ao feed</button></div></main>

  return <main className="oz-community-page oz-community-detail-page">
    <button className="oz-community-back" onClick={() => onNavigate('/comunidade')}><ArrowLeft /> Voltar para publicações</button>
    {error && <p className="oz-community-alert" role="alert">{error}</p>}
    <article className="oz-community-detail">
      <header>
        <span>{post.category.name}</span>
        <button onClick={() => onNavigate(`/comunidade/operadores/${post.author.username}`)}><Avatar author={post.author} /><b>{post.author.callsign || post.author.displayName}</b></button>
        <time>{relativeDate(post.createdAt)}</time>
      </header>
      <h1>{post.title}</h1>
      <p className="oz-community-detail-body">{post.description}</p>
      {post.imageIds.length > 0 && <div className="oz-community-gallery">{post.imageIds.map((id, index) => <img key={id} src={communityImageUrl(id)} alt={`Imagem ${index + 1} da publicação`} />)}</div>}
      <footer className="oz-community-actions">
        <button className={post.votedByCurrentUser ? 'active' : ''} onClick={vote}><ThumbsUp /> {post.voteCount} votos</button>
        <button className={post.savedByCurrentUser ? 'active' : ''} onClick={save}><Bookmark /> {post.savedByCurrentUser ? 'Salva' : 'Salvar'}</button>
        <button onClick={() => sharePost(post).catch(() => setError('Não foi possível compartilhar.'))}><MoreHorizontal /> Compartilhar</button>
        <button onClick={() => requireUser(() => setReportTarget({ type: 'post', id: post.id }))}><Flag /> Denunciar</button>
        {post.canManage && <button onClick={async () => {
          if (!window.confirm('Excluir esta publicação?')) return
          try { await deleteCommunityPost(post.id); onNavigate('/comunidade') }
          catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível excluir.') }
        }}><Trash2 /> Excluir</button>}
        {moderator && <button onClick={async () => {
          try { await moderateCommunityPost(post.id, 'SUSPENDED', 'Conteúdo suspenso para análise pela moderação.'); onNavigate('/comunidade') }
          catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível suspender.') }
        }}><ShieldAlert /> Suspender</button>}
        {moderator && <button onClick={async () => {
          try {
            await setCommunityCommentsLocked(post.id, !post.commentsLocked, 'Ação de moderação na conversa.')
            setPost(current => current ? { ...current, commentsLocked: !current.commentsLocked } : current)
          } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível alterar os comentários.') }
        }}><LockKeyhole /> {post.commentsLocked ? 'Liberar comentários' : 'Bloquear comentários'}</button>}
      </footer>
    </article>

    <section className="oz-community-comments">
      <header><div><p className="oz-community-kicker">CONVERSA</p><h2>{comments.length} {comments.length === 1 ? 'comentário' : 'comentários'}</h2></div>{post.commentsLocked && <span><LockKeyhole /> Comentários bloqueados</span>}</header>
      {!post.commentsLocked && <form onSubmit={submitComment}>
        {replyTo && <p>Respondendo a <b>{replyTo.author.callsign || replyTo.author.displayName}</b><button type="button" onClick={() => setReplyTo(null)}><X /></button></p>}
        <textarea value={comment} onFocus={() => !user && onRequireAuth()} onChange={event => setComment(event.target.value)} required maxLength={3000} placeholder={user ? 'Contribua com respeito e mantenha o assunto da conversa.' : 'Entre para comentar.'} />
        <button className="oz-community-primary" disabled={pending || !comment.trim()}>{pending ? 'Publicando…' : <><Send /> Comentar</>}</button>
      </form>}
      <CommentThread comments={comments} parentId={null} user={user}
        onReply={target => { if (!user) onRequireAuth(); else { setReplyTo(target); document.querySelector<HTMLTextAreaElement>('.oz-community-comments textarea')?.focus() } }}
        onReport={target => requireUser(() => setReportTarget({ type: 'comment', id: target.id }))}
        onDelete={removeComment}
        onAuthor={author => onNavigate(`/comunidade/operadores/${author.username}`)} />
    </section>
    {reportTarget && <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />}
  </main>
}

function AuthorView({ username, onNavigate }: { username: string; onNavigate: (path: string) => void }) {
  const [author, setAuthor] = useState<CommunityAuthor | null>(null)
  const [posts, setPosts] = useState<CommunityPostSummary[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([getCommunityAuthor(username), getCommunityPosts({ query: username, size: 50 })])
      .then(([profile, response]) => {
        if (!active) return
        setAuthor(profile)
        setPosts(response.items.filter(post => post.author.username.toLowerCase() === username.toLowerCase()))
      })
      .catch(caught => active && setError(caught instanceof Error ? caught.message : 'Operador não encontrado.'))
    return () => { active = false }
  }, [username])

  if (error) return <main className="oz-community-page"><div className="oz-community-empty"><UserRound /><h1>Perfil indisponível</h1><p>{error}</p><button onClick={() => onNavigate('/comunidade')}>Voltar à comunidade</button></div></main>
  if (!author) return <main className="oz-community-page"><div className="oz-community-loading"><span /><span /><span /></div></main>

  return <main className="oz-community-page oz-community-author-page">
    <button className="oz-community-back" onClick={() => onNavigate('/comunidade')}><ArrowLeft /> Voltar para a comunidade</button>
    <section className="oz-community-author-header"><Avatar author={author} large /><div><p className="oz-community-kicker">OPERADOR</p><h1>{author.callsign || author.displayName}</h1><span>@{author.username}{author.teamName ? ` · ${author.teamName}` : ''}{author.city ? ` · ${author.city}, ${author.stateCode}` : ''}</span></div></section>
    <section className="oz-community-author-posts"><header><h2>Publicações</h2><span>{posts.length}</span></header>
      {posts.length === 0 ? <p>Este operador ainda não publicou na comunidade.</p> : posts.map(post => <button key={post.id} onClick={() => onNavigate(`/comunidade/publicacoes/${post.id}`)}><span>{post.category.name}</span><b>{post.title}</b><ChevronRight /></button>)}
    </section>
  </main>
}

function ModerationView({ user, onNavigate }: { user: SessionUser | null; onNavigate: (path: string) => void }) {
  const [reports, setReports] = useState<CommunityReport[]>([])
  const [error, setError] = useState('')
  const moderator = user?.roles.some(role => role === 'ADMIN' || role === 'MODERATOR')

  const load = () => getCommunityReports().then(response => setReports(response.items)).catch(caught => setError(caught instanceof Error ? caught.message : 'Não foi possível carregar a fila.'))
  useEffect(() => {
    if (!moderator) return
    let active = true
    getCommunityReports()
      .then(response => active && setReports(response.items))
      .catch(caught => active && setError(caught instanceof Error ? caught.message : 'Não foi possível carregar a fila.'))
    return () => { active = false }
  }, [moderator])

  if (!moderator) return <main className="oz-community-page"><div className="oz-community-auth-required"><ShieldAlert /><h1>Acesso restrito</h1><p>Esta área está disponível somente para administradores e moderadores.</p><button onClick={() => onNavigate('/comunidade')}>Voltar à comunidade</button></div></main>

  const resolve = async (report: CommunityReport, action: 'DISMISSED' | 'ACTIONED') => {
    try {
      if (action === 'ACTIONED') {
        if (report.targetType === 'POST') await moderateCommunityPost(report.targetId, 'SUSPENDED', 'Conteúdo suspenso após denúncia.')
        else await moderateCommunityComment(report.targetId, 'SUSPENDED', 'Conteúdo suspenso após denúncia.')
      }
      await resolveCommunityReport(report.id, action, action === 'ACTIONED' ? 'Conteúdo suspenso para análise.' : 'Denúncia analisada e arquivada.')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível concluir a análise.') }
  }

  return <main className="oz-community-page oz-community-moderation">
    <button className="oz-community-back" onClick={() => onNavigate('/comunidade')}><ArrowLeft /> Voltar para a comunidade</button>
    <header><p className="oz-community-kicker">ACESSO ADMINISTRATIVO</p><h1>Moderação</h1><p>Analise denúncias e aplique medidas com auditoria no servidor.</p></header>
    {error && <p className="oz-community-alert">{error}</p>}
    <section><header><h2>Fila de denúncias</h2><span>{reports.filter(report => report.status === 'OPEN').length} abertas</span></header>
      {reports.length === 0 && <div className="oz-community-empty"><ShieldCheck /><h3>Fila vazia</h3><p>Nenhuma denúncia aguarda análise.</p></div>}
      {reports.map(report => <article key={report.id}>
        <span>{report.reason}</span>
        <div><small>{report.targetType === 'POST' ? 'PUBLICAÇÃO' : 'COMENTÁRIO'} · @{report.reporterUsername} · {relativeDate(report.createdAt)}</small><h3>{report.targetTitle}</h3>{report.details && <p>{report.details}</p>}</div>
        <footer>
          <button onClick={() => onNavigate(report.targetType === 'POST' ? `/comunidade/publicacoes/${report.targetId}` : '/comunidade')}>Abrir conteúdo</button>
          {report.status === 'OPEN' && <button onClick={() => resolve(report, 'DISMISSED')}>Arquivar</button>}
          {report.status === 'OPEN' && <button className="danger" onClick={() => resolve(report, 'ACTIONED')}>Suspender conteúdo</button>}
        </footer>
      </article>)}
    </section>
  </main>
}

export default function CommunityPage(props: CommunityProps) {
  const postMatch = props.path.match(/^\/comunidade\/publicacoes\/([0-9a-f-]+)$/i)
  const authorMatch = props.path.match(/^\/comunidade\/operadores\/([^/]+)$/)
  let content: ReactNode
  if (props.path === '/comunidade/nova') content = <CreatePostView {...props} />
  else if (props.path === '/comunidade/moderacao') content = <ModerationView user={props.user} onNavigate={props.onNavigate} />
  else if (postMatch) content = <PostDetailView {...props} postId={postMatch[1]} />
  else if (authorMatch) content = <AuthorView username={decodeURIComponent(authorMatch[1])} onNavigate={props.onNavigate} />
  else content = <FeedView {...props} />

  return <CommunityFrame {...props}>{content}</CommunityFrame>
}

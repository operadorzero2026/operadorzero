import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [app, list, item, nav, api, css] = await Promise.all([
  readFile('src/App.tsx', 'utf8'),
  readFile('src/feed/FeedList.tsx', 'utf8'),
  readFile('src/feed/FeedItem.tsx', 'utf8'),
  readFile('src/feed/AppNavigation.tsx', 'utf8'),
  readFile('src/api.ts', 'utf8'),
  readFile('src/feed/feed.css', 'utf8'),
])

// Pos-login: "/" logado vai para /feed; /feed deslogado abre o login.
assert.match(app, /currentUser && currentPath === '\/'\) \{ window\.history\.replaceState\(\{\}, '', '\/feed'\)/)
assert.match(app, /!currentUser && currentPath === '\/feed'\) setAuthMode/)

// Barra inferior com os cinco destinos, na ordem pedida.
const order = ["'Feed', 'Feed'", "'Explorar', 'Explorar'", 'feed-create-button', "'Jogos', 'Jogos'", "'Perfil', 'Perfil'"].map(token => nav.indexOf(token))
assert.ok(order.every(index => index > 0) && order.every((index, i) => i === 0 || index > order[i - 1]), 'Ordem da barra inferior: Feed, Explorar, +, Jogos, Perfil.')

// Virtualizacao: so os vizinhos do post visivel ficam montados.
const radius = Number(list.match(/const MOUNT_RADIUS = (\d+)/)?.[1])
assert.ok(radius >= 1 && radius <= 3, 'MOUNT_RADIUS deve manter poucos posts montados.')
assert.match(list, /Math\.abs\(index - active\) <= MOUNT_RADIUS/)

// Video: mudo por padrao, inline, em loop, sem baixar quando esta longe.
assert.match(list, /useState\(true\)/, 'O feed comeca mudo para permitir autoplay.')
assert.match(item, /muted=\{muted\} loop playsInline/)
const source = await readFile('src/feed/useVideoSource.ts', 'utf8')
assert.match(source, /const loading = mode !== 'idle'/, 'Video longe da tela nao baixa nada.')
assert.match(source, /canPlayType\('application\/vnd\.apple\.mpegurl'\)/, 'Safari usa HLS nativo.')
assert.match(source, /import\('hls\.js'\)/, 'hls.js so e carregado quando ha video.')
assert.match(source, /data\.fatal\) \{ hls\?\.destroy\(\); hls = null; fallbackToMp4\(\)/, 'Falha do HLS cai para o MP4.')

// Midia fora de 9:16 nao e cortada: fundo desfocado + object-fit contain.
assert.match(css, /\.feed-media-content\{[^}]*object-fit:contain/)
assert.match(item, /feed-media-backdrop/)

// Paginacao por cursor e acessibilidade basica.
assert.match(api, /\/api\/feed\?tab=\$\{tab\}/)
assert.match(item, /aria-label=\{`Na mira, /)
assert.match(item, /alt=\{alt\}/)
for (const fictional of ['[DEV]', 'dev_alfa']) assert.ok(!app.includes(fictional) && !list.includes(fictional), 'Dados de seed nao podem ir para o codigo do site.')

// Recons: barra some ao rolar, radar dourado quando ha nao visto, visualizador com segurar/arrastar e "Recon encerrado".
const [bar, viewer, page, create, reconCss] = await Promise.all(['src/feed/ReconBar.tsx', 'src/feed/ReconViewer.tsx', 'src/feed/FeedPage.tsx', 'src/feed/CreateFlow.tsx', 'src/feed/feed.css'].map(file => readFile(file, 'utf8')))
assert.match(page, /<ReconBar hidden=\{scrolled\}/, 'A barra de Recons some quando o feed rola.')
assert.match(bar, /recon-ring--unseen/)
assert.match(reconCss, /\.recon-ring--unseen::before\{[^}]*conic-gradient[^}]*animation:recon-radar/, 'Radar dourado girando para Recon nao visto.')
assert.match(reconCss, /\.recon-ring--seen::before\{background:#5d6057\}/, 'Aro cinza para Recon ja visto.')
assert.match(viewer, /const HOLD_MS = \d+/, 'Segurar pausa.')
assert.match(viewer, /const CLOSE_DRAG_PX = \d+/, 'Arrastar para baixo fecha.')
assert.match(viewer, /bounds\.width \* 0\.3\) prev\(\); else next\(\)/, 'Toque a esquerda volta, a direita avanca.')
assert.match(viewer, /Recon encerrado/)
assert.match(viewer, /`Recon de @\$\{data\.username\}`/)
assert.match(create, /MAX_VIDEO_S = \{ POST: 90, RECON: 30 \}/, 'Video de Recon ate 30 s.')

console.log('Feed validado: redirecionamento, navegacao, virtualizacao, autoplay mudo, midia sem corte, cursor e Recons.')

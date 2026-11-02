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
assert.match(item, /src=\{mode === 'idle' \? undefined/)

// Midia fora de 9:16 nao e cortada: fundo desfocado + object-fit contain.
assert.match(css, /\.feed-media-content\{[^}]*object-fit:contain/)
assert.match(item, /feed-media-backdrop/)

// Paginacao por cursor e acessibilidade basica.
assert.match(api, /\/api\/feed\?tab=\$\{tab\}/)
assert.match(item, /aria-label=\{`Na mira, /)
assert.match(item, /alt=\{alt\}/)
for (const fictional of ['[DEV]', 'dev_alfa']) assert.ok(!app.includes(fictional) && !list.includes(fictional), 'Dados de seed nao podem ir para o codigo do site.')

console.log('Feed validado: redirecionamento, navegacao, virtualizacao, autoplay mudo, midia sem corte e cursor.')

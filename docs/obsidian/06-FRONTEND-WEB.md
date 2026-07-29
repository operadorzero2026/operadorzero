# Frontend web

## Central responsiva de Operações — 2026-07-29

O detalhe de [[09-OPERACOES]] carrega estrutura e comunicação reais da API. Exibe capa ou fallback, tamanho, ocupação, times, esquadrões e chats; atualiza o canal ativo por polling de cinco segundos. O cadastro mostra prévia raster e exige tamanho do jogo. A grade se reduz para uma coluna em telas móveis.

## Capa das operações — 2026-07-28

O formulário existente de criação aceita uma capa opcional e informa limites antes do envio. Capas persistidas aparecem com recorte responsivo na agenda e como imagem principal no modal de detalhe. Operações sem capa preservam o layout textual existente, sem imagem simulada.

## Detalhe e times da operação — 2026-07-28

Os cartões de operações abrem um detalhe com campo, vagas, times e participantes. O operador escolhe um time por controle explícito antes de solicitar inscrição; depois de inscrito, a tela informa o time atual. A interface não calcula vagas nem altera composição localmente: recarrega o roster da API conforme [[09-OPERACOES]].

## Publicação de operações — 2026-07-28

O formulário de operação oferece ações separadas para salvar rascunho e publicar com inscrições abertas. Rascunhos próprios permanecem na listagem com ação de publicação posterior; organizadores não recebem botão para participar da própria operação. O estado apresentado vem sempre da API, conforme [[09-OPERACOES]].

## Experiência do operador — 2026-07-28

`Meu Operador` possui campo de data para início no airsoft e apresenta o tempo ativo resumido. `OperatorSearch` exibe callsign, equipe ativa e experiência na mesma linha, preservando estados explícitos para perfil sem equipe ou sem data. O cálculo é recebido da API, conforme [[13-MEU-OPERADOR]].

## Foto do operador — 2026-07-28

`src/OperatorPage.tsx` permite cadastrar e trocar a foto do perfil. A interface aceita PNG/JPEG, informa limites, exibe retorno amigável e atualiza a imagem com versão para evitar cache antigo. A validação autoritativa e a transformação do arquivo permanecem no backend, conforme [[13-MEU-OPERADOR]] e [[05-SEGURANCA-E-PRIVACIDADE]].

## Linguagem da interface — 2026-07-27

A interface oficial não explica arquitetura, ambiente, banco, API, persistência, tokens ou decisões internas. Estados vazios informam somente a situação e a próxima orientação disponível. Avisos técnicos permanecem nos logs e na documentação; avisos jurídicos, erros de campo, sessão, acesso, moderação e staging continuam permitidos quando necessários.

Foram removidos do bundle oficial rótulos como `CONTEÚDO REAL`, `CONTA REAL`, `Exibindo somente dados reais`, `Autenticada pela API` e explicações repetidas sobre dados fictícios ou persistência. A redação pública agora descreve a jornada do operador. O inventário completo e a ordem de integração estão em [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]].

## Estado oficial sem conteúdo fictício

Desde 2026-07-23, a build oficial não importa os protótipos demonstrativos de Operações, Equipes, Ranking, Classificados, Comunidade, Destaques ou Conquistas. A landing apresenta estados vazios e o painel autenticado usa somente a identidade retornada por `/api/auth/session`.

Enquanto um módulo não possuir persistência e autorização reais no backend, ele deve mostrar `Nenhum registro` e não pode aceitar uma ação que exista apenas no estado React. Os protótipos antigos permanecem fora do bundle oficial como referência temporária de produto e não podem ser reativados sem contratos reais. Esta decisão está ligada a [[05-SEGURANCA-E-PRIVACIDADE]] e [[01-ESTADO-ATUAL-DO-PROJETO]].

A restauração inicial de sessão possui limite de sete segundos. Se o serviço gratuito estiver despertando ou indisponível, a requisição é cancelada e a landing pública continua normalmente. A tela de validação também oferece `Continuar no site`; nenhuma identidade é simulada quando esse caminho é usado.

As ações iniciadas pelo usuário verificam primeiro a readiness da API. No plano gratuito, o cliente repete tentativas limitadas por até três minutos, pois um cold start real levou aproximadamente 115 segundos. Depois da readiness, CSRF e a mutação mantêm timeouts curtos. O fluxo não cria sessão fictícia nem contorna CSRF, termos ou validações do backend.

## Localizacao brasileira dependente

Os formularios de Classificados, propostas, Operacoes, Minha Equipe e Meu Operador reutilizam `BrazilLocationFields`. O usuario escolhe primeiro um dos 27 estados; somente depois a cidade e habilitada com os municipios da UF retornados pela API oficial de localidades do IBGE. A troca de estado limpa a cidade anterior, ha cache por sessao, estado de carregamento e tentativa novamente em caso de falha. Em producao, a API do Operador Zero tambem deve validar a combinacao UF/municipio, sem confiar apenas no frontend.

## Destaques da comunidade

[[15-DESTAQUES-DA-COMUNIDADE]] apresenta curadoria gratuita na Visão Geral e uma administração demonstrativa com tipo, região, período, posição, motivo, responsável, ativação, pausa e encerramento. Nenhum usuário pode comprar prioridade e as alterações não são persistidas.

## Comunidade demonstrativa

[[14-COMUNIDADE]] está no menu autenticado com feed, categorias, filtros, busca, criação condicionada a aceite, pré-análise local representativa, publicação oficial/sensível, comentários, votos positivos, salvos, denúncia, regras, atividade e painel de moderação demonstrativo. Nada é persistido ou autorizado no cliente.

No celular, a Comunidade usa hero reduzido, apenas uma faixa horizontal de filtros, categorias removidas do fluxo principal e cards editoriais de uma coluna. O feed prioriza título, autor, utilidade e comentários; salvar e denunciar permanecem no detalhe da publicação.

## Meu Operador

A navegação autenticada inclui [[13-MEU-OPERADOR]] com nove abas, cabeçalho de identidade, edição, prévia de privacidade, equipamentos, equipe, ranking, conquistas, operações, reputação e conta. A barra superior possui busca global agrupada e responsiva; no mobile, o perfil aparece como destino principal da barra inferior.

## Ranking conectado

O menu autenticado abre uma área própria de ranking com escopos, filtros, tabela comparável, pendências, registro resumido/detalhado, confirmação/contestação e memória de cálculo. O protótipo usa dados locais e comunica explicitamente que não persiste. Contratos e estados estão em [[11-RANKING]].

## Conquistas conectadas

O menu autenticado abre [[12-CONQUISTAS-E-MEDALHAS]] com catálogo demonstrativo de 100 definições, progresso, busca, categorias, raridades, estados, conquista secreta, detalhe, compartilhamento seguro, seleção de três destaques e simulador administrativo. `Meu perfil` apresenta os destaques e direciona à coleção. Nenhuma concessão, regra ou escolha é persistida nesta etapa.

## Identidade

O produto usa o nome **Operador Zero**. A interface permanece escura, esportiva e tecnológica, com verde oliva como assinatura, alto contraste e duas famílias tipográficas. A identidade `public/operador-zero-identity.jpeg`, entregue pelo usuário, é a âncora visual do hero, autenticação, comunidade e destaques.

## Superfície atual

Landing pública responsiva em `src/App.tsx`, com hero, agenda, método de validação, comunidade e CTA. Inclui painel modal responsivo para entrar ou criar conta exclusivamente por e-mail e senha, confirmação da senha, confirmação obrigatória do e-mail e recuperação por e-mail. Menu desktop e mobile, `prefers-reduced-motion`, HTML sem renderização insegura e sem persistência local de credenciais.

O modal chama os contratos reais `/api/auth/login`, `/api/auth/register`, `/api/auth/resend-verification`, `/api/auth/password-recovery`, `/api/auth/password-reset`, `/api/auth/verify-email`, `/api/auth/session` e `/api/auth/logout`. Credenciais não são persistidas. Depois do login, a identidade exibida no cabeçalho vem da sessão validada pela API.

`VITE_AUTH_ENABLED=true` habilita a integração somente quando a API do ambiente estiver configurada. `VITE_APP_ENV=production` remove o banner de ambiente descartável. O domínio oficial possui canonical, Open Graph, Twitter Card, JSON-LD, `robots.txt`, sitemap e manifest próprios; previews públicos devem usar proteção de acesso ou política `noindex` da plataforma de hospedagem. Segredos e tokens nunca pertencem a variáveis `VITE_`.

A restauração de sessão ocorre em segundo plano, possui timeout curto e nunca substitui a landing por uma tela bloqueante. Ações iniciadas pelo usuário possuem envio único e retornam erro claro quando a API está indisponível, sem espera oculta de três minutos. O logout mantém a interface autenticada quando a API não consegue revogar a sessão, evitando confirmação falsa de saída.

A landing oficial usa o posicionamento “O airsoft brasileiro em um só lugar”, mantém conteúdo real e estados vazios, apresenta a ação de cadastro na primeira tela móvel e inclui uma seção institucional sobre gratuidade, privacidade, regras e segurança. A implementação e os riscos de produção estão registrados em [[17-DIAGNOSTICO-E-PLANO-DE-PRODUCAO-2026-07-27]].

Na primeira experiência pública, `Entrar` é a ação principal no cabeçalho, menu móvel e hero. `Criar perfil gratuito` permanece imediatamente disponível como ação secundária. As seções posteriores de convite continuam conduzindo ao cadastro.

## Experiência conectada demonstrativa

O componente `Dashboard` em `src/App.tsx` representa a visão inicial de um operador conectado. Exibe identidade/callsign, equipe, rankings municipal e estadual, pontuação, reputação, próxima operação, pendências, evolução recente e conquista. Possui sidebar no desktop, navegação inferior no mobile, menu de perfil e saída do modo demonstração.

A entrada no dashboard ocorre somente depois de `/api/auth/session` ou login real. O cabecalho mostra callsign, username e e-mail da conta conectada, enquanto os cards dos modulos continuam marcados como demonstrativos. A build nao contem usuario ou senha de teste e nao persiste tokens em Web Storage. A sessao depende do backend descrito em [[02-ARQUITETURA-E-STACK]].

## Convenções futuras

- Rotas públicas separadas das autenticadas e administrativas.
- Navegação mobile inferior apenas na aplicação autenticada.
- Estados de loading, vazio, erro e permissão explícitos.
- Formulários com validação compartilhada de contrato, sem confiar no cliente.
- Dados de demonstração jamais misturados com dados de produção.

## Ajustes de responsividade

A seção de método da landing agrupa título e descrição de cada etapa em um único bloco de conteúdo. A grade reserva colunas compactas para ícone e numeração e preserva uma coluna flexível para o texto, evitando que descrições sejam quebradas palavra por palavra em telas largas ou estreitas.

As superfícies após o login usam escala tipográfica ampliada: conteýo principal em 12–14 px, rótulos secundários em 10–12 px e controles de navegação em 12–14 px. No mobile, áreas de toque, alturas de linha, cards, avisos e navegação inferior recebem ajustes específicos para leitura sem zoom.

## Classificados demonstrativos

O menu do operador conectado inclui **Classificados**, com busca local, filtros por tipo, estado novo/usado, preço e localização, priorização da localização fictícia do perfil e favoritos em memória. A categoria especial de AEGs e marcadores aceita somente nome e acessórios, bloqueia fotos por política preventiva e solicita nota fiscal ou prova idônea de origem. O interesse abre um chat demonstrativo com aviso de não responsabilidade e orientações para negociação cordial e encontro seguro. Nenhum anúncio, arquivo, mensagem, proposta ou pagamento é persistido ou enviado. Veja [[08-CLASSIFICADOS]].

Ao escolher `Quero vender`, o formulário exige o estado `Novo` ou `Usado` e uma confirmação contextual de veracidade, propriedade e procedência. Para novo, também declara venda ocasional sem estoque comercial; para usado, declara transparência sobre conservação e avarias.

A modalidade [[CLASSIFICADOS-PROCURO-EQUIPAMENTO]] compartilha o mesmo mural e adiciona abas por tipo de publicação, selo `Procuro`, formulário específico, detalhes, proposta rápida, conversa vinculada e possível correspondência. Todos os dados são fictícios e permanecem somente no estado visual da demonstração.

## Operações demonstrativas

A navegação conectada abre [[09-OPERACOES]] com agenda cronológica, abas de situação, filtros, cards completos, detalhes por abas e assistente de criação em seis etapas. Todas as inscrições são gratuitas; o campo adicional aceita somente orientações não financeiras. Modalidade personalizada, regras, PDF, missões, times, esquadrões e inscrições são representações visuais; nada é persistido e nenhuma permissão é aplicada fora do frontend.

O assistente e os filtros distinguem `Modalidade da operação` de `Tipo de jogo`. Cards e detalhes exibem os dois valores e a indicação para iniciantes. A modalidade pode sugerir um modelo editável, aplicado integralmente, parcialmente ou ignorado; nunca substitui conteúdo de forma silenciosa.

## Minha Equipe demonstrativa

[[10-MINHA-EQUIPE]] possui estados com/sem equipe, painel Valkyrie Ops, integrantes, convites, operações, estatísticas, campo e histórico. A criação usa três etapas e reutiliza modalidades de Operações. Busca e troca de equipe são demonstrações explícitas: nenhum convite é enviado, nenhuma saída ocorre e nenhuma permissão é concedida.

## Operador e equipe funcionais em 2026-07-27

`src/OperatorPage.tsx`, `src/OperatorSearch.tsx` e `src/TeamPage.tsx` substituem os estados demonstrativos de Meu Operador e Minha Equipe. Todos carregam dados do backend, exibem loading/erro/vazio, aguardam a resposta antes de confirmar sucesso e não usam `localStorage`, `sessionStorage` ou fixtures.

A busca com debounce aparece na barra superior e no convite de equipe, retorna somente projeção pública autorizada e nunca e-mail. Os formulários reutilizam `BrazilLocationFields`, têm escala responsiva em `functional-modules.css` e mantêm foco, labels e áreas de toque adequadas. Veja [[13-MEU-OPERADOR]], [[10-MINHA-EQUIPE]] e [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]].

A camada HTTP recupera automaticamente, uma única vez, mutações recusadas por token CSRF desatualizado. A recuperação obtém um novo token e repete o mesmo payload; erros reais de autorização continuam sendo exibidos e nunca são convertidos em sucesso.

Erros de validação retornados pela API exibem o primeiro campo inválido com rótulo amigável. Na criação e edição de equipes, a sigla aceita letras, números, pontos e hífens, com orientação no próprio campo.

Minha Equipe permite selecionar uma logo PNG/JPEG durante a criação e substituí-la depois na área de identidade visual. A interface informa limites de 2 MB e 2048 × 2048 e exibe a imagem persistida; a validação de segurança continua obrigatoriamente no backend.

Veja [[01-ESTADO-ATUAL-DO-PROJETO]].

## Autenticação same-origin - 2026-07-28

`src/api.ts` usa a origem atual da página quando `VITE_API_URL` não é fornecida. Em produção, `vercel.json` encaminha as rotas de API, readiness e OAuth para o backend antes do fallback da SPA. O navegador não precisa aceitar cookies de terceiros para restaurar a sessão criada por e-mail/senha ou Google. Ambiente local continua podendo apontar diretamente para a API com `VITE_API_URL`.
## Visão geral com dados reais (2026-07-28)

A Visão Geral autenticada consulta dados reais de [[09-OPERACOES]], [[10-MINHA-EQUIPE]] e [[11-RANKING]]. Exibe até três operações futuras, priorizando cidade e estado do operador e, em seguida, a data mais próxima; o total de equipes ativas; e os três primeiros operadores do ranking geral. O cartão Comunidade contém somente título e subtítulo, conforme [[14-COMUNIDADE]].

## Rota funcional da Comunidade - 2026-07-28

O cartão Comunidade mantém título e subtítulo e agora é um controle clicável que navega para `/comunidade`. A mesma rota está disponível na landing, menu e rodapé. O feed é público e responsivo; tentativas de publicar, comentar, responder, votar, salvar ou denunciar sem sessão abrem o login real. Rotas filhas cobrem nova publicação, detalhe e perfil comunitário do autor, conforme [[14-COMUNIDADE]].

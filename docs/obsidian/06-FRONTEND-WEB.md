# Frontend web

## Localizacao brasileira dependente

Os formularios de Classificados, propostas, Operacoes, Minha Equipe e Meu Operador reutilizam `BrazilLocationFields`. O usuario escolhe primeiro um dos 27 estados; somente depois a cidade e habilitada com os municipios da UF retornados pela API oficial de localidades do IBGE. A troca de estado limpa a cidade anterior, ha cache por sessao, estado de carregamento e tentativa novamente em caso de falha. Em producao, a API do Operador Zero tambem deve validar a combinacao UF/municipio, sem confiar apenas no frontend.

## Financeiro e destaques patrocinados

[[15-FINANCEIRO-E-PUBLICIDADE]] adiciona painel financeiro responsivo, simulador de split, campanhas, checkout sandbox e blocos `Patrocinado` na Visão Geral. O conteúdo pago é visualmente separado do orgânico. Nenhum pagamento ou dado financeiro é transmitido pelo protótipo.

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

Landing pública responsiva em `src/App.tsx`, com hero, agenda demonstrativa, método de validação, ranking demonstrativo, ecossistema e CTA. Inclui painel modal responsivo para entrar ou criar conta por Google/e-mail e senha, além de recuperação por e-mail. Menu desktop e mobile, `prefers-reduced-motion`, HTML sem renderização insegura e sem persistência local de credenciais.

O modal atual chama os contratos `/api/auth/login`, `/api/auth/register`, `/api/auth/password-recovery` e `/oauth2/authorization/google` na URL pública `VITE_API_URL`. Credenciais não são persistidas. Enquanto esses endpoints não existirem no backend, o fluxo real apresenta indisponibilidade sem liberar o dashboard.

No staging, `VITE_AUTH_ENABLED=false` desabilita Google e o envio dos formulários, evitando simular autenticação inexistente. Toda build otimizada é tratada como staging por segurança, exceto quando `VITE_APP_ENV=production` for definido explicitamente; assim, o banner de dados descartáveis e `noindex`, `nofollow` e `noarchive` não dependem de configuração ausente. O modo demonstrativo continua compilado apenas em desenvolvimento local.

## Experiência conectada demonstrativa

O componente `Dashboard` em `src/App.tsx` representa a visão inicial de um operador conectado. Exibe identidade/callsign, equipe, rankings municipal e estadual, pontuação, reputação, próxima operação, pendências, evolução recente e conquista. Possui sidebar no desktop, navegação inferior no mobile, menu de perfil e saída do modo demonstração.

A entrada no dashboard fictício é separada do login real e só aparece quando `import.meta.env.DEV` é verdadeiro. O cabeçalho e o rodapé informam que o ambiente e seus dados são demonstrativos. A build de produção não contém usuário ou senha de teste; a sessão real permanece dependente do backend descrito em [[02-ARQUITETURA-E-STACK]].

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

A navegação conectada abre [[09-OPERACOES]] com agenda cronológica, abas de situação, filtros, cards completos, detalhes por abas e assistente de criação em seis etapas. Modalidade personalizada, regras, PDF, missões, times, esquadrões, custos e inscrições são somente representações visuais; nada é persistido e nenhuma permissão é aplicada fora do frontend.

O assistente e os filtros distinguem `Modalidade da operação` de `Tipo de jogo`. Cards e detalhes exibem os dois valores e a indicação para iniciantes. A modalidade pode sugerir um modelo editável, aplicado integralmente, parcialmente ou ignorado; nunca substitui conteúdo de forma silenciosa.

## Minha Equipe demonstrativa

[[10-MINHA-EQUIPE]] possui estados com/sem equipe, painel Valkyrie Ops, integrantes, convites, operações, estatísticas, campo e histórico. A criação usa três etapas e reutiliza modalidades de Operações. Busca e troca de equipe são demonstrações explícitas: nenhum convite é enviado, nenhuma saída ocorre e nenhuma permissão é concedida.

Veja [[01-ESTADO-ATUAL-DO-PROJETO]].

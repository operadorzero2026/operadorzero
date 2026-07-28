# Auditoria de telas e integração — 2026-07-27

Esta auditoria conecta [[01-ESTADO-ATUAL-DO-PROJETO]], [[04-MODULOS-E-FLUXOS]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[07-ROADMAP-MVP]] e [[16-ARQUITETURA-DE-PRODUCAO]]. Ela foi produzida antes da funcionalização dos módulos, conforme solicitado, e descreve o código real da branch `deploy/render-vercel`.

## Conclusão executiva

O produto oficial é atualmente uma SPA sem roteador. A landing usa âncoras e, depois do login, a navegação troca um estado React chamado `activeView`; atualizar a URL ou abrir diretamente um módulo não é possível. Cadastro, confirmação de e-mail, login, recuperação, sessão, Google OIDC e logout possuem contratos reais. Os módulos de negócio não possuem controllers, services, repositories ou endpoints.

Os protótipos antigos de Comunidade, Destaques e o catálogo de Conquistas ainda existem em arquivos desconectados de `src/main.tsx`. Eles não entram no bundle oficial, mas contêm arrays fixos, ações em memória e mensagens de demonstração. Operações, Minha Equipe, Ranking, Classificados e Meu Operador não possuem mais suas antigas telas completas no código carregado; aparecem apenas como estados vazios.

## Rotas e telas atuais

| Rota ou destino | Finalidade | Estado atual | Backend | Persistência | Botões | Classificação |
|---|---|---|---|---|---|---|
| `/` e `#inicio` | Landing pública | Conteúdo institucional estático | sessão em segundo plano | não aplicável | funcionais | Funcional |
| `#operacoes` | Agenda pública | estado vazio fixo | ausente | não | sem criação/listagem | Apenas visual |
| `#como-funciona` | Explicação do produto | texto técnico sobre implementação | ausente | não aplicável | não possui | Parcial |
| `#comunidade` | Apresentação pública | texto estático | ausente | não | não possui | Apenas visual |
| `#sobre`, `#termos`, `#privacidade`, `#regras`, `#seguranca` | Informações institucionais | trechos resumidos na landing | ausente | não aplicável | links funcionais | Parcial |
| modal Entrar/Criar conta | identidade | conectado à API | existente | PostgreSQL/Redis | funcionais | Funcional |
| modal Recuperar/Redefinir senha | recuperação | conectado à API | existente | PostgreSQL | funcionais | Funcional |
| Visão geral autenticada | orientação do operador | somente identidade e estados vazios | somente sessão | identidade | navegação funcional | Parcial |
| Operações | agenda e gestão | estado vazio | ausente | não | apenas voltar | Apenas visual |
| Minha equipe | equipe e convites | estado vazio | ausente | não | apenas voltar | Apenas visual |
| Rankings | classificação e desempenho | estado vazio | ausente | não | apenas voltar | Apenas visual |
| Classificados | anúncios e procuras | estado vazio | ausente | não | apenas voltar | Apenas visual |
| Comunidade | publicações e comentários | estado vazio no bundle oficial | ausente | não | apenas voltar | Apenas visual |
| Destaques | curadoria gratuita | estado vazio no bundle oficial | ausente | não | apenas voltar | Apenas visual |
| Conquistas | catálogo e progresso | estado vazio no bundle oficial | ausente | não | apenas voltar | Apenas visual |
| Meu Operador | perfil | mostra dados da sessão sem edição | somente sessão | parcial | sem edição | Parcial |

Não existem rotas `/operacoes`, `/equipes`, `/ranking`, `/comunidade`, `/classificados` ou `/operador/:username`. A Vercel entrega a SPA, mas a aplicação não interpreta esses caminhos.

## Mensagens técnicas exibidas ao usuário

Ocorrências confirmadas no bundle oficial antes da limpeza:

- `CONTEÚDO REAL`, `CONTA REAL`, `CONTA AUTENTICADA` e `IDENTIDADE DA SESSÃO`;
- `Exibindo somente dados reais`, `Conteúdo verificado` e `Este painel não utiliza dados fictícios`;
- descrições repetidas sobre dados reais, persistência, API, backend e ausência de simulação;
- rodapé lateral com o rótulo `produção`;
- mensagem de segurança explicando onde credenciais são persistidas;
- texto de carregamento Google mencionando servidor e inicialização;
- mensagens de configuração que podem revelar `VITE_API_URL`;
- manifesto público `Dados reais. Sem simulação.`;
- textos institucionais que explicam persistência e autorização do servidor.

O banner de staging é a exceção permitida e permanece condicionado ao ambiente. Mensagens de validação, sessão, acesso, erro e avisos jurídicos permanecem, em linguagem natural.

## Mocks e código demonstrativo

| Arquivo | Evidência | Situação |
|---|---|---|
| `src/CommunityPage.tsx` | posts, autores, equipes, comentários, estatísticas e fila de moderação fixos; votos, salvos, denúncias e comentários em `useState` | desconectado do bundle; não pode ser reativado sem API |
| `src/CommunityHighlights.tsx` | `initialHighlights`, `Date.now()`, responsável demonstrativo e alterações em memória | desconectado do bundle; não pode ser reativado sem API |
| `src/achievement-catalog.ts` | 100 conquistas e progresso gerados no frontend | desconectado do bundle; deve virar catálogo persistido ou seed versionado |
| `src/styles.css` | grande volume de estilos de telas removidas e comentários demonstrativos | código morto; remoção depende da reconstrução modular |
| `scripts/*-scenarios.mjs` | motores e contratos demonstrativos antigos | válidos apenas como referência/teste; não provam backend funcional |

Não há uso de `localStorage` ou `sessionStorage` no fluxo oficial. `setTimeout` em `src/api.ts` é legítimo para timeout/retry e não representa persistência simulada.

## Botões e ações incompletas

No bundle oficial, os botões de autenticação, menu, navegação, retorno e logout executam ações reais. Entretanto, os nove destinos do menu de módulos apenas trocam o estado React e sete deles mostram a mesma estrutura vazia; portanto, a navegação é incompleta.

Nos protótipos desconectados foram encontrados botões sem ação definitiva: política completa, itens de atividade, filtro e revisão de moderação, ferramentas do editor, upload, revelar conteúdo sensível, salvar/compartilhar no detalhe, curtir/responder comentários e ações administrativas. Formulários de publicação, comentário, denúncia e destaque informam sucesso local sem persistência.

## Formulários sem persistência

- Funcionais: cadastro, login, recuperação, redefinição e início Google.
- Simulados e desconectados: publicação, comentário, denúncia e destaque.
- Ausentes: edição do operador, privacidade, equipamentos, equipe, convite, operação, inscrição, desempenho, classificado, procura e pesquisa global.

## Endpoints existentes

Contratos próprios existentes:

- `GET /api/auth/csrf`;
- `POST /api/auth/register`;
- `POST /api/auth/verify-email`;
- `POST /api/auth/login`;
- `POST /api/auth/password-recovery`;
- `POST /api/auth/resend-verification`;
- `POST /api/auth/password-reset`;
- `POST /api/auth/google/intent`;
- `GET /api/auth/session`;
- `POST /api/auth/logout`.

O Spring Security também atende o redirecionamento OIDC e os callbacks do Google. Health checks e documentação OpenAPI são infraestrutura, não contratos de negócio. Qualquer outra rota é negada pelo backend.

## Endpoints ausentes por etapa

1. Operador: perfil próprio, privacidade, preferências, equipamentos, recrutamento, sessões e busca segura.
2. Equipes: criação/edição, integrantes, convites, aceite/recusa, saída, transferência e histórico.
3. Operações: CRUD, publicação, regras, missões, times, esquadrões, inscrições, espera, check-in e encerramento.
4. Ranking e conquistas: desempenho, eventos, confirmações, contestações, homologação, snapshots, catálogo e progresso.
5. Comunidade: categorias, posts, comentários, votos, salvos, denúncias, bloqueios, silenciamentos e moderação.
6. Classificados: venda/procura, imagens permitidas, favoritos, propostas, conversas, reserva, encerramento e denúncias.
7. Destaques: seleção administrativa gratuita, vigência, status e auditoria.

## Migrations necessárias

As migrations aplicadas chegam a `V5`. A sequência nova deve ser aditiva e usar a numeração real do repositório:

1. `V6__operator_profile_functionality.sql` — evolução do perfil, preferências, recrutamento, equipamentos, privacidade e índices de busca;
2. `V7__teams_and_invitations.sql` — equipes, integrantes, papéis, convites e histórico;
3. `V8__operations_and_registrations.sql` — operação gratuita, regras, estrutura, inscrições e check-in;
4. `V9__performance_rankings_achievements.sql` — desempenho, confirmação, snapshots, catálogo e concessões idempotentes;
5. `V10__community_and_moderation.sql` — publicações, comentários, interações, denúncias e decisões;
6. `V11__classifieds_and_conversations.sql` — anúncios/procuras, propostas, favoritos, conversas e auditoria, sem pagamentos;
7. `V12__community_highlights.sql` — curadoria gratuita e trilha administrativa.

Uploads exigem storage externo, quarentena e varredura. Não devem ser implementados no disco efêmero do Render.

## Ordem de implementação confirmada

1. Limpar mensagens técnicas, retirar código demonstrativo do caminho oficial e criar testes de ausência.
2. Funcionalizar Meu Operador, Equipes, Convites e busca de operadores.
3. Funcionalizar Operações e inscrições gratuitas.
4. Funcionalizar Ranking, Reputação e Conquistas.
5. Funcionalizar Comunidade e moderação.
6. Funcionalizar Classificados e Destaques gratuitos.
7. Concluir rotas reais, acessibilidade, responsividade, testes E2E e auditoria final.

## Riscos de regressão

- misturar esta entrega com as alterações locais de autenticação ainda não publicadas;
- editar migrations `V1`–`V5` já aplicáveis;
- reativar protótipos e expor dados fixos no bundle oficial;
- ampliar `permitAll` ou confiar em IDs do cliente ao criar endpoints;
- devolver objetos completos e violar privacidade por campo;
- aceitar upload antes de storage/quarentena estarem disponíveis;
- introduzir rotas sem fallback e quebrar acesso direto/refresh;
- confirmar sucesso no frontend antes da transação no PostgreSQL;
- publicar acidentalmente sem as variáveis e migrations necessárias.

## Baseline antes das alterações

- `npm run check`: aprovado;
- `mvn -B clean verify`: aprovado, 40 testes;
- deploy, push e merge: não executados.

## Etapa 2 implementada localmente

- `V6__operator_profile_functionality.sql` e `V7__teams_and_invitations.sql` foram criadas de forma aditiva; `V1`–`V5` não foram alteradas.
- Meu Operador possui edição persistente, privacidade e equipamentos sem foto.
- Busca segura de operadores e perfil resumido foram conectados ao backend.
- Minha Equipe possui criação/edição, integrantes, convites, aceite/recusa, saída e transferência de capitania.
- Rotas `/api/operators/**` e `/api/teams/**` estão autenticadas e o backend conserva `anyRequest().denyAll()`.
- `npm run check` foi aprovado; `mvn -B clean verify` foi aprovado com 45 testes.
- As migrations não foram aplicadas em Render/produção e nenhum deploy, push ou merge foi realizado.

Pendências deliberadas: PostgreSQL isolado via Testcontainers não foi executado nesta estação, consulta visual do histórico ainda não possui endpoint, rota pública navegável de operador não foi criada e uploads permanecem bloqueados. A próxima etapa funcional é [[09-OPERACOES]] e inscrições gratuitas.

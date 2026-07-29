# Operações

## Remarcação, briefing e exclusão segura — 2026-07-29

O organizador pode editar data, horários e briefing pelo detalhe da própria operação. `PATCH /api/operations/{id}` exige motivo, data presente ou futura, sequência válida dos horários e a versão atual do registro; conflitos de edição simultânea retornam `409` sem sobrescrever dados. O briefing é texto simples de até 12.000 caracteres e nunca é renderizado como HTML.

`DELETE /api/operations/{id}` exige motivo e executa exclusão lógica somente para o proprietário. Operações em andamento ou finalizadas não podem ser excluídas. A operação removida deixa de aparecer nas consultas e de aceitar acesso por endpoints de estrutura, participação, capa ou desempenho, enquanto participantes, chats e auditoria permanecem preservados no PostgreSQL. A migration incremental `V19__operation_briefing_and_soft_delete.sql` adiciona `briefing`, `deleted_at`, `deleted_by` e índice parcial para consultas ativas.

## Estrutura persistente, comando e chats — 2026-07-29

A migration `V18__operation_structure_roles_and_chat.sql` amplia o agregado sem alterar migrations aplicadas: define `SMALL`, `MEDIUM` e `LARGE`, enriquece `operation_team`, cria `operation_squad`, funções específicas da operação, canais geral/time, mensagens, denúncias e ações de moderação. Operações antigas são classificadas pela capacidade e pela quantidade real de times, sem excluir participantes.

Inscrição e movimentação bloqueiam as linhas da operação, do time e do esquadrão antes de recontar vagas, evitando ocupação concorrente da última vaga. A redução de tamanho nunca remove dados automaticamente e informa as incompatibilidades. As funções locais não alteram RBAC global.

O chat usa REST com polling controlado de cinco segundos, compatível com a arquitetura atual do Render. Toda mensagem é persistida no PostgreSQL com chave de idempotência; a leitura do chat de time revalida a participação atual no backend. O React renderiza mensagens como texto, o backend remove controles invisíveis, limita tamanho e aplica rate limit no Redis.

A área de detalhe apresenta tamanho, ocupação, times, esquadrões, lideranças e canais. Capas seguem [[05-SEGURANCA-E-PRIVACIDADE]], com prévia local, substituição, remoção autenticada e imagem padrão quando ausentes.

## Organizador como participante — 2026-07-28

Depois de publicar a operação, o organizador também pode abrir o detalhe, escolher um time e inscrever-se como jogador. A inscrição do organizador é aprovada automaticamente, mas continua sujeita à capacidade do time, ao limite total da operação, à lista de espera e à unicidade por usuário. A função de organizador permanece independente da participação esportiva.

## Imagem de capa persistente — 2026-07-28

O cadastro permite anexar uma capa PNG ou JPEG opcional. O frontend cria primeiro a operação, envia a imagem para `POST /api/operations/{id}/cover` e somente depois publica, quando essa foi a ação escolhida. O backend valida propriedade da operação, conteúdo real, tamanho e dimensões, reencoda a imagem e persiste os bytes na tabela `operation_cover`, criada pela migration `V15__operation_cover_images.sql`. A capa aparece na agenda e no detalhe; rascunhos e suas imagens permanecem restritos ao organizador.

## Participantes e escolha de time — 2026-07-28

Ao selecionar uma operação, o usuário acessa o detalhe com times, capacidade e lista real de participantes. A migration `V14__operation_teams_and_participant_roster.sql` cria `operation_team`, vincula `operation_participant.operation_team_id`, gera dois times por padrão ou respeita o limite configurado e distribui inscrições anteriores sem apagá-las. A inscrição exige `operationTeamId` pertencente à operação; o backend valida publicação, vínculo do time, capacidade e lista de espera em uma única operação SQL. Rascunhos permanecem visíveis apenas ao organizador.

## Publicação funcional — 2026-07-28

O organizador pode escolher `Salvar rascunho` ou `Publicar operação` no cadastro. A publicação usa `POST /api/operations/{id}/publish`, aceita exclusivamente uma operação própria em estado `DRAFT` e a move atomicamente para `REGISTRATION_OPEN`, registrando `published_at` e auditoria. Rascunhos aparecem somente para o próprio organizador; depois da publicação, a operação entra na pesquisa dos demais operadores e aceita solicitações de participação.

## Base funcional iniciada em 2026-07-28

A migration `V8__fields_maps_and_operations.sql` e os módulos `operation` e `venue` iniciam a implementação persistente descrita em [[19-EXPANSAO-FUNCIONAL-OPERADORZERO-2026-07-28]]. Campos, mapas, operações e participação deixam de depender de conteúdo demonstrativo.

A migration `V10__field_optional_address_and_gps_link.sql` torna o endereço textual opcional e adiciona um link HTTPS de localização GPS. O link é validado para provedores de mapas conhecidos e, quando informado, aparece aos usuários como ação externa segura no cartão do campo.

Valores e formas de pagamento são informações fornecidas pelo organizador. O OperadorZero não processa nem intermedeia pagamentos. Uploads continuam bloqueados até a implantação dos controles de [[05-SEGURANCA-E-PRIVACIDADE]].

## Localização no cadastro

O cadastro de operação solicita Estado e Cidade, nessa ordem, usando a base brasileira compartilhada. CEP não é solicitado. Instruções de chegada e endereço preciso reservado permanecem separados e sujeitos a autorização por objeto.

O módulo pertence ao agregado `Operation` descrito em [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]] e ao fluxo de [[04-MODULOS-E-FLUXOS]]. O frontend atual é uma demonstração sem API, banco, upload ou autorização real.

## Interface demonstrativa implementada

- abas `Próximas`, `Inscrições abertas`, `Hoje`, `Em andamento`, `Minhas operações`, `Operações realizadas` e `Canceladas`;
- próximas ordenadas por data, horário, cidade e nome;
- operações finalizadas e canceladas separadas da agenda ativa;
- filtros de tipo, cidade, participação e documento;
- cards com capa, data, modalidade, campo, preço, vagas, inscritos, status, organizador, PDF, times e missões;
- detalhe com abas de visão geral, regras, missões, times/esquadrões, cronograma, documento, participantes e atualizações;
- assistente de criação em seis etapas: informações, local, data, informações adicionais, regras/documento e estrutura;
- modalidade personalizada, múltiplos times/esquadrões e missões demonstrativas;
- nenhum dado, PDF, inscrição, aceite ou rascunho é persistido.

## Modalidade e tipo de jogo

São campos obrigatórios e independentes. `Operation.modalityId` representa estilo, realismo e regras-base; `Operation.gameTypeId` representa objetivo ou dinâmica. Exemplo: modalidade `MilSim` com tipo `Captura da bandeira`.

Modalidades demonstrativas: `4FUN`, `MilSim — Military Simulation`, `SAR — Simulação de Ação Real`, `Pré-SAR`, `PMA — Pro Military Airsoft`, `CQB competitivo`, `Speedsoft`, `Treinamento tático esportivo`, `Modalidade híbrida` e `Modalidade personalizada`.

Ao selecionar modalidade, o protótipo oferece aplicar o modelo, aplicar parcialmente ou ignorar. Sugestões nunca substituem silenciosamente regras escritas. Modalidade personalizada e tipo de jogo personalizado possuem formulários separados. O organizador também informa se a operação é indicada para iniciantes.

Na API futura, `OperationModality`, `OperationModalityTemplate`, `OperationModalityRule`, `OperationModalityRequirement`, `OperationModalityDocument` e `OperationCustomModality` formam catálogo administrável. Modelo privado pertence ao organizador; publicação reutilizável exige aprovação administrativa e auditoria.

## Permissões futuras

Papéis: operador, organizador, auxiliar, comandante, líder de esquadrão, moderador e administrador. Capacidades: `OPERATION_CREATE`, `OPERATION_EDIT_OWN`, `OPERATION_EDIT_ANY`, `OPERATION_PUBLISH`, `OPERATION_CANCEL`, `OPERATION_MANAGE_PARTICIPANTS`, `OPERATION_MANAGE_TEAMS`, `OPERATION_MANAGE_SQUADS`, `OPERATION_MANAGE_MISSIONS`, `OPERATION_UPLOAD_DOCUMENT`, `OPERATION_CHECK_IN`, `OPERATION_PUBLISH_RESULT` e `OPERATION_VIEW_PRIVATE_DATA`.

Todas devem ser validadas no backend com vínculo ao objeto. Ocultar botão não é autorização.

## Modelo de dados planejado

Entidades: `Operation`, `OperationType`, `CustomGameType`, `OperationModality`, `OperationModalityTemplate`, `OperationModalityRule`, `OperationModalityRequirement`, `OperationModalityDocument`, `OperationCustomModality`, `OperationSchedule`, `OperationLocation`, `OperationPricing`, `OperationRule`, `OperationRuleVersion`, `OperationDocument`, `OperationDocumentVersion`, `OperationMission`, `OperationMissionDependency`, `OperationMissionVisibility`, `OperationTeam`, `OperationSquad`, `OperationParticipant`, `OperationParticipantRole`, `OperationRegistration`, `OperationCheckIn`, `OperationUpdate`, `OperationAuditLog`, `OperationInvitation` e `OperationFavorite`.

Usar UUID público, chave interna, timestamps UTC, soft delete onde necessário, constraints de transição e índices em início, status, cidade, estado, tipo, organizador, campo e inscrições abertas. Missões, documentos e locais reservados exigem autorização por objeto antes da leitura.

## Migrations planejadas

Como não existe backend ou banco neste repositório, nenhuma migration foi criada. Na futura API Spring Boot/Flyway, dividir de forma aditiva:

1. `V001__operation_core_types_modalities.sql` — operação, tipos e modalidades administráveis, `modality_id`, `game_type_id`, agenda, local e preço;
2. `V002__operation_rules_documents.sql` — regras/versionamento e metadados de documentos;
3. `V003__operation_missions.sql` — missões, dependências e visibilidade;
4. `V004__operation_teams_squads.sql` — times, esquadrões e vínculos;
5. `V005__operation_registrations_checkin.sql` — inscrições, participantes, funções e check-in;
6. `V006__operation_updates_audit.sql` — comunicados, convites, favoritos e auditoria.

As migrations devem preservar IDs, criar defaults explícitos, não apagar dados e ser testadas em banco separado.

## Endpoints planejados

- `GET/POST /api/operations`;
- `GET/PATCH /api/operations/{publicId}`;
- `POST /api/operations/{publicId}/publish|cancel|finish`;
- `GET/POST /api/operation-types` e administração protegida;
- `GET /api/operation-modalities` e `POST/PATCH /api/admin/operation-modalities`;
- `GET/POST/PATCH /api/operation-modality-templates` com escopo privado ou aprovação pública;
- sub-recursos `/schedule`, `/location`, `/pricing`, `/rules`, `/documents`, `/missions`, `/teams`, `/squads`, `/registrations`, `/participants`, `/check-ins`, `/updates`, `/favorites` e `/audit`;
- downloads privados por URL assinada curta, nunca caminho interno.

Consultas de próximas operações usam `start_at >= now()` e `ORDER BY start_at ASC, start_time ASC, city ASC, name ASC`. Operações finalizadas e canceladas não entram no conjunto ativo.

## Segurança e auditoria

PDF somente após validação de MIME real, limite configurável, quarentena, antimalware, nome aleatório, storage compatível com S3 e URL assinada. Textos são dados, sem HTML arbitrário. Informações adicionais bloqueiam valores, dados bancários, meios de pagamento, QR Codes e links. Mudanças de data, local, regras, documento, missão, time, participante, cancelamento e finalização geram auditoria com ator, valores anterior/novo, motivo e correlação.

Local preciso reservado, missões secretas, briefing privado e composição interna nunca podem ser recuperados por troca de ID. Veja [[05-SEGURANCA-E-PRIVACIDADE]].
## Resumo na Visão Geral (2026-07-28)

A [[06-FRONTEND-WEB|Visão Geral]] apresenta até três operações publicadas e disponíveis em datas futuras. A ordenação dá preferência à cidade do operador, depois ao estado e, dentro de cada faixa regional, à operação mais próxima na agenda.
## Correção das consultas de estrutura (2026-07-29)

As consultas PostgreSQL de times e esquadrões agrupam explicitamente as colunas usadas na ordenação (`sort_order`). Isso evita erro `500` ao abrir uma operação publicada e mantém disponíveis a seleção do time, a inscrição e a central da operação. O frontend trata roster e estrutura separadamente, conforme [[06-FRONTEND-WEB]].
# Estrutura definida antes da publicação e chats por esquadrão

- O organizador salva a operação como rascunho e conclui times, capacidades, esquadrões e funções antes de publicar.
- Depois da publicação, jogadores apenas escolhem um time e, em jogos médios ou grandes, um esquadrão existente.
- Alterações estruturais após a publicação são recusadas pelo backend; movimentações administrativas de participantes permanecem separadas.
- Toda operação possui chat geral. Cada esquadrão possui um canal privado, autorizado novamente no backend a cada leitura ou envio.
- Integrantes de outro esquadrão não conseguem consultar histórico nem enviar mensagens no canal adversário.
- Canais privados antigos de time permanecem somente como histórico no banco, sem endpoint público.
- Operações médias e grandes já publicadas antes desta regra recebem um esquadrão inicial por time; participantes antigos são vinculados ao esquadrão do próprio time para que o ingresso e a privacidade continuem funcionais.

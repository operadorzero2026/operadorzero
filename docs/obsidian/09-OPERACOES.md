# Operações

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

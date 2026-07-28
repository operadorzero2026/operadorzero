# Minha Equipe

## Identificação na busca — 2026-07-28

A busca de operadores usa exclusivamente o vínculo ativo de `team_member` com equipe `ACTIVE` para exibir o nome da equipe ao lado do callsign. Equipe antiga, arquivada ou vínculo encerrado não aparece como equipe atual. Veja [[13-MEU-OPERADOR]].

O módulo amplia o agregado `Team` de [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]] e o fluxo de [[04-MODULOS-E-FLUXOS]]. A implementação atual é frontend demonstrativo: não cria equipe, não envia convite, não altera vínculo e não concede permissão.

## Interface atual

- estados com equipe e sem equipe na mesma área;
- Valkyrie Ops com identidade, cidade, modalidades, capitão, administradores, integrantes, campo, operações e estatísticas;
- abas de visão geral, integrantes, convites recebidos/enviados, operações, estatísticas, campo, configurações e histórico;
- criação em três etapas com logo, nome, localização padronizada, modalidades reutilizadas de [[09-OPERACOES]], campo e regras;
- busca demonstrativa somente por callsign/usuário/nome de exibição;
- confirmação visual do operador antes do convite;
- aviso e confirmação quando o convidado já possui equipe;
- decisão de troca sem saída automática e bloqueio quando há capitania/responsabilidades;
- limite demonstrativo de 20 convites/dia, um convite ativo por equipe/usuário e validade padrão de 15 dias.

## Invariantes

- um usuário possui no máximo uma equipe principal ativa;
- uma equipe possui um capitão ativo;
- nome completo de equipe ativa é único;
- fundador não sai sem transferência ou encerramento/arquivamento adequado;
- função sugerida no convite não concede permissão administrativa;
- aceitar convite nunca ocorre em nome de outro usuário;
- histórico de vínculo, função, operações e estatísticas é preservado;
- estatísticas antigas permanecem ligadas à equipe representada na operação;
- declarar campo próprio cria solicitação de vínculo, não propriedade ou administração automática.

Troca de equipe deve ocorrer em uma transação: validar convite e responsabilidades, encerrar vínculo atual, preservar histórico, criar vínculo novo, aceitar convite, resolver convites incompatíveis, auditar e notificar.

## Modelo planejado

`Team`, `TeamMember`, `TeamMembershipHistory`, `TeamInvitation`, `TeamInvitationMessage`, `TeamInvitationPreference`, `TeamUserBlock`, `TeamRole`, `TeamPermission`, `TeamFieldRelationship`, `TeamGameStyle` e `TeamAuditLog`.

Estilos reutilizam `OperationModality`; não manter segunda lista independente. Logo usa object storage seguro e metadados próprios. Convite registra equipe, convidado, remetente, mensagem, função sugerida, equipe atual, envio, expiração, status, visualização, resposta e auditoria.

## Migrations planejadas

Não há banco neste repositório; nenhuma migration foi criada. Na API futura:

1. `V007__teams_members_roles.sql` — equipe, membros, papéis, permissões e histórico;
2. `V008__team_invitations_preferences_blocks.sql` — convites, mensagens, preferências e bloqueios;
3. `V009__team_fields_styles_audit.sql` — vínculos com campos, modalidades e auditoria.

Criar constraints/índices parciais para nome ativo único, equipe principal ativa única, capitão ativo único e convite ativo único por equipe/convidado.

## Endpoints planejados

- `GET/POST /api/teams` e `GET/PATCH /api/teams/{publicId}`;
- `/api/teams/{publicId}/members`, `/roles`, `/permissions`, `/history`, `/field-relationships`;
- `/api/teams/{publicId}/invitations` para criar, cancelar e reenviar;
- `/api/team-invitations/received` e ações `accept`, `decline`, `defer`, `report`;
- `/api/team-memberships/leave`, `/transfer-captaincy` e `/resolve-responsibilities`;
- `/api/operator/team-invitation-preferences` e `/blocks`;
- busca pública de operadores com campos mínimos, nunca CPF, telefone, e-mail ou nome completo privado.

## Segurança

Autorizar cada ação pela equipe, vínculo e permissão no backend. Administrador auxiliar não remove fundador, transfere capitania, promove a si próprio ou concede permissão superior. Aplicar rate limit, intervalo contra repetição, preferências/bloqueios, auditoria e suspensão por abuso.

Logo aceita somente JPEG/PNG após identificação real, limite de 2 MB e 2048 × 2048, decodificação e regravação que remove metadados e conteúdo adicional. SVG/WebP/GIF/HTML e arquivos ilegíveis são bloqueados. Enquanto não há bucket, os bytes reprocessados ficam em tabela PostgreSQL separada, nunca no disco efêmero ou webroot. Veja [[05-SEGURANCA-E-PRIVACIDADE]].

## Estado funcional em 2026-07-27

A migration aditiva `V7__teams_and_invitations.sql` e os componentes `TeamService`, `TeamController` e `TeamPage` implementam a primeira versão real do módulo. Não há mais equipe fictícia no caminho oficial. O operador pode criar e editar equipe, consultar integrantes, receber/aceitar/recusar convites, convidar por busca segura, sair da equipe e transferir a capitania.

O banco garante uma equipe ativa por usuário, um capitão ativo por equipe, nome ativo único e um convite pendente por equipe/convidado. Aceite, recusa, saída e transferência são transacionais, autorizados pelo vínculo do usuário, auditados e preservados no histórico. Convites expiram em 15 dias e têm limite de 20 por dia; gestores não podem convidar outro gestor e o capitão não pode sair antes da transferência.

Contratos autenticados: `GET /api/teams/workspace`, `POST /api/teams`, `PATCH /api/teams/{teamId}`, `POST /api/teams/{teamId}/logo`, `GET /api/teams/{teamId}/logo`, `POST /api/teams/{teamId}/invitations`, ações `accept|decline`, `POST /api/teams/leave` e `POST /api/teams/{teamId}/captaincy`. Permanecem pendentes consulta visual do histórico, remoção/promoção granular, preferências/bloqueios, arquivamento e migração das logos para object storage quando disponível.

A criação permite selecionar uma logo opcional; depois, capitão e gestores podem substituí-la na administração. A imagem exibida vem sempre do conteúdo reprocessado pelo backend.

Siglas de equipe aceitam de 2 a 12 caracteres entre letras, números, pontos e hífens, incluindo formatos tradicionais como `A.T.A.C.`. A validação existe no navegador e no backend; mensagens de erro identificam o campo inválido sem expor detalhes internos.

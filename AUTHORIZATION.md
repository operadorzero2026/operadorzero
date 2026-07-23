# Autorizacao

RBAC inicial: USER, TEAM_MANAGER, ORGANIZER, MODERATOR e ADMIN, com permissoes granulares. O papel financeiro legado e removido de forma auditada pela migration `V4`. O servidor valida permissao, propriedade do recurso, estado do fluxo e visibilidade; a interface apenas oculta controles por conveniencia.

Toda consulta deve ser filtrada pelo sujeito autorizado. Acoes administrativas, financeiras, de moderacao, privacidade e ranking geram auditoria. A matriz detalhada sera validada antes da Fase 2 e coberta por testes positivos e negativos.

Status em 2026-07-22: a migration `V3` cria os papeis e concede ao `USER` somente permissoes minimas de perfil/sessao. O filtro de sessao entrega as authorities ao Spring Security, e todas as rotas fora da identidade permanecem negadas. Isso ainda nao implementa autorizacao por objeto dos modulos demonstrativos; nenhum desses modulos pode ser habilitado para escrita antes de repositories, services e testes BOLA/IDOR filtrarem pelo usuario autenticado.

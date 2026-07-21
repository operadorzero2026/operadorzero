# Autorizacao

RBAC inicial: USER, TEAM_MANAGER, ORGANIZER, MODERATOR, FINANCE_MANAGER e ADMIN, com permissoes granulares. O servidor valida permissao, propriedade do recurso, estado do fluxo e visibilidade; a interface apenas oculta controles por conveniencia.

Toda consulta deve ser filtrada pelo sujeito autorizado. Acoes administrativas, financeiras, de moderacao, privacidade e ranking geram auditoria. A matriz detalhada sera validada antes da Fase 2 e coberta por testes positivos e negativos.


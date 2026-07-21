# Access Control Matrix

| Recurso | Público | Operador | Gestor vinculado | Admin |
|---|---|---|---|---|
| Perfil público | ler campos permitidos | editar o próprio | — | moderar com motivo |
| Equipe | ler publicada | solicitar/sair | gerir membros conforme papel | moderar |
| Campo/mapa | ler publicado | avaliar | gerir campo próprio | verificar/moderar |
| Operação | ler publicada | inscrever/check-in próprio | gerir operação própria | moderar |
| Desempenho | ler agregado permitido | declarar/contestar vinculado | confirmar quando autorizado | auditar com motivo |
| Ranking | ler publicado | ler explicação própria | ler | congelar/recalcular com dupla confirmação |
| Auditoria | nenhum | próprios eventos selecionados | escopo necessário | leitura restrita; nunca apagar na UI |

Toda decisão ocorre no backend, combina papel + vínculo + estado + escopo e gera teste de negação.


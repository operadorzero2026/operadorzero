# Rede social de operadores

O módulo Operadores integra perfis de [[13-MEU-OPERADOR]], publicações e interações de [[14-COMUNIDADE]], equipes de [[10-MINHA-EQUIPE]], operações de [[09-OPERACOES]] e os controles de [[05-SEGURANCA-E-PRIVACIDADE]].

## Entrega funcional inicial

- `/operadores`: feed real baseado nas publicações persistidas da Comunidade.
- `/operadores/buscar`: busca server-side por nick, callsign ou nome.
- `/operadores/amigos`: amizades aceitas.
- `/operadores/solicitacoes`: solicitações pendentes recebidas e enviadas.
- `/operadores/{username}`: perfil público estável.
- ações persistentes: solicitar, aceitar, recusar, cancelar, remover, bloquear, desbloquear e denunciar.

As relações são persistidas pela migration `V22__operator_social_connections.sql`. A unicidade da dupla de usuários impede duas amizades/solicitações ativas simultâneas, independentemente de quem iniciou. O backend valida o proprietário e o estado de cada transição. Um bloqueio remove imediatamente relações ativas e oculta o usuário nas listas.

## Mídia e publicações

O módulo reutiliza publicações, imagens raster reprocessadas, comentários, votos, favoritos e moderação da Comunidade. Não há fixtures nem conteúdo demonstrativo. Vídeos grandes não são gravados no PostgreSQL; publicação de vídeo permanece indisponível até existir armazenamento privado compatível com R2/S3, análise de conteúdo e entrega por URLs controladas.

## Limitações conhecidas

- capa personalizada, contadores agregados e mensagens privadas ainda precisam de uma etapa posterior;
- o perfil exibe integrações somente quando os módulos oficiais possuírem registros reais;
- a migration foi validada estaticamente e pelos testes, mas não foi aplicada em produção nesta entrega.
# Perfil social responsivo

Em 2026-07-30, a rota `/operadores` passou a abrir o perfil social do operador autenticado; `/operadores/{username}` usa a mesma composição para outros perfis. A tela mantém o shell oficial e organiza capa, avatar, identidade, ações, estatísticas, experiência, equipe, especialidade, feed e atividade real.

No desktop, a área inferior usa três colunas: resumo, feed e atividade. Em larguras abaixo de 900px, a hierarquia vira uma única coluna, com cabeçalho compacto, métricas, informações profissionais, abas e feed. Publicações com mídia usam proporção 4:5. Vídeos não são simulados e continuam indisponíveis enquanto o módulo oficial aceitar apenas PNG e JPEG.

O endpoint `GET /api/operators/social/{username}/profile` agrega somente dados persistidos e aplica bloqueio e privacidade de localização/equipamentos. Nível, XP, conquistas e ranking mostram estado vazio quando não houver contrato oficial persistido; nenhum número demonstrativo é criado.

Relaciona-se com [[06-FRONTEND-WEB]], [[13-MEU-OPERADOR]], [[14-COMUNIDADE]] e [[05-SEGURANCA-E-PRIVACIDADE]].

# Meu Operador

## Localizacao

Na edicao do perfil, Estado precede Cidade. Todos os estados brasileiros estao disponiveis e a cidade e carregada conforme a UF selecionada pela fonte oficial do IBGE. A localizacao publica continua limitada a municipio e estado; o backend devera validar os codigos oficiais e a combinacao informada.

Tela central de identidade do jogador, conectada a [[09-OPERACOES]], [[10-MINHA-EQUIPE]], [[11-RANKING]] e [[12-CONQUISTAS-E-MEDALHAS]]. A implementação atual é uma demonstração frontend; os contratos abaixo são obrigatórios antes de produção.

## Experiência entregue no protótipo

- Cabeçalho com avatar, callsign, `@username`, localização aproximada, equipe, posição, recrutamento, rankings e reputação agregada.
- Abas: Visão geral, Equipamentos, Equipe, Ranking, Conquistas, Operações, Reputação, Privacidade e Configurações da conta.
- Edição de identidade e preferências, inventário com terminologia exclusiva de airsoft, predefinições de privacidade, prévia por tipo de visitante e reautenticação demonstrativa.
- Busca global agrupada por operadores, equipes, operações, campos e campeonatos, sem indexar e-mail, telefone, CPF, nome real privado, endereço ou nascimento.
- Layout responsivo, controles maiores e navegação horizontal no mobile.

## Modelo de dados proposto

- `users`: credenciais, e-mail verificado, telefone verificado, MFA e estado da conta.
- `operator_profiles`: callsign, nome de exibição, nome real criptografado, username, bio, cidade, estado, país e status.
- `operator_privacy_settings`: uma linha por campo e nível de visibilidade.
- `operator_game_preferences` e `operator_position_preferences`: modalidades, frequência, experiência e posições do catálogo de [[11-RANKING]].
- `operator_equipments` e `operator_equipment_images`: categoria, plataforma, marca, modelo, alimentação, função, cor, apelido, status, observações e visibilidade.
- `operator_recruitment_statuses` e `operator_recruitment_preferences`: disponibilidade, região, modalidades, posições, viagens, campeonatos, convites e notificações.
- `operator_social_links`, `operator_profile_views`, `operator_username_history`, `operator_profile_audit_log` e `operator_search_index`.

Todas as tabelas de domínio devem possuir UUID interno, `user_id`, timestamps e índices adequados. IDs internos nunca entram na URL pública.

## Migrations planejadas

1. `V020__operator_profile_privacy_username.sql`: perfil, privacidade, nomes reservados e histórico de username.
2. `V021__operator_preferences_recruitment.sql`: preferências, posições e recrutamento.
3. `V022__operator_equipment_social.sql`: equipamentos, imagens e redes sociais.
4. `V023__operator_search_views.sql`: índice seguro, visualizações agregadas e redirects de username.
5. `V024__operator_audit_account_security.sql`: auditoria, MFA, códigos de recuperação e sessões revogáveis.

As migrations não existem no repositório porque ainda não há backend ou banco configurado. Não executar SQL improvisado no frontend.

## Endpoints propostos

- `GET|PATCH /api/v1/operators/me`
- `GET|PUT /api/v1/operators/me/privacy`
- `GET|POST|PATCH|DELETE /api/v1/operators/me/equipment`
- `GET|PUT /api/v1/operators/me/preferences`
- `GET|PUT /api/v1/operators/me/recruitment`
- `GET /api/v1/operators/{username}` com projeção filtrada no servidor
- `GET /api/v1/operators/{username}/preview?viewerRole=...` restrito ao proprietário/admin
- `GET /api/v1/search?q=&types=&cursor=` com limite, paginação e rate limit
- `POST /api/v1/account/username/check` e `POST /api/v1/account/username/change`
- endpoints separados para senha, e-mail, telefone, MFA, recuperação, sessões, exportação e exclusão.

## Privacidade e autorização

Níveis: Somente eu; Minha equipe; Organizadores de operações em que participo; Usuários conectados; Público. A política é avaliada no service do backend para cada campo, considerando usuário autenticado, vínculo ativo da equipe e participação confirmada na operação. Frontend nunca decide autorização.

Nome real, nascimento, contato e atividade começam privados. Localização pública aceita somente cidade, estado, região e país. Endereço, bairro, CEP e coordenadas exatas não podem compor resposta pública nem índice de busca.

Reputação expõe apenas agregado com amostra mínima; nunca autores, comentários, voto isolado ou operação relacionada. Perfis suspensos, bloqueados ou excluídos ficam fora da busca e recebem resposta pública uniforme, evitando enumeração.

## Username e URL pública

O username aceita 3 a 30 caracteres entre letras, números, ponto, hífen e sublinhado, sem espaços. Deve ser único sem distinção de maiúsculas, respeitar nomes reservados, antifalsidade ideológica, histórico e intervalo sugerido de 30 dias. A URL pública é `/operador/{username}`; mudanças preservam redirect temporário seguro sem expor UUID.

## Segurança da conta

Mudança de senha, e-mail, telefone, username, MFA, sessões, exportação e exclusão exigem reautenticação; ações críticas também exigem MFA quando ativo. Códigos de recuperação são mostrados uma vez, armazenados com hash e nunca logados. Uploads exigem limite, MIME real, antivírus, remoção de metadados e armazenamento fora da aplicação. URLs de redes sociais usam allowlist de protocolos e normalização contra `javascript:`.

## Testes obrigatórios do backend

- Autorização e IDOR para proprietário, equipe, organizador, autenticado e visitante.
- Matriz completa de visibilidade por campo e predefinição.
- Concorrência, cooldown, reserva, histórico e enumeração de username.
- Busca sem dados privados, com paginação, limite, bloqueio, suspensão e rate limit.
- Upload malicioso, XSS, URL perigosa, textos longos e categorias proibidas.
- Reautenticação, MFA, recuperação, revogação de sessão, exportação e exclusão.
- Coerência de equipe ativa, recrutamento, equipamento principal e posição do ranking.

## Pendências

- Criar backend, banco, migrations e testes de integração.
- Implementar rota real `/operador/:username` e estado 404 uniforme.
- Persistir as alterações, aplicar a política no servidor e conectar mídia segura.
- Fazer revisão jurídica e de proteção de dados antes da produção, conforme [[05-SEGURANCA-E-PRIVACIDADE]].

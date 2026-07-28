# Segurança e privacidade

## Organizador participante — 2026-07-28

O papel de organizador não concede uma segunda identidade nem contorna as regras de inscrição. Quando participa da própria operação, o mesmo usuário autenticado ocupa uma única vaga em um time válido, respeita capacidade e lista de espera e pode cancelar a própria participação. A aprovação automática limita-se ao organizador vinculado pelo backend àquela operação.

## Capas de operações — 2026-07-28

Somente o organizador autenticado pode inserir ou substituir a capa da própria operação. O servidor aceita exclusivamente PNG/JPEG reais de até 2 MB e 2048 × 2048 pixels, remove metadados pela reencodificação e armazena bytes no PostgreSQL, sem nome original ou disco efêmero. A leitura aplica a mesma visibilidade da operação: capa de rascunho é privada do organizador; capa de operação publicada pode ser lida por usuários autenticados. Veja [[09-OPERACOES]].

## Times e participantes de operações — 2026-07-28

O roster exige sessão e respeita a visibilidade do detalhe da operação. A inscrição aceita somente UUID de time pertencente à mesma operação, impede inscrição do organizador, aplica capacidade/lista de espera no banco e mantém unicidade por operação/usuário. Callsign e nome de exibição são a projeção mínima exibida; e-mail e demais dados privados não entram na resposta. Veja [[09-OPERACOES]].

## Autorização de publicação — 2026-07-28

A publicação de operação é validada por objeto no backend: somente o `organizer_user_id` da operação pode executar a transição de `DRAFT` para `REGISTRATION_OPEN`. A consulta não expõe rascunhos a terceiros, a transição é condicional no banco e a ação gera auditoria. Veja [[09-OPERACOES]].

## Mídia controlada — 2026-07-28

Fotos de operador seguem a mesma fronteira segura das logos: apenas o proprietário autenticado altera e lê a própria foto; o backend valida conteúdo real, tamanho e dimensões, reencoda PNG/JPEG e persiste bytes em tabela isolada. Não são armazenados nome original nem metadados, e o disco efêmero do Render não é utilizado. O contrato está documentado em [[13-MEU-OPERADOR]] e `FILE-UPLOAD-POLICY.md`.

O contrato de visibilidade por campo, busca não enumerável, username público, reautenticação, MFA e conta está detalhado em [[13-MEU-OPERADOR]]. A interface atual apenas demonstra esses controles; filtragem, autorização e auditoria continuam pendentes no backend.

## Ranking

O Ranking exige autorização por recurso no servidor, prevenção de IDOR, confirmação idempotente e única, proibição de autovoto, trilha imutável e snapshots versionados. Votos de reputação são secretos, agregados somente após amostra mínima e protegidos contra reciprocidade, conluio e assédio. Detalhes operacionais estão em [[11-RANKING]].

## Conquistas

[[12-CONQUISTAS-E-MEDALHAS]] impede autoconcessão e edição de progresso pelo operador. Concessões manuais exigem RBAC, MFA, motivo e auditoria; progresso usa apenas fontes homologadas. Reputação secreta nunca aparece em detalhes ou compartilhamentos. Revogação preserva histórico e uma constraint única impede concessões duplicadas sob concorrência.

Segurança é critério de aceite, não etapa posterior. A baseline detalhada vive em `SECURITY.md`, `THREAT-MODEL.md`, `ACCESS-CONTROL-MATRIX.md`, `RATE-LIMIT-POLICY.md`, `FILE-UPLOAD-POLICY.md`, `AUDIT-LOG-POLICY.md`, `PRIVACY-DATA-MAP.md`, `DEPLOYMENT-SECURITY.md`, `INCIDENT-RESPONSE.md` e `BACKUP-RESTORE.md`.

## Controles obrigatórios

- Argon2id para senha; tokens opacos de uso único; MFA obrigatório para administração.
- Google somente via OAuth 2.0/OIDC com PKCE, `state`, `nonce`, redirect URIs exatas e validação de issuer/audience.
- Recuperação por e-mail com resposta uniforme, rate limit, expiração curta, token armazenado como hash e revogação após uso.
- Sessão em cookie `HttpOnly`, `Secure`, `SameSite` e proteção CSRF adequada.
- Autorização por objeto no backend; IDs não são autorização.
- Validação com limites; queries parametrizadas; saída codificada; CSP restritiva.
- Upload fora do webroot, nome aleatório, allowlist real de conteúdo e varredura.
- Rate limits por conta, IP, rota e risco; respostas de login não enumeráveis.
- Logs de auditoria append-only e sem segredos; ações críticas com reautenticação.
- URLs de conexão recebidas dos provedores devem ser decompostas antes do uso: credenciais ficam em propriedades separadas e nunca aparecem na URL JDBC ou em logs.
- Rotas inexistentes retornam erro `404` padronizado e genérico; o tratador global não deve transformar ausência de recurso em falso erro interno nem expor detalhes de implementação.
- LGPD: minimização, finalidade, retenção, exportação, correção e exclusão/anonimização.

## Fronteira frontend/backend

O browser recebe somente `VITE_API_URL`, que é configuração pública. Senhas fixas, tokens, chaves OAuth, banco, Redis, storage, e-mail e pagamentos são proibidos no bundle, no Git e no armazenamento do navegador. Login, cadastro, recuperação e Google apontam para a API; o modo demonstrativo existe apenas em build local de desenvolvimento. A CI valida o bundle e executa varredura de segredos. A implantação está conectada a [[16-ARQUITETURA-DE-PRODUCAO]].

URLs PostgreSQL recebidas do ambiente são normalizadas sem credenciais na URL JDBC e exigem `sslmode=require` no mínimo; modos mais fortes, como `verify-full`, são preservados. A interface só confirma logout depois que a revogação no backend conclui. O diagnóstico de 2026-07-27 ainda registra riscos de timing de autenticação, bloqueio por sujeito e acesso direto ao endpoint de autorização Google para tratamento posterior; veja [[17-DIAGNOSTICO-E-PLANO-DE-PRODUCAO-2026-07-27]].

Desde 2026-07-22, a identidade real esta implementada: Argon2id, tokens de uso unico armazenados como SHA-256, cookie de sessao `HttpOnly`, CSRF, rate limit Redis, respostas neutras de e-mail, auditoria e OIDC/PKCE. O token de recuperacao aparece apenas no link recebido e e removido da URL antes da renderizacao. A SPA mantem o token CSRF somente em memoria e o renova depois da autenticacao.

Em 2026-07-23, o Google OIDC foi validado E2E no staging, incluindo intent protegido por CSRF, CORS explicito, PKCE, `nonce`, callback, criacao de conta e sessao autenticada. O cliente externo foi publicado no Google Auth Platform para remover a restricao a usuarios de teste; as origens autorizadas continuam explicitas e o callback permanece somente na API Render. Segredos Google permanecem apenas no Render. O segredo anterior do cliente OAuth deve ser removido apos esta homologacao.

O callback real revelou perda do pedido de autorizacao quando o Render Free reiniciava a instancia durante o consentimento. A sessao temporaria do Spring Security passou a usar Redis, cookie `HttpOnly`/`Secure`/`SameSite=Lax`, namespace exclusivo e TTL de 10 minutos; o E2E passou com essa configuracao. A sessao de usuario continua opaca e persistida separadamente no PostgreSQL. Logs de falha OAuth aceitam somente codigo tecnico sanitizado e tipo da excecao; codigo de autorizacao, token, segredo e descricao do provedor continuam proibidos.

## Pendências antes de produção

Threat modeling revisado, testes BOLA/IDOR, restauração de backup, dependências auditadas, MFA administrativo, política jurídica para menores, DPO/canal de privacidade e avaliação do provedor.

O dominio Resend possui SPF/DKIM verificados e os fluxos de confirmacao e recuperacao tiveram entrega externa validada. Permanecem obrigatorios: DMARC, tratamento de bounce e webhook validado, credenciais Google separadas por ambiente, rotacao do segredo Google anterior apos o E2E, termos/privacidade aprovados e infraestrutura sem expiracao. `RESEND_API_KEY` fica somente no Render e nenhum segredo deve usar prefixo `VITE_`.

## Comunidade

[[14-COMUNIDADE]] amplia riscos de assédio, discriminação, exposição de dados, fraude, conteúdo ilegal, denúncias abusivas e moderação indevida. Publicação exige análise no backend; automação não é prova; denunciante é protegido; decisões são motivadas e recorríveis; auditoria é imutável; ranking esportivo permanece separado. A abertura para menores e os termos dependem de revisão jurídica.

## Plataforma sem movimentação financeira

O Operador Zero não processa cobranças, não recebe valores, não calcula comissões e não vende prioridade. Informações adicionais de operações bloqueiam valores, meios de pagamento, dados bancários, QR Codes e links. Classificados preservam somente valor informativo e conversa entre usuários, conforme [[08-CLASSIFICADOS]]. Destaques seguem [[15-DESTAQUES-DA-COMUNIDADE]].

## Classificados

Anúncios, chat, propostas, documentos de procedência e imagens ampliam os riscos de fraude, PCE, abuso e exposição de dados. Upload em quarentena, remoção EXIF, autorização por objeto, limites progressivos, moderação humana e retenção justificada são gates obrigatórios. AEGs e marcadores usam fluxo especial somente textual, sem fotos por política preventiva, com prova de origem declarada e ativação real condicionada a parecer jurídico; as demais categorias sensíveis continuam desabilitadas. Veja [[08-CLASSIFICADOS]].

Procuras de equipamento reutilizam exatamente a mesma matriz de produtos, moderação e autorização. Dados públicos ficam limitados a callsign, cidade/UF, equipe, reputação e selos. Propostas, mensagens, encerramento, renovação e marcação como encontrado exigem autorização do proprietário no servidor, rate limit e auditoria. Veja [[CLASSIFICADOS-PROCURO-EQUIPAMENTO]].

## Operações

O módulo [[09-OPERACOES]] exige autorização por objeto para gestão, inscrições, missões, times, documentos e dados privados. PDF passa por quarentena e validação real. Local reservado, briefing privado e missão secreta nunca são enviados ao frontend sem autorização. Data, local, preço, regras, documentos, participantes, cancelamento e finalização geram auditoria e, quando relevante, notificação e novo aceite.

## Equipes

[[10-MINHA-EQUIPE]] exige autorização por equipe e impede escalada de privilégio. Convite respeita preferências, bloqueios, rate limit e vínculo ativo único. Troca de equipe é transacional e preserva histórico. Busca pública nunca usa CPF, telefone, e-mail ou nome completo privado. Logos seguem a política de upload seguro e não aceitam SVG na primeira versão.

## Endurecimento da autenticação em 2026-07-27

Cadastro, recuperação e reenvio executam trabalho Argon2 equivalente para reduzir enumeração por tempo. O rate limit por sujeito foi associado ao IP de origem, evitando bloqueio global de uma conta por terceiros. O Google OIDC somente inicia depois de uma liberação de uso único criada pelo endpoint protegido por CSRF e rate limit; acesso direto não aloca pedido OAuth.

Hashes de tokens, sessões, endereços e identificadores de auditoria usam HMAC-SHA-256 com `AUTH_HASH_KEY` exclusiva do backend. A chave é obrigatória quando a autenticação está habilitada, nunca usa prefixo `VITE_` e sua rotação invalida sessões e links pendentes. Intenções Google consumidas ou expiradas possuem limpeza indexada pela migration `V5`. O Redis usa `noeviction`; esgotamento fecha o fluxo em vez de expulsar silenciosamente estado de autenticação. Veja [[17-DIAGNOSTICO-E-PLANO-DE-PRODUCAO-2026-07-27]].
## Controles dos módulos de operador e equipe — 2026-07-27

- `/api/operators/**` e `/api/teams/**` exigem sessão; o restante continua `deny-by-default`.
- IDs públicos UUID são usados nos contratos, enquanto IDs internos permanecem restritos ao backend.
- Atualizações de perfil/equipe usam versão otimista; username e vínculos ativos têm constraints e validações no servidor.
- Busca de operador aplica rate limit e projeção mínima, sem e-mail, nome real, telefone ou localização exata.
- Convite, aceite, recusa, saída e transferência validam proprietário/vínculo/função no service e registram auditoria.
- Nenhum token ou dado funcional é persistido em Web Storage; mutações usam cookie de sessão e CSRF.
- Upload de avatar continua bloqueado. Logo de equipe possui exceção controlada: somente capitão/gestor envia PNG/JPEG de até 2 MB e 2048 × 2048; o backend identifica, decodifica e reencoda a imagem, remove metadados e persiste bytes em tabela PostgreSQL separada. Nenhum arquivo usa o disco efêmero ou o webroot. Outros uploads continuam dependendo de object storage e controles adicionais.
- Pendente antes de usuários reais em escala: validar município pertencente à UF também no backend, executar as migrations em PostgreSQL isolado/backup e ampliar testes de integração de IDOR e concorrência.

## Sessão first-party no domínio oficial - 2026-07-28

O frontend oficial usa proxy same-origin da Vercel para `/api`, `/actuator`, `/oauth2` e `/login/oauth2`. Login por senha, CSRF, callback Google e restauração de sessão passam por `operadorzero.com.br`, evitando dependência de cookies de terceiros no domínio `onrender.com`. Cookies continuam `HttpOnly` quando aplicável, `Secure`, com CSRF dedicado; segredos Google e Resend permanecem somente no backend. Veja `AUTHENTICATION.md` e [[06-FRONTEND-WEB]].

Requisições mutáveis renovam o token CSRF e repetem a chamada uma única vez quando o servidor retorna `403 ACCESS_DENIED`. Isso recupera com segurança divergências entre o token mantido em memória e o cookie renovado por login, restauração de sessão ou outra aba, sem desabilitar CSRF nem ampliar permissões.

## Comunidade - 2026-07-28

[[14-COMUNIDADE]] usa leitura pública com projeção mínima e mutações autenticadas por sessão/CSRF. Autorizações de exclusão e moderação são verificadas no backend. Votos, salvos e chaves idempotentes possuem unicidade no PostgreSQL; SQL usa parâmetros e ordenações fixas. Conteúdo suspenso/excluído não entra no feed público. Imagens são limitadas a quatro por publicação, PNG/JPEG reais de até 2 MB, reencodificadas e armazenadas sem nome original ou metadados. Foto privada do perfil não é exposta: a interface usa avatar textual até existir consentimento específico.

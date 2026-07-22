# Segurança e privacidade

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

## Pendências antes de produção

Threat modeling revisado, testes BOLA/IDOR, restauração de backup, dependências auditadas, MFA administrativo, política jurídica para menores, DPO/canal de privacidade e avaliação do provedor.

## Comunidade

[[14-COMUNIDADE]] amplia riscos de assédio, discriminação, exposição de dados, fraude, conteúdo ilegal, denúncias abusivas e moderação indevida. Publicação exige análise no backend; automação não é prova; denunciante é protegido; decisões são motivadas e recorríveis; auditoria é imutável; ranking esportivo permanece separado. A abertura para menores e os termos dependem de revisão jurídica.

## Financeiro e publicidade

[[15-FINANCEIRO-E-PUBLICIDADE]] exige validação de webhook e montantes, idempotência ponta a ponta, ledger append-only, OAuth sem coleta de senha, segredos no servidor, MFA/reautenticação administrativa, conciliação e separação contábil. Pagamento nunca autoriza publicação sem moderação.

## Classificados

Anúncios, chat, propostas, documentos de procedência e imagens ampliam os riscos de fraude, PCE, abuso e exposição de dados. Upload em quarentena, remoção EXIF, autorização por objeto, limites progressivos, moderação humana e retenção justificada são gates obrigatórios. AEGs e marcadores usam fluxo especial somente textual, sem fotos por política preventiva, com prova de origem declarada e ativação real condicionada a parecer jurídico; as demais categorias sensíveis continuam desabilitadas. Veja [[08-CLASSIFICADOS]].

Procuras de equipamento reutilizam exatamente a mesma matriz de produtos, moderação e autorização. Dados públicos ficam limitados a callsign, cidade/UF, equipe, reputação e selos. Propostas, mensagens, encerramento, renovação e marcação como encontrado exigem autorização do proprietário no servidor, rate limit e auditoria. Veja [[CLASSIFICADOS-PROCURO-EQUIPAMENTO]].

## Operações

O módulo [[09-OPERACOES]] exige autorização por objeto para gestão, inscrições, missões, times, documentos e dados privados. PDF passa por quarentena e validação real. Local reservado, briefing privado e missão secreta nunca são enviados ao frontend sem autorização. Data, local, preço, regras, documentos, participantes, cancelamento e finalização geram auditoria e, quando relevante, notificação e novo aceite.

## Equipes

[[10-MINHA-EQUIPE]] exige autorização por equipe e impede escalada de privilégio. Convite respeita preferências, bloqueios, rate limit e vínculo ativo único. Troca de equipe é transacional e preserva histórico. Busca pública nunca usa CPF, telefone, e-mail ou nome completo privado. Logos seguem a política de upload seguro e não aceitam SVG na primeira versão.

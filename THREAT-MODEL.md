# Threat Model

| Ameaça | Ativo/vetor | Impacto / prob. | Prevenção e detecção | Resposta / residual |
|---|---|---|---|---|
| Invasor/bot | login, recovery, API | conta/DoS; alta | rate limit, respostas uniformes, MFA admin, métricas | bloquear/revogar; médio |
| Usuário malicioso | objetos alheios/BOLA | privacidade; alta | autorização por objeto e testes negativos | conter, auditar; baixo-médio |
| Fraude esportiva | desempenho/resultados | ranking; alta | procedência, confirmações, conflito, janela e anomalias | congelar ranking, revisar; médio |
| Admin comprometido | painel/tokens | sistêmico; médio | MFA, sessão curta, reauth, dual control e logs externos | revogar tudo/rotacionar; médio |
| Roubo de sessão | XSS/dispositivo | conta; médio | HttpOnly, CSP, rotação, detecção de risco | revogar sessões; baixo-médio |
| Upload malicioso | imagem poliglota | execução/abuso; médio | allowlist, sniffing, reencode, AV, bucket isolado | desativar uploads/remover; baixo |
| Vazamento do banco | credencial/rede | LGPD; médio | TLS, rede restrita, mínimo privilégio, criptografia/backup | conter, notificar, rotacionar; médio |
| Dependência/CI | pacote ou pipeline | supply chain; médio | lockfile, revisão, SCA, build protegido | rollback/rotacionar; médio |
| Hospedagem incorreta | debug/CORS/banco público | sistêmico; médio | checklist e IaC revisada | manutenção/correção; baixo-médio |

Revisar a cada mudança de arquitetura e antes de produção.


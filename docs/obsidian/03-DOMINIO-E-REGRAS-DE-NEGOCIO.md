# Domínio e regras de negócio

## Agregados principais

- User: credenciais, estado, confirmação, sessões e fatores MFA.
- OperatorProfile: callsign, exibição, cidade, função, privacidade e reputação.
- Team: membros, papéis, convites, preferências, vínculos com campo e histórico, conforme [[10-MINHA-EQUIPE]].
- Field: responsável, localização, estrutura, verificação e mapas.
- Operation: agenda, regras, vagas, participantes, check-in e encerramento.
- PerformanceRecord: autor, métricas, origem, evidências, status e validações.
- RankingSnapshot: escopo, período, fórmula versionada e posições reproduzíveis.
- Championship: formato, inscrições, confrontos, resultados e homologação.
- Achievement: definição versionada e concessão idempotente.
- Report/AuditEvent: moderação e rastreabilidade imutável.
- Classifieds: mural de anúncios, catálogo, conversa, proposta informativa, reputação e moderação, sem transação financeira, conforme [[08-CLASSIFICADOS]].
- Community Highlights: curadoria gratuita, vigência, região, posição, motivo, responsável e auditoria, conforme [[15-DESTAQUES-DA-COMUNIDADE]].

## Relacionamentos

Um usuário possui no máximo um perfil de operador; uma equipe possui muitos membros com histórico temporal; um campo possui mapas; uma operação usa um campo e opcionalmente um mapa; participações ligam operação, operador e equipe representada; desempenho pertence a uma participação; validações nunca sobrescrevem a origem, apenas acrescentam decisões.

## Estados críticos

Operação: `DRAFT -> PUBLISHED -> REGISTRATION_OPEN -> FULL -> IN_PROGRESS -> FINISHED` ou `CANCELLED`. Resultado: `PENDING -> SELF_DECLARED -> TEAM_CONFIRMED/ORGANIZER_CONFIRMED -> CONTESTED -> AUDITED/CORRECTED/REJECTED`.

## Ranking inicial (substituído)

As regras simplificadas abaixo permanecem apenas como registro histórico. O contrato vigente é a fórmula versionada, sensível à posição e baseada em confirmações descrita em [[11-RANKING]]. Nenhuma pontuação deve usar simultaneamente os dois modelos.

Base por partida: 10 participação confirmada + 3 por eliminação - 1 por morte + 8 por objetivo + 15 por vitória ou 5 por empate. Bônus: 10% organizador, 5% equipe, zero autodeclaração. Fator de experiência: 40% (1–2), 60% (3–5), 80% (6–10), 100% (>10). Penalidades são explícitas.

Toda pontuação armazena versão da fórmula, bruto, fator, bônus, penalidades e final. Recalcular somente após homologação ou job incremental. Mudanças de cidade têm janela de cooldown e auditoria.

## Invariantes

- Métrica nunca é aceita sem participação correspondente.
- Organizador não pode validar silenciosamente registro no qual tenha conflito não declarado.
- Uma concessão de conquista é única por definição, operador e evento gerador.
- Exclusão de conta anonimiza referências esportivas legítimas quando exigido.
- Callsign é público; nome completo, nascimento e localização precisa são privados por padrão.

Veja [[04-MODULOS-E-FLUXOS]] e `AUDIT-LOG-POLICY.md`.

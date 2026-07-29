# Módulos e fluxos

## Atualização de Operações — 2026-07-29

O fluxo persistente de [[09-OPERACOES]] inclui tamanho do jogo, times, esquadrões, funções locais, seleção transacional de vagas, chat geral e chat privado por time. O polling atualiza somente o canal aberto e não recarrega a página.

O perfil principal, a busca global e os controles por campo estão definidos em [[13-MEU-OPERADOR]]. Esse módulo reutiliza operações, equipe, ranking e conquistas; não deve duplicar seus catálogos ou regras.

## Perfis e permissões

Visitante, operador, capitão/admin de equipe, organizador de partida, administrador de campo, organizador de campeonato e administrador da plataforma. Papéis concedem capacidades; acesso a cada objeto também valida vínculo, estado e escopo.

## Fluxos do MVP

1. Cadastro por e-mail e senha -> confirmação obrigatória do endereço -> perfil privado -> publicação controlada.
2. Cadastro por e-mail: idade mínima -> e-mail e senha -> e-mail confirmado -> perfil privado -> publicação controlada.
3. Login: e-mail/senha -> verificação de estado e confirmação -> sessão segura em cookie.
4. Recuperação: e-mail -> resposta neutra -> token de uso único -> nova senha -> revogação das sessões anteriores.
5. Equipe: criação ou convite -> decisão explícita -> validação de responsabilidades -> vínculo principal -> papel -> histórico preservado, conforme [[10-MINHA-EQUIPE]].
6. Operação: rascunho -> publicação -> inscrição/espera -> times/esquadrões/missões -> check-in -> encerramento -> resultados, conforme [[09-OPERACOES]].
7. Desempenho: autodeclaração -> confirmação -> janela de contestação -> auditoria -> homologação.
8. Ranking: operação concluída e homologada -> registro elegível -> confirmação/contestação -> cálculo por posição e experiência -> snapshot versionado -> publicação, conforme [[11-RANKING]].
9. Denúncia: categoria -> evidência -> triagem -> decisão motivada -> recurso -> retenção.
10. Classificados: item usado permitido -> anúncio em análise -> publicação -> conversa/proposta informativa -> reservado/vendido -> avaliação, sob os gates de [[08-CLASSIFICADOS]].
11. Conquistas: evento válido -> atualização incremental -> avaliação de regra versionada -> concessão idempotente -> notificação agrupada -> destaque opcional, conforme [[12-CONQUISTAS-E-MEDALHAS]].
12. Comunidade: aceite versionado -> análise preventiva -> publicação/revisão -> interação -> denúncia -> decisão motivada -> recurso, conforme [[14-COMUNIDADE]].
13. Destaques da comunidade: seleção administrativa -> motivo e vigência -> publicação gratuita -> pausa/encerramento -> auditoria, conforme [[15-DESTAQUES-DA-COMUNIDADE]].

## Identidade implementada em 2026-07-22

Os fluxos 1 a 4 possuem contratos reais na API e na SPA. Cadastro por e-mail cria conta `PENDING_EMAIL`; o link de uso único ativa a conta. Login cria sessão opaca, logout a revoga e recuperação troca o hash Argon2id e revoga sessões anteriores. Contas antigas sem senha usam a recuperação para definir uma credencial sem perder dados. A interface autenticada recebe a identidade da sessão.

## Notificações

Convites, decisões de inscrição, mudanças/cancelamentos, lembretes, validações, contestações, conquistas e campeonato. Preferências por canal, agrupamento e idempotência evitam duplicidade.

## Fora do primeiro MVP

Chat privado geral, mapa interativo, automação completa de chaves e cadastro de menores com consentimento. O chat estritamente vinculado a anúncio pertence aos Classificados e exige os controles de abuso de [[08-CLASSIFICADOS]].

## Comunidade implementada em 2026-07-28

O fluxo 12 possui implementação persistente inicial em [[14-COMUNIDADE]]: leitura pública; criação/interações somente com sessão; chave idempotente; imagens controladas; comentários e respostas; voto único; salvos; denúncia confidencial; exclusão por autor ou moderação; suspensão, bloqueio de comentários e resolução administrativa auditada.

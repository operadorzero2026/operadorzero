# Módulos e fluxos

O perfil principal, a busca global e os controles por campo estão definidos em [[13-MEU-OPERADOR]]. Esse módulo reutiliza operações, equipe, ranking e conquistas; não deve duplicar seus catálogos ou regras.

## Perfis e permissões

Visitante, operador, capitão/admin de equipe, organizador de partida, administrador de campo, organizador de campeonato e administrador da plataforma. Papéis concedem capacidades; acesso a cada objeto também valida vínculo, estado e escopo.

## Fluxos do MVP

1. Cadastro por Google: consentimento OAuth/OIDC -> identidade validada -> idade mínima -> perfil privado -> publicação controlada.
2. Cadastro por e-mail: idade mínima -> e-mail e senha -> e-mail confirmado -> perfil privado -> publicação controlada.
3. Login: Google ou e-mail/senha -> avaliação de risco -> sessão segura em cookie.
4. Recuperação: e-mail -> resposta neutra -> token de uso único -> nova senha -> revogação das sessões anteriores.
5. Equipe: criação ou convite -> decisão explícita -> validação de responsabilidades -> vínculo principal -> papel -> histórico preservado, conforme [[10-MINHA-EQUIPE]].
6. Operação: rascunho -> publicação -> inscrição/espera -> times/esquadrões/missões -> check-in -> encerramento -> resultados, conforme [[09-OPERACOES]].
7. Desempenho: autodeclaração -> confirmação -> janela de contestação -> auditoria -> homologação.
8. Ranking: operação concluída e homologada -> registro elegível -> confirmação/contestação -> cálculo por posição e experiência -> snapshot versionado -> publicação, conforme [[11-RANKING]].
9. Denúncia: categoria -> evidência -> triagem -> decisão motivada -> recurso -> retenção.
10. Classificados: item usado permitido -> anúncio em análise -> publicação -> conversa/proposta informativa -> reservado/vendido -> avaliação, sob os gates de [[08-CLASSIFICADOS]].
11. Conquistas: evento válido -> atualização incremental -> avaliação de regra versionada -> concessão idempotente -> notificação agrupada -> destaque opcional, conforme [[12-CONQUISTAS-E-MEDALHAS]].
12. Comunidade: aceite versionado -> análise preventiva -> publicação/revisão -> interação -> denúncia -> decisão motivada -> recurso, conforme [[14-COMUNIDADE]].
13. Financeiro: pedido -> checkout -> webhook validado -> ledger -> comissão/split ou receita publicitária -> conciliação -> estorno/auditoria, conforme [[15-FINANCEIRO-E-PUBLICIDADE]].

## Notificações

Convites, decisões de inscrição, mudanças/cancelamentos, lembretes, validações, contestações, conquistas e campeonato. Preferências por canal, agrupamento e idempotência evitam duplicidade.

## Fora do primeiro MVP

Chat privado geral, mapa interativo, pagamentos integrados, automação completa de chaves e cadastro de menores com consentimento. O chat estritamente vinculado a anúncio pertence aos Classificados e exige os controles de abuso de [[08-CLASSIFICADOS]].

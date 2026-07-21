# Implantacao

Ambientes isolados: local, test, staging e production, cada um com banco, Redis, objetos, chaves e contas externas separados. Build gera artefatos imutaveis para SPA e API. O proxy termina TLS, aplica headers, limites e encaminha `/api`.

Pipeline esperado: lint -> testes frontend/backend -> analise de dependencias/segredos -> build -> migrations verificadas -> deploy staging -> smoke/integracao -> aprovacao -> backup -> deploy gradual production -> smoke -> monitoramento/rollback. Nunca executar Flyway clean em producao.


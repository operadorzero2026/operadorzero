# Observabilidade

A API ja prepara correlation ID, logs estruturados em producao, health/liveness/readiness e metricas Prometheus. Proximos passos: OpenTelemetry, dashboard de latencia/erros/saturacao, tracking de jobs/webhooks e alertas com runbook.

SLO inicial a validar: 99,5% mensal para API essencial; p95 abaixo de 500 ms fora de uploads; alerta por erro 5xx, falha de login anomala, fila parada, webhook atrasado, backup falho e espaco/conexoes do banco. Logs devem ser sanitizados e ter acesso minimo.


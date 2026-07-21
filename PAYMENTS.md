# Pagamentos

O backend e a unica autoridade. Criacao de cobranca usa idempotency key; valores e beneficiario sao recalculados no servidor. O ledger local registra intencao, transacao, taxa, estorno e reconciliacao sem armazenar dados de cartao.

Webhooks sao autenticados conforme o provedor, persistidos com identificador unico, respondidos rapidamente e processados de modo idempotente. Estados financeiros nao dependem de retorno do navegador.


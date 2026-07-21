# Rate Limit Policy

Limites iniciais por IP e identificador normalizado, ajustáveis após observação: login 5/15 min por conta e 20/15 min por IP; recuperação 3/h por conta e 10/h por IP; cadastro 5/h por IP; confirmação 10/h; busca pública 60/min; API autenticada 120/min; upload 10/h; denúncia 10/dia; desempenho 30/dia. Usar atrasos progressivos, `429`, `Retry-After`, métricas e bloqueio temporário sem bloqueio permanente explorável. Nunca revelar existência da conta.


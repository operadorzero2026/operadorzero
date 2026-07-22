# Deploy da API no Render

## Blueprint de staging

`render.yaml` declara somente recursos gratuitos de teste: API Docker, PostgreSQL e Key Value. O Blueprint usa a branch `deploy/render-vercel`, deploy apos checks aprovados e health check em `/actuator/health`.

Na criacao do Blueprint, informe `CORS_ALLOWED_ORIGINS` com as origens HTTPS exatas da Vercel, separadas por virgula. Banco e Redis sao injetados por referencias internas; nao copie suas credenciais para o frontend, logs ou GitHub.

## Limitacoes do ambiente gratuito

Recursos gratuitos servem apenas para demonstracao/staging: podem suspender, expirar, perder dados ou nao oferecer backups e garantias adequadas. Nao os trate como producao nem migre dados reais para eles.

## Validacao

1. Confirmar que o workflow CI passou.
2. Criar o Blueprint pelo painel Render a partir deste repositorio.
3. Conferir o build Docker e a migration Flyway.
4. Verificar `/actuator/health`, logs sem segredos e CORS somente para origens aprovadas.
5. Executar smoke tests da Vercel para a API.

Producao exige plano, backup e restauracao testados, observabilidade e aprovacao explicita de custos.

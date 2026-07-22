# Deploy da web na Vercel

O projeto Vercel deve apontar para a raiz do repositorio. `vercel.json` seleciona Vite, gera `dist`, preserva rotas SPA e aplica headers de seguranca.

## Variavel publica

- `VITE_API_URL`: URL HTTPS publica da API no Render, sem barra final.
- `VITE_APP_ENV=staging`: exibe o aviso permanente de ambiente descartavel.
- `VITE_AUTH_ENABLED=false`: mantem Google e formularios remotos desabilitados ate os endpoints reais existirem.

Nenhum segredo pode usar prefixo `VITE_`. Segredos de Google, banco, Redis, e-mail, storage e pagamentos pertencem exclusivamente ao backend/Render.

## Ordem segura

1. Publicar e validar primeiro a API de staging no Render.
2. Criar o projeto Vercel a partir do GitHub e definir `VITE_API_URL` apenas para Preview.
3. Fazer deploy da branch `deploy/render-vercel`.
4. Validar headers, HTTPS, rotas SPA e ausencia de credenciais no bundle.
5. Adicionar o dominio Vercel exato a `CORS_ALLOWED_ORIGINS` no Render.
6. Somente promover para Production apos os gates do checklist.

O plano Hobby e destinado a uso pessoal e nao comercial. Como o produto inclui classificados e um modulo financeiro planejado, a escolha de plano e a publicacao comercial exigem decisao explicita do responsavel; esta preparacao nao autoriza contratacao.

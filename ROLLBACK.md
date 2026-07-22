# Rollback

## Frontend

Promova na Vercel o ultimo deployment aprovado. Depois valide landing, rotas SPA, headers e comunicacao com a API. Nao reutilize uma build cujo contrato de API seja incompativel.

## API

No Render, faca rollback para a imagem/commit anterior somente se a migration aplicada for retrocompativel. O Flyway nunca executa `clean` em producao.

## Banco

Migrations destrutivas exigem estrategia expand/contract. Se dados forem afetados, interrompa escrita, preserve evidencias e restaure o backup validado conforme `BACKUP-RESTORE.md`; nao improvise `DROP`, `TRUNCATE` ou reversao manual em producao.

Todo rollback deve registrar commit, horario, responsavel, motivo, impacto, validacao e pendencias.

# Segurança dos Classificados

- autorização backend por proprietário, conversa, denúncia e papel administrativo;
- UUID não substitui autorização; testes BOLA/IDOR são obrigatórios;
- rate limit por conta/IP/risco para anúncios, mensagens, propostas, denúncias e uploads;
- JPEG/PNG/WebP validados por conteúdo, reprocessados, sem EXIF, fora do webroot e em quarentena;
- SVG, HTML, scripts, executáveis, compactados e extensão dupla bloqueados;
- texto limitado e codificado, URLs validadas, queries parametrizadas e CSRF conforme sessão;
- auditoria append-only, correlação, alerta de abuso e preservação de evidências;
- segredo, documento e conversa nunca entram em logs comuns.

Conecta-se a [[05-SEGURANCA-E-PRIVACIDADE]] e `FILE-UPLOAD-POLICY.md`.

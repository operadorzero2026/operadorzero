# Seguranca de uploads

Nenhum upload deve atravessar a API como arquivo publico direto. Fluxo: autorizacao -> URL assinada curta -> bucket de quarentena -> limite/tipo esperado -> MIME e assinatura real -> antivirus -> normalizacao de imagem -> nome aleatorio -> bucket privado -> entrega assinada/CDN.

Bloquear executaveis, arquivos ativos, dupla extensao e SVG/HTML nao sanitizado. Remover metadados quando adequado, definir quota e registrar autor/objeto/hash/status. A regra dos classificados que proibe foto de AEG/marcador deve ser validada tambem no backend.


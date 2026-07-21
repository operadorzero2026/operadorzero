# Procuro equipamento

Esta modalidade pertence ao mesmo módulo [[08-CLASSIFICADOS]] e compartilha publicação, categoria, localização, moderação, favoritos, conversa, denúncia e auditoria com anúncios de venda. O discriminador previsto é `ClassifiedListingType = SELLING | WANTED`; não deve existir um segundo módulo isolado.

## Protótipo atual

- abas `Todos`, `À venda` e `Itens procurados`;
- selos visuais `À venda` e `Procuro`;
- formulário alternável entre `Quero vender` e `Estou procurando`;
- campos de marca, modelo, fabricante, plataforma, encaixe, versão, material, capacidade, alimentação/voltagem, medidas, cor e observações de compatibilidade;
- condição, quantidade, faixa de preço, envio/retirada, validade e urgência;
- página detalhada demonstrativa com perfil público minimizado, reputação, status, propostas e aviso jurídico;
- `Tenho este item`, proposta rápida e conversa vinculada;
- respostas rápidas e sugestão de possível correspondência;
- nenhuma persistência, mensagem, imagem, proposta, notificação ou transação real.

## Regras

As mesmas matrizes de [[CLASSIFICADOS-PRODUTOS-PERMITIDOS]] e [[CLASSIFICADOS-PRODUTOS-PROIBIDOS]] valem para procuras. Telefone, e-mail, CPF, nome completo, endereço exato e dados bancários não são públicos. O contato inicial ocorre no chat.

Imagens de referência são opcionais em categorias comuns e bloqueadas para AEGs/marcadores pela política preventiva existente. Em produção, qualquer upload depende de quarentena, remoção EXIF, validação real do conteúdo, limite e moderação descritos em [[CLASSIFICADOS-SEGURANCA]].

Correspondência automática é sugestão. A interface deve dizer: **Possível correspondência. Confirme modelo, medidas e compatibilidade diretamente com o outro usuário.**

## Modelo futuro

Reutilizar `ClassifiedListing` para campos comuns e adicionar detalhes específicos em `WantedListingDetails`/`WantedListingCompatibility`. Propostas, correspondências e notificações podem usar `WantedListingOffer`, `WantedListingMatch` e `WantedListingNotification`, vinculadas por IDs internos e sempre autorizadas pelo proprietário no backend.

Status previstos: `DRAFT`, `PENDING_REVIEW`, `ACTIVE`, `RECEIVING_OFFERS`, `NEGOTIATING`, `FOUND`, `PAUSED`, `EXPIRED`, `REJECTED` e `REMOVED`.

Aceitar uma proposta ou marcar `Item encontrado` não cria pagamento nem obrigação financeira. O histórico deve permanecer auditável.

## Gates de produção

- migration compatível com anúncios atuais;
- autorização por objeto/tenant em procura, proposta, mensagem, renovação e encerramento;
- validação server-side de enumerações, limites e produtos proibidos;
- rate limit, anti-spam, deduplicação, varredura de links e moderação;
- expiração e renovação limitadas por job idempotente;
- notificações sem revelar dados privados;
- trilha de auditoria para mudanças críticas;
- testes de IDOR/BOLA, concorrência, expiração, moderação e retenção.

Veja [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]] e [[99-HISTORICO-DE-ALTERACOES]].

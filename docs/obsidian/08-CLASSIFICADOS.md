# Classificados Operador Zero

## Localizacao do anuncio

Nova publicacao e proposta exigem primeiro Estado e depois Cidade. A lista abrange as 27 UFs e consulta os municipios da UF na API oficial do IBGE. A troca de UF invalida a cidade anterior. A mesma combinacao devera ser validada no backend antes de persistir ou usar a localizacao para priorizacao.

> [!warning] Mural gratuito, sem intermediação
> O Operador Zero apenas oferece o espaço de anúncio e conversa. Não recebe pagamentos ou comissão, não garante produto, entrega, procedência ou negociação e não é parte do acordo feito no chat.

## Interface demonstrativa atual

O usuário conectado pode buscar anúncios e filtrar por tipo, estado do item (`Novo` ou `Usado`), faixa de preço e localização. Condições como `Sem uso` entram em `Novo`; `Pouco usado`, `Usado` e `Com marcas de uso` entram em `Usado`. A opção padrão `Perto de você` prioriza a localização declarada no perfil; no protótipo, usa `Curitiba, PR` como dado fictício. Nenhuma geolocalização do aparelho é coletada.

Favoritos, anúncios e mensagens existem somente em memória. Nenhum dado, arquivo, proposta ou pagamento é persistido ou enviado.

Na criação de publicação `Quero vender`, o anunciante deve selecionar obrigatoriamente o estado `Novo` ou `Usado`. Também precisa confirmar que o estado informado é verdadeiro, que é proprietário legítimo e possui nota fiscal ou prova idônea de origem. Item novo inclui declaração de venda ocasional, sem estoque comercial; item usado exige transparência sobre conservação e avarias.

## Modalidade Procuro

O mural também possui a modalidade `Estou procurando`, integrada aos anúncios de venda. Ela inclui aba própria `Itens procurados`, formulário de compatibilidade, detalhes públicos minimizados, botão `Tenho este item`, proposta rápida, chat vinculado e correspondência apenas sugestiva. O protótipo não persiste publicação, proposta, conversa, expiração ou notificação. Regras, estados, modelo compartilhado e gates estão em [[CLASSIFICADOS-PROCURO-EQUIPAMENTO]].

## Categorias e AEGs/marcadores

As categorias comuns incluem vestuário, proteção, baterias, carregadores, equipamentos de campo e acessórios esportivos sem controle específico.

AEGs e marcadores formam uma categoria especial e controlada no protótipo:

- anúncio somente textual, exibindo nome do equipamento e acessórios que acompanham;
- fotos bloqueadas por política preventiva do Operador Zero, adotada por cautela jurídica, segurança e moderação — não como afirmação de proibição legal expressa;
- declaração obrigatória de nota fiscal ou documento idôneo de origem lícita;
- interesse encaminhado ao chat, sem pagamento ou fechamento pela plataforma;
- liberação em produção condicionada a parecer jurídico e moderação humana.

Peças internas, canos, mecanismos, cilindros de alta pressão, itens modificados ou controlados permanecem desativados. Armas de fogo, munição real, explosivos, itens roubados, falsificados, adulterados, sem procedência ou destinados a conversão ilegal são proibidos.

## Conversa e negociação segura

Antes de iniciar a conversa, a interface informa que pagamento, entrega, transporte, conferência e documentação são responsabilidades diretas dos usuários. Recomenda:

- comunicação clara e respeitosa;
- encontro diurno em local público e movimentado, acompanhado ou comunicado a pessoa de confiança;
- inspeção do item e dos acessórios antes de qualquer pagamento;
- não compartilhar senhas, códigos ou documentos desnecessários;
- comprar e vender AEGs ou marcadores somente com nota fiscal ou prova idônea de origem.

## Gate de produção

Exige backend real, autenticação, verificação de e-mail, telefone e idade, autorização por objeto, moderação operante, termos revisados por advogado, aceite versionado, canal LGPD, auditoria, resposta a incidentes, backup/restauração e parecer jurídico específico para a categoria controlada.

Veja [[CLASSIFICADOS-PROCURO-EQUIPAMENTO]], [[CLASSIFICADOS-RISCOS-JURIDICOS]], [[CLASSIFICADOS-PRODUTOS-PERMITIDOS]], [[CLASSIFICADOS-PRODUTOS-PROIBIDOS]], [[CLASSIFICADOS-MODERACAO]], [[CLASSIFICADOS-PRIVACIDADE]], [[CLASSIFICADOS-SEGURANCA]], [[CLASSIFICADOS-TERMOS-RASCUNHO]] e [[CLASSIFICADOS-RESPOSTA-A-INCIDENTES]].

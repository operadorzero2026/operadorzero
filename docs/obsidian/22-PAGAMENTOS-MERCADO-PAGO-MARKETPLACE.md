# Pagamentos: Mercado Pago Marketplace (Split 1:1)

Atualizado em 2026-11-03.

## Como o dinheiro anda

1. O organizador conecta a conta Mercado Pago em **Financeiro → Configurações de recebimento**. Usa OAuth Authorization Code com PKCE S256.
2. O jogador aprovado toca em **Pagar com Mercado Pago**. O servidor cria uma preferência do Checkout Pro (`POST /checkout/preferences`) com o **token OAuth do organizador**, o valor da inscrição e o `marketplace_fee`, a comissão em reais.
3. No Mercado Pago, o jogador escolhe o meio de pagamento (Pix, cartão e os demais meios habilitados na conta do organizador).
4. O Mercado Pago cobra. Primeiro desconta a tarifa dele da parte do organizador; depois transfere o `marketplace_fee` para a conta da plataforma, que é a dona da aplicação. O restante fica com o organizador.
5. A notificação (webhook) chega assinada. O servidor consulta o pagamento na API com o token do organizador e confere referência, valor e conta recebedora. Só então marca como pago e libera os documentos.

O Pix direto da Etapa F continua existindo. Ele cai na conta da plataforma, sem divisão, e só aparece quando o organizador **não** conectou a conta e `MERCADOPAGO_ACCESS_TOKEN` está preenchido.

## Comissão: o que é possível com o split nativo

- `marketplace_fee` é um **valor fixo em reais**, definido ao criar a cobrança. A API recusa um valor maior que o total.
- Ao criar a preferência, o meio de pagamento ainda não foi escolhido: o jogador escolhe no checkout. Por isso a tarifa real ainda é desconhecida. A tarifa também depende do meio, do parcelamento, do prazo de recebimento e das condições da conta do organizador.
- Por isso **não dá para garantir "10% do líquido"** com o split nativo. O sistema calcula a comissão sobre o **valor bruto** (percentual configurado × preço, arredondado para baixo) e guarda em cada pagamento o percentual e o valor aplicados.
- Depois da aprovação, o sistema guarda a tarifa efetiva (`fee_details`), a comissão cobrada e o líquido do organizador. Isso é **conciliação**: registra o que aconteceu, não muda a divisão.
- **Decisão do responsável (2026-11-03): comissão padrão de 7% sobre o valor bruto da inscrição.** Exemplo: inscrição de R$ 100,00 → R$ 7,00 para o Operador Zero; o Mercado Pago fica com a tarifa dele, descontada da parte do organizador, que recebe o restante (R$ 93,00 menos a tarifa). Transferências posteriores entre contas não são feitas. O administrador pode mudar o padrão ou dar percentuais individuais em Financeiro.

## Configuração no Mercado Pago Developers

Aplicação existente: **Operador Zero**, Checkout Pro, API de Preferences, Marketplace Split 1:1.

- **URL de redirecionamento (OAuth):** `https://operadorzero.com.br/api/payments/mercadopago/callback`. A rota existe a partir desta versão; só funciona depois da publicação.
- **PKCE:** habilitado. O app sempre envia `code_challenge` e `code_challenge_method=S256`.
- **Escopos:** leitura, escrita e acesso offline. O acesso offline é obrigatório para o `refresh_token`.
- **Webhooks:** URL `https://operadorzero.com.br/api/payments/mercadopago/webhook` com o evento **Pagamentos**. Copie a assinatura secreta para `MERCADOPAGO_WEBHOOK_SECRET`.
- **Conta do organizador:** precisa ser de vendedor, com identificação KYC nível 6 e o app do Mercado Pago, conforme os pré-requisitos do Split 1:1.

## Variáveis de ambiente (`deploy/vps/.env`)

| Variável | De quem | Para quê |
|---|---|---|
| `MERCADOPAGO_CLIENT_ID` | plataforma (aplicação) | OAuth dos organizadores |
| `MERCADOPAGO_CLIENT_SECRET` | plataforma (aplicação) | OAuth dos organizadores (só no servidor) |
| `MERCADOPAGO_WEBHOOK_SECRET` | plataforma (aplicação) | assinatura das notificações |
| `MERCADOPAGO_ENVIRONMENT` | — | `test` (contas e credenciais de teste) ou `production` |
| `PLATFORM_DEFAULT_COMMISSION_PERCENT` | — | comissão padrão até o administrador gravar outra (7) |
| `MERCADOPAGO_ACCESS_TOKEN` | plataforma (opcional) | Pix direto sem divisão (Etapa F) |

Os tokens de cada organizador **não** são variáveis de ambiente. Ficam cifrados (AES-GCM) em `mercadopago_connection`, com chave derivada de `AUTH_HASH_KEY`. Se `AUTH_HASH_KEY` for trocada, os organizadores precisam reconectar.

Não é preciso `MERCADOPAGO_PUBLIC_KEY`: o Checkout Pro abre pela URL devolvida pelo servidor, sem SDK no site.

## Banco

- `V37__mercadopago_marketplace.sql`:
  - tabelas `mercadopago_connection`, `mercadopago_oauth_attempt`, `platform_commission` e `payment_notification`;
  - colunas novas em `operation_payment` (provedor, organizador, conta recebedora, preferência, meio, comissão prevista e efetiva, tarifa, reembolsado, líquido, tarifas confirmadas);
  - novos estados: `PARTIALLY_REFUNDED` e `CHARGEBACK`.
- `V38__payment_status_length.sql`: amplia a coluna de status.

## Reembolsos

- Nunca são automáticos. O organizador pede em **Pagamentos → Reembolsar pelo Mercado Pago**, total ou parcial.
- O Mercado Pago debita o organizador e a plataforma na proporção de cada um. Sem saldo, ele recusa e nada muda.
- Pagamentos manuais e Pix direto continuam com "Registrar estorno": o sistema só anota.

## Pendente de validação real

Estes pontos só podem ser confirmados com contas de teste do Mercado Pago, porque o teste local usa um simulador:

- o nome exato dos tipos em `fee_details`;
- o comportamento do `marketplace_fee` com Pix;
- o retorno do `/oauth/token` com `code_verifier`;
- a notificação com `user_id` do vendedor.

# Financeiro e publicidade

Contrato do módulo de monetização, conectado a [[09-OPERACOES]], [[08-CLASSIFICADOS]], [[10-MINHA-EQUIPE]] e [[05-SEGURANCA-E-PRIVACIDADE]]. A interface atual é demonstrativa: não cria cobrança, Pix, token, split, webhook ou repasse.

## Diagnóstico

O repositório contém somente React/Vite. Não há backend, banco, Mercado Pago, contas financeiras, administração ou fiscal. Por isso, migrations e endpoints abaixo são projeto de implementação, não recursos ativos.

## Arquitetura financeira

Criar um módulo `finance` no monólito Spring Boot com serviços de pagamentos, ledger, comissões, publicidade, conciliação, estornos e auditoria. Operações e Classificados publicam comandos/eventos; nunca gravam diretamente tabelas financeiras. Valores usam `BIGINT` em centavos ou `NUMERIC`, nunca `float`/`double`.

Fluxos:

1. Inscrição paga: pedido imutável -> checkout sandbox -> evento externo -> validação/consulta no provedor -> lançamento no ledger -> comissão/split -> conciliação -> repasse.
2. Publicidade: campanha -> plano e preço versionados -> criativo -> checkout integral da plataforma -> confirmação -> análise -> agenda -> publicação identificada -> métricas.
3. Estorno/chargeback: evento -> bloqueio de saldo/veiculação -> lançamentos reversos -> conciliação -> notificação -> auditoria.

## Split de operações

`liquido = bruto - taxas_confirmadas - ajustes_confirmados`

`comissao = arredondamento_em_centavos(liquido * 10 / 100)`

`recebivel_organizador = liquido - comissao`

A taxa de 10% deve ser configuração versionada e imutável no pedido. O cliente nunca envia comissão calculada. O backend recalcula e valida montante/moeda/operação. Publicidade é receita integral da plataforma, sem recebível de organizador.

Segundo a documentação oficial consultada em 2026-07-21, o Split 1:1 do Mercado Pago requer OAuth do vendedor e suporta Checkout Pro ou Checkout Transparente. A taxa do marketplace é informada por `marketplace_fee` (Pro) ou `application_fee` (Transparente). O modelo e disponibilidade devem ser revalidados na contratação; 1:N possui restrições comerciais. Reembolsos no 1:1 podem depender de saldo das partes.

Referências oficiais: [Split 1:1](https://www.mercadopago.com.br/developers/pt/docs/split-payments/split-1-1/integration-configuration/integrate-marketplace), [pré-requisitos](https://www.mercadopago.com.br/developers/pt/docs/split-payments/split-1-1/prerequisites) e [OAuth](https://www.mercadopago.com.br/developers/pt/docs/split-payments/split-1-1/additional-content/security/oauth/introduction).

## Modelo de dados e migrations

Entidades: `PlatformRevenue`, `OperationCommission`, `OperationPaymentSplit`, `AdvertisingPlan`, `AdvertisingCampaign`, `AdvertisingPlacement`, `AdvertisingTargeting`, `AdvertisingCreative`, `AdvertisingPayment`, `AdvertisingSchedule`, `AdvertisingImpression`, `AdvertisingClick`, `AdvertisingConversion`, `AdvertisingModeration`, `AdvertisingRefund`, `MercadoPagoAccountConnection`, `MercadoPagoPayment`, `MercadoPagoWebhookEvent`, `FinancialLedgerEntry`, `FinancialReconciliation` e `FinancialAuditLog`.

Migrations planejadas:

1. `V031__financial_ledger_gateway_events.sql`
2. `V032__operation_commissions_splits.sql`
3. `V033__advertising_plans_campaigns.sql`
4. `V034__advertising_creatives_targeting_schedule.sql`
5. `V035__advertising_metrics_moderation_refunds.sql`
6. `V036__financial_reconciliation_audit.sql`

Tipos: `OPERATION_COMMISSION`, `ADVERTISING_REVENUE`, `ADVERTISING_REFUND`, `OPERATION_REFUND`, `GATEWAY_FEE`, `ORGANIZER_RECEIVABLE`, `CHARGEBACK` e `FINANCIAL_ADJUSTMENT`.

## Endpoints propostos

- `POST /api/v1/operations/{id}/checkout` e `GET /api/v1/operation-payments/{id}`
- `GET /api/v1/organizers/me/mercado-pago/connect` e callback OAuth com `state`/PKCE
- `POST /api/v1/payments/mercado-pago/webhooks` sem sessão, mas com assinatura/origem validadas
- `GET|POST /api/v1/advertising/campaigns`, `PATCH /{id}`, `/submit`, `/pause`, `/renew`
- `GET /api/v1/advertising/plans`, `/metrics`, `/receipts`
- `/api/v1/admin/finance/*` e `/api/v1/admin/advertising/*` com RBAC, MFA e reautenticação.

O webhook responde rápido, grava evento idempotente e processa em fila. Antes de mutar estado, consulta o objeto no provedor e compara conta, ambiente, moeda, valor e referência interna. A documentação oficial oferece URL de teste e assinatura secreta `x-signature`; eventos de QR Code têm exceções documentadas que precisam ser tratadas conforme o produto escolhido. Veja [Webhooks oficiais](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks).

## Planos e publicidade

Planos administráveis preservam versão de nome, preço, duração, alcance, posições, capacidade, tipo, prioridade, regras e métricas. Iniciais: Regional 7/15 dias, Estadual 7 dias, Nacional 7 dias, Destaque de operação, Destaque de equipamento e Banner principal.

Todo anúncio exibe `Patrocinado`, fica separado do orgânico e não altera silenciosamente sua cronologia. Limites iniciais: um banner, cinco operações, oito equipamentos, frequência por usuário e rotação por anunciante. Segmentação usa cidade/região/estado/país, modalidade e interesses consentidos; nunca raça, religião, orientação sexual, saúde, denúncia, avaliação secreta ou reputação negativa.

AEGs e marcadores continuam sob a regra preventiva de [[08-CLASSIFICADOS]]: sem foto, inclusive em patrocínio. Imagem patrocinada de equipamento só é permitida para categorias liberadas.

## Moderação e estados

`DRAFT -> AWAITING_PAYMENT -> PAYMENT_PENDING -> PAID -> IN_REVIEW -> CHANGES_REQUESTED|APPROVED|REJECTED -> SCHEDULED -> PUBLISHED -> PAUSED|ENDED|CANCELLED|REFUNDED`.

Pagamento não publica. Moderação valida conteúdo, produto, operação, anunciante, imagem, link, direitos, qualidade e região. Rejeição informa motivo e opções de correção, crédito, reagendamento ou estorno conforme política aceita.

## Segurança

- Credenciais e refresh tokens criptografados no servidor/secret manager; nunca frontend ou log.
- OAuth sem senha do vendedor, com `state`, PKCE quando aplicável, redirect exato e vínculo autenticado.
- Idempotência para pedido, checkout, webhook, ledger, split, estorno e publicação.
- MFA/reautenticação e dupla checagem para preço, comissão, ajuste, estorno, posição e conciliação.
- Upload em quarentena, MIME real, JPEG/PNG/WebP, remoção EXIF, antivírus e moderação.
- Métricas agregadas com consentimento, retenção e proteção contra fraude; sem promessa de venda/inscrição.
- Auditoria append-only com ator, entidade, antes/depois, motivo, identificador externo e correlação.

## Jurídico e tributário

Texto-base: “O organizador é o responsável pela realização, segurança, estrutura, regras, autorizações e execução da operação. O Operador Zero atua como plataforma tecnológica de divulgação, inscrições e integração de pagamentos, respondendo pelos serviços que efetivamente prestar e pelas obrigações que legalmente lhe forem aplicáveis.”

Antes da produção, jurídico e contabilidade devem revisar relação de consumo, marketplace, responsabilidade, política de cancelamento/estorno, publicidade, direitos de imagem, LGPD, notas fiscais e retenção. Contabilidade deve separar receita de comissão, receita publicitária, valores de terceiros, taxas, estornos e chargebacks; arrecadação bruta de operações não é receita integral da plataforma.

## Interface entregue

- Menu Financeiro com resumo, comissões, publicidade, pagamentos, conciliação e relatórios.
- Simulador de split em centavos, lançamentos separados, conciliação, campanhas e checkout sandbox explícito.
- Visão Geral com Destaques patrocinados claramente identificados e separados das operações orgânicas.
- Fluxo responsivo de campanha e escolha Pix/cartão sem transmissão de dados.

## Pendências

- Confirmar elegibilidade comercial e modelo exato do Split 1:1 com Mercado Pago.
- Criar backend, migrations, sandbox, OAuth, checkout, webhooks, ledger, conciliação e testes de integração.
- Definir CNPJ/contratos, emissão fiscal, política de estorno, suporte e operação de chargebacks.
- Fazer threat model financeiro, pentest e ensaio de reconciliação/rollback antes de produção.

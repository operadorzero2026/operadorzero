# Relatório final: operações completas (módulos 1 a 21)

Data: 2026-11-03. Tudo abaixo está publicado em https://operadorzero.com.br (main `0cd780d`). O banco de produção está na versão 38 e o backup foi feito antes de cada publicação.

## 1. O que foi implementado

| Etapa | Módulos | Entrega | Commit |
|---|---|---|---|
| A | 2, 3, 4, 5, 6, 7 | Inscrição em lista única ou por equipes; funções Assalto, Sniper e DMR, com Médico e Rádio opcionais e combináveis; contadores e filtros; encerramento no horário de início, encerrar e reabrir; cancelamento e nova inscrição; remoção com motivo e autorização de retorno; troca de equipe só com aprovação | `dbbe1f5` |
| B | 1, 11, 13, 14, 15 | Capa compacta (+, miniatura, trocar, remover); briefing com "Ver mais / Ver menos"; PDFs protegidos (só inscritos aprovados, bloqueados após o início e, em operação paga, só com pagamento confirmado); aviso de documento novo; página pública com prévia para compartilhar; pesquisa; operações realizadas no perfil | `cd940ae` |
| C | 8, 9, 10 | Chat geral em tempo real (SSE, com consulta a cada 10 s se cair); só organizador e inscritos aprovados; excluir mensagens, silenciar, comunicados fixados; encerra 7 dias após o jogo, com histórico; layout para iPhone que acompanha o teclado | `95c7ad5` |
| D | 12 | Marcar operação ao publicar no feed, mesmo sem estar inscrito; galeria "Fotos e vídeos" (logada e pública); o organizador desvincula publicações | `2154d60` |
| E | 16, 17 | "Este e-mail já possui uma conta"; erros claros junto ao botão; pop-up de conta criada; o link de confirmação já entra na conta e volta para a operação; reenvio da confirmação; boas-vindas no primeiro acesso com Google | `1eb8891` |
| F | 18 | Status financeiro separado da inscrição; Pix (Etapa F); confirmação manual; registro de estorno; histórico financeiro que nunca é apagado | `92b30a8` |
| Marketplace | 18 | Mercado Pago Split 1:1: cada organizador conecta a própria conta (OAuth PKCE); Checkout Pro com comissão de 7% sobre o bruto; webhooks assinados (modo de teste e de produção); conciliação; reembolso pelo organizador; telas Financeiro do organizador e do administrador, com CSV | `cc862fd`, `da390d0`, `0cd780d` |

Segurança (módulo 19): todas as regras são conferidas no servidor. Os testes cobrem:
- acesso indevido ao chat e aos PDFs;
- PDFs sem pagamento ou depois do início;
- troca de equipe sem aprovação;
- inscrição duplicada;
- retorno de removido sem autorização;
- mensagem de jogador silenciado;
- inscrição depois do encerramento;
- permissões alteradas por quem não pode.

## 2. Arquivos alterados (principais)

- **Servidor, pacote `operation`:** `OperationRegistration*`, `OperationDocument*`, `OperationPage*`, `OperationChat*`, `OperationGalleryController`, `CallUpController`.
- **Servidor, outros pacotes:**
  - `payment`: pacote novo inteiro;
  - `identity`: `AuthService`, `AuthController`, `AuthException`;
  - `feed`: `FeedPostService`, `FeedPostRepository`;
  - `SecurityConfig`.
- **Site:**
  - `src/operation/*` (inscrição, página, documentos, chat, galeria, pagamento);
  - `src/FinancePage.tsx`, `src/AuthFeedback.tsx`, `src/authRules.ts`;
  - `App.tsx`, `api.ts`, `feed/CreateFlow.tsx`.
- **Implantação:** `deploy/vps/compose.yml`, `.env.example`, `README.md`, `backup.sh` (inclui os PDFs privados).
- **Documentação:**
  - entradas por etapa em `99-HISTORICO-DE-ALTERACOES.md`;
  - `22-PAGAMENTOS-MERCADO-PAGO-MARKETPLACE.md`.

## 3. Banco de dados

Todas as migrações apenas acrescentam; nenhum dado existente foi perdido.

| Migração | Conteúdo |
|---|---|
| V33 | Modelo de inscrição, funções, remoção e retorno, histórico da inscrição, trocas de equipe |
| V34 | Documentos PDF da operação (arquivos fora da pasta pública) |
| V35 | Comunicados fixados e silenciamento no chat |
| V36 | Pagamentos e histórico de cada pagamento |
| V37 | Conexões Mercado Pago, tentativas OAuth, comissões, notificações, campos do split |
| V38 | Coluna de status maior (`PARTIALLY_REFUNDED`) |

## 4. Testes executados

- **Servidor:** 293 testes automatizados passando.
- **Testes de ponta a ponta contra a API local:**

  | Área | Verificações |
  |---|---|
  | Inscrições | 28 |
  | Página, PDFs e capa | 19 |
  | Chat (evento em tempo real em 0,01 s) | 20 |
  | Galeria e marcação | 11 |
  | Cadastro e login | 12 |
  | Pix | 25 |
  | Marketplace (Mercado Pago simulado) | 55 |

- **Navegador, em tela de celular:**
  - editor de Recon, capa, briefing, chat com teclado aberto e fechado;
  - pop-ups de cadastro, conexão OAuth e telas de pagamento.
- **Produção, depois de cada publicação:**
  - 5 tentativas de acessar PDFs por caminho indireto, todas bloqueadas;
  - cabeçalhos do tempo real;
  - simulação de webhook do Mercado Pago com resposta 200 nos dois modos;
  - rotas protegidas recusando acesso sem login.

## 5. Problemas encontrados e corrigidos

- **Proxy:** o nginx respondia 301 no `POST /api/media/uploads`. Corrigido com rotas exatas; os PDFs sobem pelo prefixo que aceita 9 MB.
- **CSS:** o estilo global de `footer` vazava para o editor e para os botões do Recon. Os componentes passaram a usar `div`.
- **Botão Voltar do celular:** no modo de desenvolvimento do React (StrictMode) ficavam entradas antigas no histórico. Corrigido com marcadores de profundidade.
- **Servidor:**
  - os blocos de texto do Java removem o espaço no fim da linha, o que quebrava o SQL;
  - parâmetro nulo sem tipo no Postgres;
  - estado `PARTIALLY_REFUNDED` maior que a coluna (V38).
- **Mercado Pago:**
  - na primeira gravação, o Client Secret estava com o valor errado;
  - cada modo dos webhooks tem a própria assinatura secreta, e as duas passaram a ser aceitas.

## 6. Pendências

| Pendência | Depende de | O que fazer |
|---|---|---|
| Conectar organizadores ao Mercado Pago | Mercado Pago | A autorização mostra "O aplicativo não está pronto para se conectar". Ativar as credenciais de produção da aplicação ou abrir chamado no suporte (client_id 8660421557844589) |
| Primeiro pagamento de teste real | item acima | Conta de teste de vendedor e de comprador; inscrição de R$ 10,00 |
| Testes no iPhone real (Safari, modo instalado na tela inicial) | aparelho | Validado em simulação de tela de celular; falta o aparelho |
| Cadastro com Google de ponta a ponta | conta Google de teste | O pop-up de boas-vindas não foi visto com o Google real |
| Contas não confirmadas que entram com Google | decisão do responsável | Proposta: o Google assume a conta, apaga a senha não confirmada e encerra as sessões |
| Produção do Mercado Pago | itens acima | Trocar para credenciais de produção e `MERCADOPAGO_ENVIRONMENT=production` só depois do teste |

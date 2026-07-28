# Expansão funcional do OperadorZero — 2026-07-28

Este documento conecta o pedido de expansão das abas ao código real e deve ser lido com [[00-LEIA-ANTES-CODEX]], [[04-MODULOS-E-FLUXOS]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]] e [[09-OPERACOES]].

## Inventário confirmado antes da alteração

- Produção pública: Início com links para Operações, Como funciona e Comunidade; login e cadastro reais.
- Área autenticada no código: Visão geral, Operações, Minha equipe, Rankings, Classificados, Comunidade, Destaques, Conquistas e Meu Operador.
- Persistência existente: identidade, perfil do operador, privacidade, equipamentos, equipes, integrantes, histórico e convites.
- Áreas sem domínio persistente antes desta etapa: campos, mapas, operações, inscrições, desempenho, estatísticas, rankings calculados, campeonatos, notificações, avaliações, denúncias e administração.

## Princípios da expansão

- Manter nome, identidade visual, autenticação e componentes existentes.
- Ampliar as áreas atuais sem criar uma segunda plataforma ou rotas duplicadas.
- Nunca preencher telas com pessoas, eventos, pontuações ou números fictícios.
- Construir cada superfície sobre migration, API, autorização por objeto, auditoria e estado vazio.
- Valores de inscrição e formas de pagamento são informativos. O OperadorZero não processa, recebe ou intermedeia pagamentos.
- Imagens, plantas e fotos permanecem bloqueadas até existir storage privado, validação de conteúdo, quarentena e antivírus conforme [[05-SEGURANCA-E-PRIVACIDADE]].

## Etapa implementada: base de locais e operações

- `airsoft_field`: dados do campo, localização, estrutura, capacidade, valores informativos, responsável e versionamento.
- `field_map`: mapas vinculados ao campo, terreno, capacidade, áreas, rotas e regras específicas.
- `airsoft_operation`: agenda, local, horários, modalidade, regras, limites, valor informativo, aprovação, lista de espera e oito status.
- `operation_participant`: solicitação, aprovação, espera, confirmação, check-in e cancelamento preservando histórico.
- APIs autenticadas para pesquisar e cadastrar campos/mapas, pesquisar/criar operações e solicitar/cancelar participação.
- Autorização: somente o responsável cadastra mapa no próprio campo; somente o organizador altera o status da própria operação.
- O mapa selecionado é validado no backend como pertencente ao campo da operação.

## Etapa implementada: desempenho, revisão e contestação

- O operador registra desempenho somente em operação finalizada na qual tenha presença confirmada ou check-in.
- O registro aceita eliminações, mortes, objetivos, vitórias por rodada, resultado, função, equipe representada, observações, destaque, penalidades, abandono e participação completa/parcial.
- O organizador pode confirmar, corrigir ou rejeitar registros da própria operação.
- Capitão e gestor podem confirmar registros vinculados à própria equipe.
- Outro participante confirmado da mesma operação pode contestar, com motivo obrigatório.
- Registros contestados saem do ranking até correção ou rejeição pelo organizador.
- Atualizações usam versão otimista e todas as decisões relevantes geram auditoria.

## Sequência restante, sem atalhos

1. Completar operações: edição, administração de participantes, pagamentos informativos, times, resultados, ocorrências, penalidades e fotos após storage seguro.
2. Ampliar perfil e diretórios públicos de operadores/equipes, solicitações de entrada e administração completa de integrantes.
3. Calcular estatísticas agregadas e conquistas exclusivamente com registros válidos.
5. Implementar campeonatos, histórico e medalhas sobre operações/resultados persistidos.
6. Implementar notificações, avaliações de evento/campo e denúncias com moderação.
7. Criar configurações seccionadas e painel administrativo com RBAC explícito.
8. Enriquecer Início e Meu Painel por endpoints agregadores reais; números inexistentes devem continuar ausentes ou zerados, nunca simulados.

## Decisões confirmadas em 2026-07-28

- A fórmula nova descrita no pedido substitui o OZ-RANK 1.0 como regra oficial. A implementação recebe versão própria e testes de regressão.
- Qualquer usuário autenticado pode criar equipe, cadastrar campo e organizar operação.
- Criar é uma permissão geral; administrar, editar participantes ou alterar status continua restrito ao capitão/gestor, responsável pelo campo ou organizador do objeto.
- O pedido permite informar taxa de inscrição. Isso não revoga a regra de plataforma gratuita: nesta etapa o dado é apenas informativo e nenhum fluxo financeiro foi criado.

## Verificação necessária antes de produção

- Backup e preflight do PostgreSQL antes de aplicar `V8`.
- Testes de integração da migration em PostgreSQL real.
- Homologação autenticada de campo → mapa → operação → publicação → inscrição → cancelamento.
- Revisão de papéis para responsáveis por campo e organizadores antes de permitir criação para todos os usuários em produção.

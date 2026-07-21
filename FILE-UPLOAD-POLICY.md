# File Upload Policy

Somente imagens necessárias; limite inicial 8 MB e dimensões máximas definidas. Validar extensão, MIME declarado e assinatura real; rejeitar SVG e conteúdo ativo no MVP; decodificar e reencodar; remover metadados; gerar nome aleatório; armazenar em bucket privado/quarentena fora do domínio de sessão; servir miniaturas por URL assinada ou host estático sem cookies; antivírus quando disponível. Limites por usuário, logs de decisão e exclusão de órfãos. Nunca confiar no nome original nem executar o arquivo.


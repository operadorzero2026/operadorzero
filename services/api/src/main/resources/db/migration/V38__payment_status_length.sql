-- PARTIALLY_REFUNDED tem 18 caracteres: amplia as colunas de status dos pagamentos (so aumenta, nao altera dados).
ALTER TABLE operation_payment ALTER COLUMN status TYPE VARCHAR(24);
ALTER TABLE operation_payment_event ALTER COLUMN status TYPE VARCHAR(24);

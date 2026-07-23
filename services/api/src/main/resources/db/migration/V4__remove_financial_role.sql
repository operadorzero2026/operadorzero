DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name IN (
              'operation_payment', 'operation_payment_split', 'operation_commission',
              'operation_refund', 'organizer_financial_account', 'organizer_recipient',
              'organizer_payout', 'advertising_payment', 'advertising_plan',
              'advertising_campaign_payment', 'payment_gateway_fee', 'payment_webhook_event',
              'payment_chargeback', 'financial_reconciliation', 'platform_revenue',
              'mercado_pago_organizer_account', 'mercado_pago_oauth_state',
              'mercado_pago_webhook_event'
          )
    ) THEN
        RAISE EXCEPTION 'V4 abortada: tabelas financeiras nao versionadas exigem inventario e exportacao antes da remocao.';
    END IF;
END $$;

INSERT INTO audit_event (
    actor_user_id,
    action,
    entity_type,
    entity_public_id,
    reason,
    correlation_id,
    before_data,
    after_data
)
SELECT
    NULL,
    'ROLE_ASSIGNMENT_REMOVED_BY_MIGRATION',
    'APP_USER',
    u.public_id,
    'Papel financeiro descontinuado: a plataforma passou a operar sem monetizacao.',
    'migration-v4-remove-financial-role',
    jsonb_build_object('role', 'FINANCE_MANAGER', 'assigned', true),
    jsonb_build_object('role', 'FINANCE_MANAGER', 'assigned', false)
FROM user_role ur
JOIN app_user u ON u.id = ur.user_id
JOIN role r ON r.id = ur.role_id
WHERE r.code = 'FINANCE_MANAGER';

DELETE FROM user_role
WHERE role_id IN (SELECT id FROM role WHERE code = 'FINANCE_MANAGER');

DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE code = 'FINANCE_MANAGER');

DELETE FROM role WHERE code = 'FINANCE_MANAGER';

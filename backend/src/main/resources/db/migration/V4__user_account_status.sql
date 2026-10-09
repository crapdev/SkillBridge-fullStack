-- Estado de la cuenta: los proveedores se registran pendientes hasta que un administrador los apruebe.
-- Las cuentas existentes quedan activas.
ALTER TABLE app_users ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_app_users_role_status ON app_users(role, status);

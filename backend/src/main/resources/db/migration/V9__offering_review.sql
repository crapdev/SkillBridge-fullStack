-- HU-21 · Revisión de mentorías por el administrador.
-- Las mentorías que ya existen quedan aprobadas: si no, el catálogo quedaría vacío al desplegar.
-- Las nuevas las crea la aplicación en PENDING_REVIEW y no son visibles hasta que un administrador las apruebe.
ALTER TABLE offerings ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE offerings ADD COLUMN rejection_reason VARCHAR(300);

ALTER TABLE offerings ADD CONSTRAINT chk_offerings_review_status
    CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED'));

-- El panel de administración lista las pendientes: el índice parcial solo guarda esas filas
CREATE INDEX idx_offerings_pending_review ON offerings(created_at) WHERE review_status = 'PENDING_REVIEW';

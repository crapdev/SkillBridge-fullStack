-- HU-20 · Ficha completa de la mentoría: temario, duración, nivel y días en que se ofrece.
-- Las mentorías existentes quedan con valores por defecto válidos:
--   * 60 minutos por sesión y nivel básico (los 3 servicios de ejemplo reciben su nivel real),
--   * todos los días de la semana (así nadie pierde días en los que ya podía publicar horarios),
--   * un temario de al menos 2 temas (los de ejemplo reciben el temario que antes estaba fijo en el frontend).

-- 1. Duración y nivel -------------------------------------------------------------------------------
ALTER TABLE offerings ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE offerings ADD COLUMN level VARCHAR(20) NOT NULL DEFAULT 'BASICO';

ALTER TABLE offerings ADD CONSTRAINT chk_offerings_duration CHECK (duration_minutes BETWEEN 30 AND 240);
ALTER TABLE offerings ADD CONSTRAINT chk_offerings_level CHECK (level IN ('BASICO', 'INTERMEDIO', 'AVANZADO'));

-- 2. Temario: lista ordenada de temas por mentoría ----------------------------------------------------
CREATE TABLE offering_topics (
    offering_id UUID         NOT NULL REFERENCES offerings(id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(300) NOT NULL,
    PRIMARY KEY (offering_id, position)
);

-- 3. Días de la semana en que se ofrece -------------------------------------------------------------
CREATE TABLE offering_working_days (
    offering_id UUID       NOT NULL REFERENCES offerings(id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    PRIMARY KEY (offering_id, day_of_week),
    CONSTRAINT chk_offering_working_days CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

-- 4. Datos por defecto para las mentorías existentes --------------------------------------------------
INSERT INTO offering_working_days (offering_id, day_of_week)
SELECT o.id, d.day_of_week
FROM offerings o
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY'))
    AS d(day_of_week);

-- Servicios de ejemplo (V1): nivel y temario que antes mostraba service-content.ts
UPDATE offerings SET level = 'INTERMEDIO' WHERE id = '11111111-1111-1111-1111-111111111111';
UPDATE offerings SET level = 'INTERMEDIO' WHERE id = '22222222-2222-2222-2222-222222222222';
UPDATE offerings SET level = 'AVANZADO'   WHERE id = '33333333-3333-3333-3333-333333333333';

INSERT INTO offering_topics (offering_id, position, title, description)
SELECT o.id, t.position, t.title, t.description
FROM offerings o
JOIN (VALUES
    ('11111111-1111-1111-1111-111111111111'::uuid, 0, 'Arquitectura hexagonal', 'Puertos, adaptadores y un dominio independiente.'),
    ('11111111-1111-1111-1111-111111111111'::uuid, 1, 'APIs REST', 'Diseño de endpoints y contratos con Spring Boot.'),
    ('11111111-1111-1111-1111-111111111111'::uuid, 2, 'Seguridad', 'Spring Security, JWT y control de acceso por roles.'),
    ('11111111-1111-1111-1111-111111111111'::uuid, 3, 'Persistencia', 'JPA, consultas eficientes y migraciones con Flyway.'),
    ('22222222-2222-2222-2222-222222222222'::uuid, 0, 'Componentes modernos', 'Standalone components, signals y control flow.'),
    ('22222222-2222-2222-2222-222222222222'::uuid, 1, 'RxJS sin fugas', 'Operadores clave y suscripciones bien gestionadas.'),
    ('22222222-2222-2222-2222-222222222222'::uuid, 2, 'Rutas y seguridad', 'Guards, interceptores y manejo de sesión.'),
    ('22222222-2222-2222-2222-222222222222'::uuid, 3, 'Formularios', 'Validaciones claras y buena experiencia de usuario.'),
    ('33333333-3333-3333-3333-333333333333'::uuid, 0, 'Contenedores', 'Imágenes con Docker y entornos reproducibles con Compose.'),
    ('33333333-3333-3333-3333-333333333333'::uuid, 1, 'Arquitectura distribuida', 'Separación de servicios, límites y comunicación entre ellos.'),
    ('33333333-3333-3333-3333-333333333333'::uuid, 2, 'Mensajería y eventos', 'Flujos asíncronos con RabbitMQ y manejo de reintentos.'),
    ('33333333-3333-3333-3333-333333333333'::uuid, 3, 'Observabilidad', 'Health checks, métricas y logs que sirven en producción.')
) AS t(offering_id, position, title, description) ON t.offering_id = o.id;

-- Resto de mentorías (creadas por proveedores): temario genérico de 2 temas que el proveedor puede editar
INSERT INTO offering_topics (offering_id, position, title, description)
SELECT o.id, t.position, t.title, t.description
FROM offerings o
CROSS JOIN (VALUES
    (0, 'Diagnóstico y objetivos', 'Revisamos tu punto de partida y definimos qué quieres lograr en la sesión.'),
    (1, 'Práctica guiada', 'Trabajamos sobre un caso real del tema con acompañamiento del mentor.')
) AS t(position, title, description)
WHERE NOT EXISTS (SELECT 1 FROM offering_topics x WHERE x.offering_id = o.id);

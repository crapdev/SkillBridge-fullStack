-- Administrador inicial de la plataforma.
-- Si el correo ya existe (por ejemplo, registrado antes como CUSTOMER) solo se promueve a ADMIN
-- y se conservan su nombre y contraseña.
INSERT INTO app_users (id, name, email, password, role)
VALUES (gen_random_uuid(), 'Cristian Albor', 'calborparra@gmail.com', '$2a$10$p4B7dylIyDv0ttmTqNbtmOcTjpZlnbRbgVgJbnw7BSHV3mFLsCvr.', 'ADMIN')
ON CONFLICT (email) DO UPDATE SET role = 'ADMIN';

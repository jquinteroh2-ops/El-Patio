-- La barra tiene su propia persona: prepara las bebidas y solo ve esa pantalla.
-- Hasta ahora la cuenta de cocina abria las dos.
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;

ALTER TABLE usuarios
  ADD CONSTRAINT usuarios_rol_check
  CHECK (rol IN ('mesero', 'cocina', 'bar', 'recepcion', 'repartidor', 'cajero', 'administrador'));

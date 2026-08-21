-- El repartidor deja de ser un nombre escrito a mano y pasa a ser una persona.
--
-- Hasta ahora, despachar un domicilio guardaba un texto: «Wilfrido». Eso basta
-- para que el cliente sepa quién le toca la puerta y para que recepción sepa a
-- quién preguntarle, pero no alcanza para que esa persona entre al sistema y
-- vea lo suyo: dos «Wilfrido» distintos son el mismo texto, y un nombre escrito
-- con una letra de más no se parece a ninguna fila de `usuarios`.
--
-- Dos cambios que van juntos:
--
--  1. El rol. La restricción de `usuarios` se escribió en V1 con los roles de
--     entonces y se corrigió en V6 al nacer recepción. Ahora nace repartidor y
--     hay que volver a pasar por aquí, o crearlo desde /admin/configuracion
--     falla contra la base con un error que no explica nada.
--
--  2. La columna. `repartidor` sigue siendo el nombre que sale en el papel y en
--     el WhatsApp del cliente, porque a veces lo lleva alguien que no tiene
--     usuario —el hijo del dueño, un motorizado de turno— y ese caso tiene que
--     seguir funcionando. `repartidor_id` es opcional y solo se llena cuando
--     quien lo lleva sí es una cuenta: es lo que permite que su pantalla filtre
--     por él sin adivinar por el nombre.
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;

ALTER TABLE usuarios
  ADD CONSTRAINT usuarios_rol_check
  CHECK (rol IN ('mesero', 'cocina', 'recepcion', 'repartidor', 'cajero', 'administrador'));

ALTER TABLE ordenes
  ADD COLUMN repartidor_id TEXT REFERENCES usuarios (id);

-- La pantalla del repartidor pregunta siempre lo mismo: qué llevo yo ahora.
-- Sin este índice esa consulta recorre todas las comandas de la historia.
CREATE INDEX ordenes_repartidor_idx
  ON ordenes (repartidor_id)
  WHERE repartidor_id IS NOT NULL;

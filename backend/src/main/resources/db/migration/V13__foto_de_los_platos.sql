-- Foto de cada plato, para el menú visual público.
--
-- Mismo almacén de imágenes que ya usan las publicaciones: nulo se permite,
-- porque obligar a subir foto antes de poder guardar un plato nuevo haría que
-- el administrador suba cualquier cosa con tal de terminar el formulario.
ALTER TABLE items_carta
  ADD COLUMN imagen TEXT;

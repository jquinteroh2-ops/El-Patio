-- El precio de lista, guardado junto al que de verdad se cobró.
--
-- Sin esto no se puede decir cuánto se rebajó en el día. La línea de comanda
-- guarda `precio_unitario`, que con una promoción activa es el precio menor, y
-- el de lista solo vive en la carta —donde puede cambiar mañana—. Restar contra
-- la carta de hoy daría un descuento distinto cada vez que se consulta el mismo
-- cierre.
--
-- Es la misma razón por la que la comanda ya copiaba el nombre y el precio en
-- vez de leerlos de la carta: lo que se vendió anoche no puede cambiar de valor
-- porque hoy alguien editó un plato.
ALTER TABLE items_orden
  ADD COLUMN precio_lista BIGINT;

-- Nulo significa «se vendió al precio de lista», que es el caso de todo lo
-- cobrado hasta hoy y seguirá siendo el caso normal. Se deja nulo en vez de
-- copiar `precio_unitario` en las filas viejas para no inventar un dato que
-- nadie registró: una promoción anterior a esta columna no se puede reconstruir.
COMMENT ON COLUMN items_orden.precio_lista IS
  'Precio de carta cuando se tomó la comanda. Nulo: se vendió sin rebaja.';

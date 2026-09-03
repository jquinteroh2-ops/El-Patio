-- Más fotos por plato, para la ficha que se abre al tocarlo en la carta.
--
-- `imagen` se queda como la PORTADA: es la que sale en el listado, la que
-- identifica el plato de un vistazo, y tenerla aparte evita que algo tenga que
-- decidir cuál de un arreglo manda. Aquí van solo las ADICIONALES, en el orden
-- en que el administrador las subió, que es el orden en que quiere mostrarlas.
--
-- JSONB y no una tabla aparte por lo mismo que los modificadores: solo se leen
-- completas junto con su plato y nunca se filtra ni se ordena por ellas, así
-- que normalizarlas costaría mantenimiento sin que ninguna consulta lo
-- aproveche.
ALTER TABLE items_carta
  ADD COLUMN galeria JSONB NOT NULL DEFAULT '[]'::jsonb;

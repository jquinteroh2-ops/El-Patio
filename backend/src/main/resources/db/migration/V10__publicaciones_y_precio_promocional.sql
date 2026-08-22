-- Publicaciones del restaurante y precio promocional de la carta.
--
-- Dos cosas que el dueño pidió y que en el fondo son la misma: poder contarle
-- algo al cliente sin llamar a nadie. Una promoción que además baja el precio,
-- un evento con su foto, o simplemente cómo se ve el local por dentro.

-- ---------------------------------------------------------------------------
-- Publicaciones
-- ---------------------------------------------------------------------------

CREATE TABLE publicaciones (
  id         TEXT PRIMARY KEY,
  -- Qué es lo que se publica. Los tres salen en sitios distintos del sitio
  -- público, y por eso el tipo no es decorativo: decide dónde aparece.
  tipo       TEXT NOT NULL CHECK (tipo IN ('promocion', 'evento', 'galeria')),
  titulo     TEXT NOT NULL,
  cuerpo     TEXT NOT NULL DEFAULT '',
  -- Nombre del archivo en el almacén de imágenes. Nulo se permite: una
  -- promoción puede ser solo texto, y obligar a subir una foto haría que el
  -- dueño suba cualquier cosa con tal de poder publicar.
  imagen     TEXT,
  -- Vigencia. Nulo en los dos extremos significa «mientras esté publicada»:
  -- las fotos del local no vencen, una promoción de fin de semana sí.
  desde      DATE,
  hasta      DATE,
  -- Lo que decide si el cliente la ve. Se guarda como borrador y se publica
  -- aparte, para que nadie tenga que escribir con prisa por estar en vivo.
  publicada  BOOLEAN NOT NULL DEFAULT FALSE,
  -- El dueño decide el orden en que salen; no lo decide la fecha.
  "orden"    INTEGER NOT NULL DEFAULT 0,
  creada_en  TIMESTAMPTZ NOT NULL,
  -- Una vigencia al revés no es un error del cliente: es una promoción que no
  -- se puede mostrar nunca. Se ataja aquí y no en la pantalla, porque la
  -- pantalla no es la única puerta a la base.
  CONSTRAINT publicaciones_vigencia_coherente
    CHECK (desde IS NULL OR hasta IS NULL OR desde <= hasta)
);

-- Lo que el sitio público pregunta siempre: qué hay publicado, de qué tipo y
-- en qué orden.
CREATE INDEX publicaciones_visibles ON publicaciones (tipo, publicada, "orden");

-- ---------------------------------------------------------------------------
-- Precio promocional
-- ---------------------------------------------------------------------------

-- El descuento se modela como un PRECIO, no como una rebaja sobre la cuenta.
-- Esa decisión es la que mantiene simple todo lo demás: la venta ocurre al
-- precio promocional, y el INC, la propina y el documento electrónico se
-- calculan sobre él sin ninguna regla especial. Una línea de descuento en la
-- cuenta, en cambio, obliga a repartir la rebaja entre las líneas gravadas para
-- que la base gravable que se le declara a la DIAN siga cuadrando.
ALTER TABLE items_carta
  ADD COLUMN precio_promocional BIGINT,
  ADD COLUMN promocion_desde    DATE,
  ADD COLUMN promocion_hasta    DATE;

-- Un precio promocional que no es menor que el de lista no es una promoción.
-- Que la base lo rechace evita el error de teclado que le sube el precio a un
-- plato creyendo que se lo baja.
ALTER TABLE items_carta
  ADD CONSTRAINT items_carta_promocion_mas_barata
    CHECK (precio_promocional IS NULL
           OR (precio_promocional >= 0 AND precio_promocional < precio));

ALTER TABLE items_carta
  ADD CONSTRAINT items_carta_promocion_coherente
    CHECK (promocion_desde IS NULL OR promocion_hasta IS NULL
           OR promocion_desde <= promocion_hasta);

-- Ubicación exacta de la entrega.
--
-- En Turbaco las direcciones escritas fallan seguido: «casa esquinera»,
-- «portón verde», calles sin nomenclatura clara. El domiciliario termina
-- llamando al cliente para que lo guíe, y eso cuesta minutos en cada entrega.
--
-- Con el permiso del cliente, el navegador entrega la posición del celular y el
-- domiciliario abre el punto exacto en Waze o Google Maps.
--
-- Tres decisiones que van juntas:
--
--  1. Es OPCIONAL. Si el cliente niega el permiso o el navegador no la puede
--     dar, el pedido entra igual. La dirección escrita sigue siendo la que
--     manda; la coordenada es una ayuda, no un requisito.
--
--  2. Se guarda la PRECISIÓN que informó el navegador. Un GPS da quince metros;
--     una posición deducida de la IP da dos kilómetros y es peor que inútil,
--     porque parece precisa y no lo es. Recepción tiene que poder distinguirlas.
--
--  3. Es dato personal preciso. Solo se captura con una acción explícita del
--     cliente, solo se guarda en domicilios y solo la ve quien despacha, que ya
--     son los únicos roles con acceso a /api/pedidos.

ALTER TABLE ordenes
  ADD COLUMN cliente_latitud   DOUBLE PRECISION,
  ADD COLUMN cliente_longitud  DOUBLE PRECISION,
  -- Radio en metros dentro del cual el navegador afirma que está el punto.
  ADD COLUMN ubicacion_precision_metros INTEGER;

-- O están las dos coordenadas o no está ninguna: media coordenada no ubica nada.
ALTER TABLE ordenes ADD CONSTRAINT ordenes_ubicacion_completa
  CHECK (
    (cliente_latitud IS NULL AND cliente_longitud IS NULL)
    OR (cliente_latitud IS NOT NULL AND cliente_longitud IS NOT NULL)
  );

-- Coordenadas fuera del mundo son un error de quien las envía, no un dato.
ALTER TABLE ordenes ADD CONSTRAINT ordenes_ubicacion_en_rango
  CHECK (
    cliente_latitud IS NULL
    OR (cliente_latitud BETWEEN -90 AND 90 AND cliente_longitud BETWEEN -180 AND 180)
  );

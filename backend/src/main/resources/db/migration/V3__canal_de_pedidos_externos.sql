-- Domicilios y para llevar.
--
-- Hasta aquí el sistema solo conocía mesas. Un pedido que entra desde fuera del
-- salón no tiene mesa ni mesero asignado en el momento en que llega, así que
-- las dos columnas dejan de ser obligatorias.
--
-- La decisión de fondo: un pedido de domicilio NO es una entidad nueva, es una
-- `orden` con otro canal. Así cocina lo ve exactamente igual que el de una
-- mesa, el cierre de caja lo suma sin lógica aparte y los reportes lo cruzan
-- con el resto de la venta. Una tabla `pedidos` separada habría obligado a
-- duplicar los ítems, los cargos, el cobro y el cálculo del INC.

-- ---------------------------------------------------------------------------
-- La orden deja de exigir mesa y mesero
-- ---------------------------------------------------------------------------

ALTER TABLE ordenes ALTER COLUMN mesa_id DROP NOT NULL;
ALTER TABLE ordenes ALTER COLUMN mesero_id DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- Zonas de domicilio
-- ---------------------------------------------------------------------------

-- Se administran desde /admin/configuracion, así que viven en la base y no en
-- config.ts: el dueño sube la tarifa de una zona cuando sube la gasolina, y no
-- puede necesitar un despliegue para eso.
CREATE TABLE zonas_domicilio (
  id                 TEXT PRIMARY KEY,
  nombre             TEXT NOT NULL,
  -- Lo que se le cobra al cliente por llevarle el pedido. No causa INC.
  tarifa             BIGINT NOT NULL CHECK (tarifa >= 0),
  -- Por debajo de esto no sale un domicilio a esa zona: el envío se comería
  -- el margen del pedido.
  monto_minimo       BIGINT NOT NULL DEFAULT 0 CHECK (monto_minimo >= 0),
  minutos_estimados  INTEGER NOT NULL DEFAULT 40 CHECK (minutos_estimados > 0),
  activa             BOOLEAN NOT NULL DEFAULT TRUE,
  orden              INTEGER NOT NULL DEFAULT 0
);

-- Barrios reales de Turbaco, ordenados de más cerca a más lejos del local.
INSERT INTO zonas_domicilio (id, nombre, tarifa, monto_minimo, minutos_estimados, activa, orden) VALUES
  ('zd1', 'Centro', 5000, 30000, 30, TRUE, 1),
  ('zd2', 'El Cerrito', 6000, 30000, 35, TRUE, 2),
  ('zd3', 'Bonanza', 7000, 35000, 40, TRUE, 3),
  ('zd4', 'La Esmeralda', 7000, 35000, 40, TRUE, 4),
  ('zd5', 'Nuevo Horizonte', 8000, 40000, 45, TRUE, 5),
  ('zd6', 'Campo Alegre', 9000, 45000, 50, TRUE, 6),
  ('zd7', 'Villa Rosa', 10000, 50000, 55, TRUE, 7);

-- ---------------------------------------------------------------------------
-- El canal del pedido
-- ---------------------------------------------------------------------------

ALTER TABLE ordenes
  -- 'mesa' para todo lo que ya existía: las órdenes del salón no cambian.
  ADD COLUMN tipo TEXT NOT NULL DEFAULT 'mesa'
    CHECK (tipo IN ('mesa', 'domicilio', 'llevar')),

  ADD COLUMN estado_pedido TEXT
    CHECK (estado_pedido IN ('nuevo', 'aceptado', 'en_preparacion', 'listo',
                             'despachado', 'entregado', 'rechazado', 'cancelado')),

  ADD COLUMN cliente_nombre     TEXT,
  ADD COLUMN cliente_telefono   TEXT,
  ADD COLUMN cliente_direccion  TEXT,
  ADD COLUMN cliente_barrio     TEXT,

  ADD COLUMN zona_domicilio_id  TEXT REFERENCES zonas_domicilio (id) ON DELETE SET NULL,
  -- El envío es una línea aparte, después del impuesto: no causa INC ni propina.
  ADD COLUMN costo_envio        BIGINT NOT NULL DEFAULT 0 CHECK (costo_envio >= 0),

  -- Lo que el cliente dijo que pensaba pagar. No es el cobro: ese sigue siendo
  -- el registro de `pagos`, que se escribe cuando el dinero de verdad entra.
  ADD COLUMN metodo_pago_previsto TEXT
    CHECK (metodo_pago_previsto IN ('efectivo', 'tarjeta', 'transferencia', 'mixto')),

  ADD COLUMN minutos_estimados  INTEGER,
  ADD COLUMN repartidor         TEXT,
  ADD COLUMN motivo_rechazo     TEXT,
  -- Cuándo entró el pedido. El cronómetro de recepción cuenta desde aquí, no
  -- desde `abierta_en`: para el cliente el reloj arrancó cuando pidió.
  ADD COLUMN recibido_en        TIMESTAMPTZ;

-- Una orden de mesa exige mesa; una de domicilio o para llevar, no.
ALTER TABLE ordenes ADD CONSTRAINT ordenes_mesa_segun_canal
  CHECK ((tipo = 'mesa' AND mesa_id IS NOT NULL) OR (tipo <> 'mesa'));

-- Un pedido externo siempre tiene estado de recepción; uno de mesa, nunca.
ALTER TABLE ordenes ADD CONSTRAINT ordenes_estado_pedido_segun_canal
  CHECK ((tipo = 'mesa' AND estado_pedido IS NULL) OR (tipo <> 'mesa' AND estado_pedido IS NOT NULL));

-- Un domicilio necesita a dónde llevarlo.
ALTER TABLE ordenes ADD CONSTRAINT ordenes_direccion_en_domicilio
  CHECK (tipo <> 'domicilio' OR (cliente_direccion IS NOT NULL AND cliente_direccion <> ''));

-- La pantalla de recepción filtra por canal y estado en cada refresco.
CREATE INDEX ordenes_canal ON ordenes (tipo, estado_pedido);

-- ---------------------------------------------------------------------------
-- Ajustes del canal
-- ---------------------------------------------------------------------------

ALTER TABLE ajustes
  -- Interruptor para cerrar el canal cuando la cocina está saturada. Es lo
  -- primero que busca un administrador un viernes a las nueve de la noche.
  ADD COLUMN domicilios_pausados BOOLEAN NOT NULL DEFAULT FALSE,
  -- Horario en que se reciben pedidos, en la zona del restaurante.
  ADD COLUMN domicilios_desde    TIME NOT NULL DEFAULT '11:30',
  ADD COLUMN domicilios_hasta    TIME NOT NULL DEFAULT '21:30';

-- ---------------------------------------------------------------------------
-- Cobro y cierre por canal
-- ---------------------------------------------------------------------------

-- El envío se guarda con el pago y no se recalcula: si mañana sube la tarifa de
-- la zona, el comprobante de anoche no puede cambiar de valor.
ALTER TABLE pagos ADD COLUMN costo_envio BIGINT NOT NULL DEFAULT 0 CHECK (costo_envio >= 0);

-- El dueño necesita ver cuánto pesa el domicilio contra el salón, y la caja
-- tiene que cuadrar con los tres canales sumados.
ALTER TABLE cierres_caja
  ADD COLUMN total_salon      BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN total_domicilio  BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN total_llevar     BIGINT NOT NULL DEFAULT 0,
  -- Lo cobrado por envíos, dentro de total_domicilio. Se discrimina porque no
  -- es venta de cocina: es un costo que se le traslada al cliente.
  ADD COLUMN total_envios     BIGINT NOT NULL DEFAULT 0;

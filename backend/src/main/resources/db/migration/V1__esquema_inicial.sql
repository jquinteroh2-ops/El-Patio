-- Esquema inicial del sistema de sala.
--
-- Los nombres de columna son los mismos campos de src/compartido/tipos.ts en
-- snake_case, para que la traduccion entre la base y el frontend sea mecanica y
-- nadie tenga que mantener un mapa de nombres en la cabeza.
--
-- El dinero se guarda en BIGINT y no en NUMERIC: el peso colombiano no tiene
-- centavos, y un decimal solo abre la puerta a residuos que descuadran la caja.
--
-- Los identificadores son TEXT con prefijo legible (ord_, io_, pg_) y no UUID,
-- porque aparecen en las URLs de la comandera y en los registros de sala.

-- ---------------------------------------------------------------------------
-- Personal
-- ---------------------------------------------------------------------------

CREATE TABLE usuarios (
  id          TEXT PRIMARY KEY,
  nombre      TEXT NOT NULL,
  rol         TEXT NOT NULL CHECK (rol IN ('mesero', 'cocina', 'cajero', 'administrador')),
  usuario     TEXT NOT NULL,
  clave_hash  TEXT NOT NULL,
  activo      BOOLEAN NOT NULL DEFAULT TRUE
);

-- El nombre de acceso se compara sin distinguir mayusculas, asi que la unicidad
-- tambien tiene que ignorarlas: "Mesero" y "mesero" no pueden ser dos personas.
CREATE UNIQUE INDEX usuarios_usuario_unico ON usuarios (LOWER(usuario));

-- ---------------------------------------------------------------------------
-- Salon
-- ---------------------------------------------------------------------------

CREATE TABLE mesas (
  id               TEXT PRIMARY KEY,
  numero           INTEGER NOT NULL UNIQUE,
  nombre           TEXT,
  zona             TEXT NOT NULL CHECK (zona IN ('salon', 'terraza', 'privado')),
  capacidad        INTEGER NOT NULL CHECK (capacidad > 0),
  estado           TEXT NOT NULL CHECK (estado IN ('libre', 'ocupada', 'cuenta_pedida', 'reservada')),
  mesero_id        TEXT REFERENCES usuarios (id) ON DELETE SET NULL,
  orden_activa_id  TEXT
);

-- ---------------------------------------------------------------------------
-- Carta
-- ---------------------------------------------------------------------------

CREATE TABLE categorias_carta (
  id      TEXT PRIMARY KEY,
  nombre  TEXT NOT NULL,
  orden   INTEGER NOT NULL
);

CREATE TABLE items_carta (
  id                     TEXT PRIMARY KEY,
  categoria_id           TEXT NOT NULL REFERENCES categorias_carta (id) ON DELETE RESTRICT,
  nombre                 TEXT NOT NULL,
  descripcion            TEXT NOT NULL DEFAULT '',
  precio                 BIGINT NOT NULL CHECK (precio >= 0),
  disponible             BOOLEAN NOT NULL DEFAULT TRUE,
  tiempo_preparacion_min INTEGER NOT NULL DEFAULT 0,
  destino                TEXT NOT NULL CHECK (destino IN ('cocina', 'bar')),
  -- Los modificadores son una estructura anidada que solo se lee completa junto
  -- con su producto. Normalizarla en tres tablas mas costaria mantenimiento sin
  -- que ninguna consulta del sistema lo aproveche.
  modificadores          JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX items_carta_categoria ON items_carta (categoria_id);

-- ---------------------------------------------------------------------------
-- Comandas
-- ---------------------------------------------------------------------------

CREATE TABLE ordenes (
  id                 TEXT PRIMARY KEY,
  mesa_id            TEXT NOT NULL REFERENCES mesas (id) ON DELETE RESTRICT,
  mesero_id          TEXT NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  -- Consecutivo del dia operativo. La unicidad la garantiza el contador
  -- bloqueado en `ajustes`; este indice es la red de seguridad que impide un
  -- repetido si alguien escribe en la base por fuera de la aplicacion.
  numero             INTEGER NOT NULL,
  dia_operativo      DATE NOT NULL,
  estado             TEXT NOT NULL CHECK (estado IN
                       ('abierta', 'enviada', 'en_preparacion', 'servida',
                        'cuenta_pedida', 'pagada', 'anulada')),
  comensales         INTEGER NOT NULL DEFAULT 1,
  abierta_en         TIMESTAMPTZ NOT NULL,
  cerrada_en         TIMESTAMPTZ,
  notas              TEXT,
  motivo_anulacion   TEXT,
  orden_reemplazo_id TEXT REFERENCES ordenes (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX ordenes_consecutivo_diario ON ordenes (dia_operativo, numero);
CREATE INDEX ordenes_estado ON ordenes (estado);
CREATE INDEX ordenes_mesa ON ordenes (mesa_id);
CREATE INDEX ordenes_abierta_en ON ordenes (abierta_en);

-- La mesa apunta a su comanda activa; se declara despues de `ordenes` porque
-- las dos tablas se referencian entre si.
ALTER TABLE mesas
  ADD CONSTRAINT mesas_orden_activa_fk
  FOREIGN KEY (orden_activa_id) REFERENCES ordenes (id) ON DELETE SET NULL;

CREATE TABLE items_orden (
  id                          TEXT PRIMARY KEY,
  orden_id                    TEXT NOT NULL REFERENCES ordenes (id) ON DELETE CASCADE,
  item_carta_id               TEXT NOT NULL,
  -- El nombre y el precio se copian y no se leen de la carta: si manana sube el
  -- lomo, la comanda de anoche no puede cambiar de valor.
  nombre                      TEXT NOT NULL,
  precio_unitario             BIGINT NOT NULL CHECK (precio_unitario >= 0),
  cantidad                    INTEGER NOT NULL CHECK (cantidad > 0),
  modificadores_seleccionados JSONB NOT NULL DEFAULT '[]'::jsonb,
  nota_cocina                 TEXT,
  estado                      TEXT NOT NULL CHECK (estado IN
                                ('pendiente', 'en_preparacion', 'listo', 'servido', 'anulado')),
  destino                     TEXT NOT NULL CHECK (destino IN ('cocina', 'bar')),
  enviado_en                  TIMESTAMPTZ,
  listo_en                    TIMESTAMPTZ,
  turno_envio                 INTEGER NOT NULL DEFAULT 0,
  -- Conserva el orden en que el mesero los fue tomando: la comanda impresa debe
  -- salir en la misma secuencia en que se dicto.
  posicion                    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX items_orden_orden ON items_orden (orden_id, posicion);
CREATE INDEX items_orden_destino ON items_orden (destino, estado);

CREATE TABLE cargos_adicionales (
  id           TEXT PRIMARY KEY,
  orden_id     TEXT NOT NULL REFERENCES ordenes (id) ON DELETE CASCADE,
  nombre       TEXT NOT NULL,
  valor        BIGINT NOT NULL,
  -- Nada entra a la cuenta sin responsable.
  agregado_por TEXT NOT NULL,
  agregado_en  TIMESTAMPTZ NOT NULL
);

CREATE INDEX cargos_adicionales_orden ON cargos_adicionales (orden_id);

-- ---------------------------------------------------------------------------
-- Cobro
-- ---------------------------------------------------------------------------

CREATE TABLE pagos (
  id                 TEXT PRIMARY KEY,
  -- Una comanda se cobra una sola vez. Si el cajero se equivoco, la comanda se
  -- anula con motivo y se genera otra: el pago original queda intacto.
  orden_id           TEXT NOT NULL UNIQUE REFERENCES ordenes (id) ON DELETE RESTRICT,
  subtotal           BIGINT NOT NULL,
  inc                BIGINT NOT NULL,
  propina            BIGINT NOT NULL,
  cargos_adicionales BIGINT NOT NULL,
  total              BIGINT NOT NULL,
  metodo             TEXT NOT NULL CHECK (metodo IN ('efectivo', 'tarjeta', 'transferencia', 'mixto')),
  divisiones         JSONB NOT NULL DEFAULT '[]'::jsonb,
  recibido_por       TEXT NOT NULL,
  fecha_hora         TIMESTAMPTZ NOT NULL
);

CREATE INDEX pagos_fecha_hora ON pagos (fecha_hora);

-- ---------------------------------------------------------------------------
-- Reservas
-- ---------------------------------------------------------------------------

CREATE TABLE reservas (
  id                TEXT PRIMARY KEY,
  nombre_cliente    TEXT NOT NULL,
  telefono          TEXT NOT NULL,
  fecha_hora        TIMESTAMPTZ NOT NULL,
  personas          INTEGER NOT NULL CHECK (personas > 0),
  ocasion           TEXT CHECK (ocasion IN ('cumpleanos', 'aniversario', 'negocios', 'ninguna')),
  estado            TEXT NOT NULL CHECK (estado IN
                      ('solicitada', 'confirmada', 'cancelada', 'cumplida', 'no_asistio')),
  notas             TEXT,
  mesa_asignada_id  TEXT REFERENCES mesas (id) ON DELETE SET NULL
);

CREATE INDEX reservas_fecha_hora ON reservas (fecha_hora);

-- ---------------------------------------------------------------------------
-- Caja
-- ---------------------------------------------------------------------------

CREATE TABLE cierres_caja (
  id                  TEXT PRIMARY KEY,
  fecha               DATE NOT NULL,
  turno               TEXT NOT NULL CHECK (turno IN ('almuerzo', 'cena')),
  venta_total         BIGINT NOT NULL,
  total_efectivo      BIGINT NOT NULL,
  total_tarjeta       BIGINT NOT NULL,
  total_transferencia BIGINT NOT NULL,
  propinas_totales    BIGINT NOT NULL,
  inc_total           BIGINT NOT NULL,
  ordenes_atendidas   INTEGER NOT NULL,
  ticket_promedio     BIGINT NOT NULL,
  cerrado_por         TEXT NOT NULL,
  fecha_hora          TIMESTAMPTZ NOT NULL
);

CREATE INDEX cierres_caja_fecha ON cierres_caja (fecha, turno);

-- ---------------------------------------------------------------------------
-- Ajustes
-- ---------------------------------------------------------------------------

-- Una sola fila, forzada por el CHECK: los ajustes son del establecimiento, no
-- de un usuario ni de una sesion.
CREATE TABLE ajustes (
  id                    INTEGER PRIMARY KEY CHECK (id = 1),
  porcentaje_inc        INTEGER NOT NULL DEFAULT 8,
  simular_sin_conexion  BOOLEAN NOT NULL DEFAULT FALSE,
  consecutivo_orden     INTEGER NOT NULL DEFAULT 0,
  fecha_consecutivo     DATE NOT NULL
);

INSERT INTO ajustes (id, porcentaje_inc, simular_sin_conexion, consecutivo_orden, fecha_consecutivo)
VALUES (1, 8, FALSE, 0, CURRENT_DATE);

-- ---------------------------------------------------------------------------
-- Sesiones
-- ---------------------------------------------------------------------------

-- Solo se guarda el hash del refresh token: si alguien se lleva una copia de la
-- base, no se lleva sesiones utilizables.
CREATE TABLE sesiones_refresh (
  id          TEXT PRIMARY KEY,
  usuario_id  TEXT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL UNIQUE,
  emitido_en  TIMESTAMPTZ NOT NULL,
  expira_en   TIMESTAMPTZ NOT NULL,
  revocado    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX sesiones_refresh_usuario ON sesiones_refresh (usuario_id);
CREATE INDEX sesiones_refresh_expira ON sesiones_refresh (expira_en);

-- Peticiones, quejas, reclamos, sugerencias y felicitaciones.
--
-- Contexto propio. No comparte nada con reclutamiento ni con ventas: quien se
-- queja de un plato no es un aspirante ni un empleado.
--
-- Aquí también hay datos personales y aplica la Ley 1581 de 2012, así que la
-- autorización vuelve a ser tres columnas y no un booleano, por la misma razón:
-- una autorización que no se puede demostrar es como no tenerla.

-- ---------------------------------------------------------------------------
-- El consecutivo del radicado
-- ---------------------------------------------------------------------------
--
-- Una fila por año, y se entrega bajo bloqueo de esa fila dentro de la misma
-- transacción que inserta la solicitud. Es el mismo mecanismo que ya usa el
-- consecutivo de comandas, y por el mismo motivo:
--
-- Una secuencia de PostgreSQL sería más simple pero DEJA HUECOS cuando una
-- transacción se revierte, porque las secuencias no participan del rollback. Un
-- radicado con huecos destruye lo único que el radicado tiene que garantizar:
-- que el restaurante pueda demostrar cuántas solicitudes recibió y que ninguna
-- desapareció por el camino.
create table pqr_consecutivos (
    ano     integer not null primary key,
    ultimo  integer not null
);

comment on table pqr_consecutivos is
  'Contador del radicado, uno por año. Se entrega bajo bloqueo de fila: sin saltos ni repetidos.';

-- ---------------------------------------------------------------------------

create table solicitudes_pqr (
    id                      text        not null primary key,

    -- PQR-AAAA-NNNNN. Único: es la referencia con que un cliente sin cuenta
    -- vuelve a consultar su solicitud, y dos iguales harían que dos personas
    -- distintas vieran la misma.
    radicado                text        not null unique,
    tipo                    varchar(15) not null,

    nombre_completo         text        not null,
    email                   text        not null,
    telefono                varchar(40),
    fecha_visita            date,

    asunto                  varchar(120)  not null,
    descripcion             varchar(2000) not null,

    -- El adjunto NO va aquí; en la base queda solo la referencia.
    adjunto_ref             text,
    adjunto_nombre_original text,

    estado                  varchar(15) not null,
    fecha_radicacion        timestamptz not null,

    -- Se congela al radicar y no se recalcula al consultar. Si se recalculara,
    -- cambiar el plazo en la configuración movería hacia atrás el vencimiento
    -- de solicitudes ya radicadas, y el restaurante se encontraría de un día
    -- para otro con quejas vencidas que ayer estaban al día.
    -- Nula en las felicitaciones: no tienen término.
    fecha_limite_respuesta  date,

    fecha_respuesta         timestamptz,
    respuesta               text,
    respondido_por          text,

    autorizacion_datos      boolean     not null,
    autorizacion_fecha      timestamptz not null,
    autorizacion_ip         text,

    notas_internas          text,
    actualizado_en          timestamptz not null
);

-- Por donde entra la consulta pública: radicado + correo. El correo entra en el
-- índice para que la comprobación no obligue a leer la fila entera de alguien
-- que solo está probando radicados al azar.
create index ix_pqr_radicado_email on solicitudes_pqr (radicado, email);

-- Por donde entra la bandeja: lo que está por vencer, primero.
create index ix_pqr_vencimiento on solicitudes_pqr (estado, fecha_limite_respuesta);

-- Por donde entra el reporte por período.
create index ix_pqr_radicacion on solicitudes_pqr (fecha_radicacion desc);

comment on table solicitudes_pqr is
  'PQR de clientes. Datos personales: Ley 1581 de 2012. Término de respuesta: Ley 1480 de 2011.';

-- ---------------------------------------------------------------------------
-- El plazo, configurable desde el panel
-- ---------------------------------------------------------------------------
--
-- No va como constante en el código a propósito: el término aplicable depende
-- del tipo de solicitud y la normativa puede cambiar. Quince días hábiles es el
-- valor de partida, no una verdad: confírmelo con quien asesore al restaurante.
alter table ajustes
    add column dias_habiles_pqr integer not null default 15;

comment on column ajustes.dias_habiles_pqr is
  'Días hábiles para responder una PQR. Por defecto 15. Confirmar con asesoría jurídica.';

-- Las hojas de vida que llegan por el sitio público.
--
-- Contexto propio: reclutamiento. Sin relación con `usuarios` ni con nada de
-- ventas, y a propósito. Un aspirante NO es personal del restaurante, y unir
-- las dos tablas terminaría dándole acceso al sistema a quien solo mandó un PDF.
--
-- AQUÍ HAY DATOS PERSONALES. Nombre, cédula, teléfono, correo y una hoja de
-- vida completa. La Ley 1581 de 2012 obliga a pedir autorización antes de
-- tratarlos y a poder eliminarlos cuando el titular lo pida. Por eso la
-- autorización son tres columnas y no un booleano: sin la fecha y la IP no hay
-- con qué demostrar que se pidió, y una autorización que no se puede demostrar
-- es como no tenerla.

create table postulaciones (
    id                          text        not null primary key,

    nombre_completo             text        not null,
    tipo_documento              varchar(4)  not null,
    numero_documento            text        not null,
    email                       text        not null,
    telefono                    text        not null,
    cargo_interes               varchar(20) not null,
    mensaje                     varchar(500),

    -- El PDF NO va aquí. En la base queda la referencia con que el almacén lo
    -- recupera; el binario vive en el volumen. Meter archivos en la base infla
    -- cada respaldo y no aporta nada: no se consultan ni se indexan.
    hoja_de_vida_ref            text        not null,
    -- El nombre con que lo subieron, solo para mostrarlo al descargarlo. Nunca
    -- se usa para construir una ruta: viene de afuera.
    hoja_de_vida_nombre_original text,

    estado                      varchar(20) not null,
    fecha_postulacion           timestamptz not null,

    autorizacion_datos          boolean     not null,
    autorizacion_fecha          timestamptz not null,
    -- Puede venir nula: detrás de ciertos proxies no hay IP fiable, y preferir
    -- un dato falso a ninguno sería peor evidencia que no tener ninguna.
    autorizacion_ip             text,

    notas_internas              text,
    actualizado_en              timestamptz not null
);

-- Por donde entra la bandeja del administrador: lo más reciente primero.
create index ix_postulaciones_fecha on postulaciones (fecha_postulacion desc);

-- Por donde entra el filtro de estado, que es el uso diario.
create index ix_postulaciones_estado on postulaciones (estado, fecha_postulacion desc);

-- Para el control de envíos repetidos por documento dentro de una ventana.
create index ix_postulaciones_documento on postulaciones (numero_documento, fecha_postulacion desc);

comment on table postulaciones is
  'Aspirantes que dejaron su hoja de vida. Datos personales: Ley 1581 de 2012.';
comment on column postulaciones.hoja_de_vida_ref is
  'Referencia en el almacén de documentos. El PDF vive en el volumen, no aquí.';
comment on column postulaciones.autorizacion_ip is
  'Evidencia de la autorización de tratamiento de datos. Puede ser nula.';

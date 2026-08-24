-- El texto institucional del sitio: quiénes somos, misión, visión, valores.
--
-- Va en la base y no en el código por una razón práctica: el dueño del
-- restaurante tiene que poder corregir su propia misión sin pedirle a nadie un
-- despliegue. Un texto de estos se reescribe tres veces el primer mes.
--
-- La clave es estable y el título no: el sitio busca la sección por `clave`, así
-- que renombrar el título de «Quiénes somos» a «Nuestra historia» cambia lo que
-- se lee sin romper nada.

create table contenido_institucional (
    clave       varchar(40)  not null primary key,
    titulo      text         not null,
    -- Texto plano con saltos de línea, NO HTML. Es contenido que edita alguien
    -- desde un formulario, y aceptar HTML ahí sería aceptar que cualquiera con
    -- acceso al panel pueda inyectar un script en la página pública.
    cuerpo      text         not null,
    orden       integer      not null,
    -- La visión puede quedar vacía hoy: el restaurante todavía no la tiene
    -- escrita. Apagada, la sección no se pinta en vez de salir con un hueco.
    visible     boolean      not null default true,
    actualizado_en timestamptz not null
);

create index ix_contenido_orden on contenido_institucional (orden);

comment on table contenido_institucional is
  'Texto institucional del sitio público, editable desde el panel. Texto plano, nunca HTML.';

-- ---------------------------------------------------------------------------
-- Precarga
-- ---------------------------------------------------------------------------
--
-- EL TEXTO ES UN MARCADOR DE POSICIÓN y lo dice en su propio contenido. No lo
-- escribió el restaurante y no describe a nadie: está para que la sección se
-- pueda ver y probar. Sale con un aviso dentro porque un placeholder que suene
-- bien es un placeholder que nadie reemplaza, y acabaría publicado.

insert into contenido_institucional (clave, titulo, cuerpo, orden, visible, actualizado_en) values
('quienes-somos', 'Quiénes somos',
 'PENDIENTE: reemplace este texto desde el panel de administración.

Aquí va la historia del restaurante: cuándo abrió, quién está detrás, qué se propuso hacer y qué lo distingue de los demás en Turbaco. Dos o tres párrafos bastan; se lee de pie y en un celular.',
 1, true, now()),

('mision', 'Nuestra misión',
 'PENDIENTE: reemplace este texto desde el panel de administración.

La misión responde qué hace el restaurante hoy y para quién. Una o dos frases, en el lenguaje de la casa y no en el de un manual.',
 2, true, now()),

-- La visión llega apagada a propósito: el restaurante todavía no la tiene
-- escrita, y una sección con texto de relleno publicada es peor que ninguna.
-- Se enciende desde el panel el día que exista.
('vision', 'Nuestra visión',
 'PENDIENTE: escriba aquí la visión del restaurante y active esta sección desde el panel.',
 3, false, now()),

('valores', 'Nuestros valores',
 'PENDIENTE: reemplace este texto desde el panel de administración.

Una línea por valor, pocos y concretos. Tres que se cumplan valen más que ocho que suenen bien.',
 4, true, now());

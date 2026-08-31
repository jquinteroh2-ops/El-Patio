-- Pases de cruce ya canjeados.
--
-- El dueño tiene dos restaurantes con este mismo sistema y salta de un panel al
-- otro con un botón. Hasta ahora ese salto lo recibía la pantalla de acceso,
-- porque son dos servidores distintos y la credencial de uno no vale en el
-- otro. Escribir la clave cada vez convertía el botón en un adorno: quien
-- compara dos cajas termina abriendo dos pestañas y dejando las dos con sesión
-- abierta, que es peor que el problema que se quería resolver.
--
-- Ahora el sistema de origen firma un PASE: un token que vive 30 segundos, dice
-- qué usuario es y va firmado con un secreto que solo conocen los dos
-- restaurantes. El destino lo verifica, comprueba que ese usuario exista ALLÁ
-- como administrador activo, y emite su propia sesión.
--
-- ESTA TABLA ES LA QUE HACE QUE UN PASE SIRVA UNA SOLA VEZ. Sin ella, un pase
-- interceptado dentro de su ventana de 30 segundos podría canjearse otra vez.
-- Se guarda el identificador único del token (`jti`), no el token: basta para
-- reconocerlo y no sirve para nada si alguien se lleva una copia de la base.
--
-- Se puede vaciar entera en cualquier momento sin consecuencias: lo único que
-- se pierde es la protección contra el recanje de pases que de todos modos ya
-- vencieron.

create table pases_de_cruce_usados (
    -- El `jti` del token, que el emisor genera al azar.
    jti        varchar(80)  not null primary key,
    -- Quién lo canjeó, para poder mirar el registro si algo se ve raro.
    usuario    text         not null,
    -- De qué restaurante venía. Texto libre: es el nombre que el otro sistema
    -- puso en el pase, y no hay catálogo de restaurantes que respetar.
    origen     text         not null,
    canjeado_en timestamptz not null,
    -- Cuándo deja de hacer falta recordarlo: pasado el vencimiento del propio
    -- token, la firma ya no valida y la fila sobra.
    expira_en  timestamptz  not null
);

-- Se barre por vencimiento en cada canje, igual que las sesiones de refresco.
create index ix_pases_de_cruce_expira on pases_de_cruce_usados (expira_en);

comment on table pases_de_cruce_usados is
  'Pases de cruce entre restaurantes ya canjeados, para que ninguno sirva dos veces.';

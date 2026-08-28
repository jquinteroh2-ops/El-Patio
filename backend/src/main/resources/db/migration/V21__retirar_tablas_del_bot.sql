-- Tumba las dos tablas que sostenían el bot de WhatsApp.
--
-- El bot se retiró del código el 2026-08-27 porque el cliente no quiso la
-- automatización. Las tablas quedaron vivas y vacías, porque una migración ya
-- aplicada no se puede borrar sin romper Flyway: lo que se deshace se deshace
-- hacia adelante, con otra migración. Esta es esa migración.
--
-- Se van solo las dos que existían para conversar. NO se toca nada de lo que
-- V14 y V16 añadieron a `ordenes` y `reservas` -las columnas `canal` y
-- `origen_*`-, porque eso es procedencia de pedidos que ya están guardados: dice
-- por dónde entró cada uno, y hay filas con `whatsapp` escrito. Borrarlo sería
-- perder historia y además romper el mapeo de FilaOrden, que sigue leyéndolas.
--
-- `if exists` porque esta migración también corre sobre bases nuevas, donde V14
-- y V16 acaban de crear las tablas un momento antes, y sobre cualquier copia
-- donde alguien ya las hubiera quitado a mano.

-- El índice ix_conversaciones_canal_identificador cae con la tabla; no hace
-- falta nombrarlo.
drop table if exists conversaciones;

-- La bandeja de mensajes ya atendidos, que evitaba contestar dos veces el mismo
-- WhatsApp cuando Meta reintentaba la entrega. Sin webhook no hay reintentos.
drop table if exists mensajes_whatsapp_procesados;

-- La papelera: lo que el administrador elimino, con quien, cuando y por que.
--
-- Eliminar una venta errada la quita de verdad de pagos y comandas, para que
-- ningun reporte, cierre ni exportacion tenga que acordarse de filtrarla. Lo
-- que queda aqui es la copia exacta de esas filas (en `datos`), y con ella se
-- puede restaurar tal como estaba. Esta tabla no se borra nunca: es la
-- auditoria.

create table registros_eliminados (
    id              text         not null primary key,
    -- Que se elimino. Hoy solo 'venta'.
    tipo            varchar(30)  not null,
    -- El id de lo eliminado (para una venta, el del pago).
    referencia      text         not null,
    -- En una linea, para la lista: comanda, mesa, total, fecha.
    resumen         text         not null,
    motivo          text         not null,
    -- La copia exacta de las filas borradas, para poder restaurarlas.
    datos           jsonb        not null,
    eliminado_por   text         not null,
    eliminado_en    timestamptz  not null,
    restaurado_por  text,
    restaurado_en   timestamptz
);

create index ix_registros_eliminados_fecha on registros_eliminados (eliminado_en desc);

comment on table registros_eliminados is
  'Auditoria de lo eliminado por el administrador, con copia para restaurarlo. No se borra.';

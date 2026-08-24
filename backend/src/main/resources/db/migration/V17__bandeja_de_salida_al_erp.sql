-- La bandeja de salida de ventas hacia el ERP.
--
-- El Patio dejo de emitir documentos fiscales: los emite Globalsoft. Esta tabla
-- es el puente, y existe para que el puente pueda estar caido sin que el
-- restaurante deje de cobrar.
--
-- La fila se escribe en la MISMA transaccion que registra el pago. Esa es toda
-- la garantia: si hay venta, hay envio. Escribirla despues —al confirmar la
-- transaccion, o desde un evento— abre una ventana en la que un corte de luz
-- deja la venta cobrada y sin reportar, y esas son justo las que nadie
-- encuentra hasta el cierre contable.

create table erp_outbox (
    id               text         not null primary key,
    pago_id          text         not null,

    -- Lo que impide facturar dos veces la misma comida.
    --
    -- Nace con la venta y no cambia entre reintentos. El caso que cubre no es
    -- teorico: el ERP recibe, emite el documento y la respuesta se pierde en el
    -- camino; sin esta llave el reintento produce un segundo documento fiscal
    -- por una sola venta, y deshacer eso ante la DIAN es una nota credito y una
    -- conversacion con el contador.
    idempotency_key  varchar(64)  not null unique,

    estado           varchar(30)  not null,
    intentos         integer      not null default 0,

    -- Nulo cuando ya no hay proximo intento: confirmado o agotado.
    proximo_intento  timestamptz,

    -- Que se mando y que contesto, tal cual. Es la bitacora de auditoria: sin
    -- el cuerpo original no hay forma de responder «esto fue lo que se envio»
    -- cuando el ERP y el sistema no coinciden.
    payload          text         not null,
    respuesta_cruda  text,

    -- El numero del documento que emitio el ERP. Es la columna con la que el
    -- contador cruza una venta de El Patio contra su contabilidad.
    documento_externo varchar(80),

    error            text,
    adaptador        varchar(20),

    creado_en        timestamptz  not null,
    actualizado_en   timestamptz  not null,

    constraint fk_erp_outbox_pago foreign key (pago_id) references pagos (id)
);

-- Un pago se reporta una sola vez. La restriccion vive en la base y no solo en
-- el codigo porque es la unica que sigue valiendo si manana algo escribe aqui
-- por otro camino: un respaldo restaurado, un script de correccion, una
-- segunda instancia del backend durante un despliegue.
create unique index ux_erp_outbox_pago on erp_outbox (pago_id);

-- Por donde entra el worker: los pendientes cuya hora ya llego.
create index ix_erp_outbox_pendientes on erp_outbox (estado, proximo_intento);

-- Por donde entra la pantalla de conciliacion: lo del dia, por estado.
create index ix_erp_outbox_creado on erp_outbox (creado_en desc);

comment on table erp_outbox is
  'Ventas en camino al ERP externo. El Patio no emite documentos fiscales.';
comment on column erp_outbox.idempotency_key is
  'Llave estable entre reintentos. Impide que un reintento genere un segundo documento.';
comment on column erp_outbox.documento_externo is
  'Numero del documento emitido por el ERP. Con este se concilia contra la contabilidad.';

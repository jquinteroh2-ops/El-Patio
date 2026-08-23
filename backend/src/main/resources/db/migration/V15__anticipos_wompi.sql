alter table ajustes
    add column porcentaje_anticipo integer not null default 0;

create table pagos_online (
    id varchar(40) not null primary key,
    orden_id varchar(40) not null,
    referencia varchar(100) not null unique,
    monto_centavos bigint not null,
    estado varchar(20) not null,
    url_pago varchar(500),
    transaction_id varchar(100),
    expira_en timestamp not null,
    creada_en timestamp not null,
    actualizada_en timestamp not null
);

create index ix_pagos_online_estado_expira on pagos_online (estado, expira_en);

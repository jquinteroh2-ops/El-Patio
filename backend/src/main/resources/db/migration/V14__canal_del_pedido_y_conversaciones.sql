alter table ordenes
    add column canal varchar(20) not null default 'presencial',
    add column origen_id_conversacion varchar(100),
    add column origen_transcripcion text,
    add column origen_url_audio varchar(500);

create table conversaciones (
    id varchar(40) not null primary key,
    canal varchar(20) not null,
    identificador_externo varchar(100) not null,
    estado varchar(30) not null,
    pedido_id varchar(40),
    reserva_id varchar(40),
    iniciada_en timestamp not null,
    actualizada_en timestamp not null
);

create index ix_conversaciones_canal_identificador
    on conversaciones (canal, identificador_externo);

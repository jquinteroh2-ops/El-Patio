alter table reservas
    add column canal varchar(20) not null default 'presencial';

alter table conversaciones
    add column datos_contexto text;

create table mensajes_whatsapp_procesados (
    message_id varchar(100) not null primary key,
    procesado_en timestamp not null
);

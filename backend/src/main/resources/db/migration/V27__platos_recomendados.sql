-- Platos recomendados por la casa.
--
-- El administrador marca en la carta los que quiere destacar, y el sitio
-- público los muestra en su propia sección arriba de la carta. Es un sí/no por
-- plato: el orden de la sección es el de la carta.
alter table items_carta add column recomendado boolean not null default false;

-- La base nunca acepto el rol `recepcion`.
--
-- V1 escribio la restriccion con los cuatro roles que existian entonces. V3
-- abrio el canal de pedidos externos y con el nacio RECEPCION: quedo en el
-- enumerado de Java, en las reglas de seguridad y en la pantalla de personal,
-- pero nadie volvio a esta linea. El resultado es que crear a la persona de
-- recepcion desde /admin/configuracion fallaba contra la base, con un error
-- que no explica nada porque el problema esta tres capas mas abajo.
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;

ALTER TABLE usuarios
  ADD CONSTRAINT usuarios_rol_check
  CHECK (rol IN ('mesero', 'cocina', 'recepcion', 'cajero', 'administrador'));

-- El cierre guarda lo que el contador va a necesitar despues.
--
-- Hasta ahora el corte guardaba la venta y sus totales por medio de pago y por
-- canal. Eso alcanza para entregar la caja, y no alcanza para declarar: el INC
-- se declara cada dos meses, y para entonces el turno del 3 de agosto ya no se
-- puede reconstruir preguntandole a la pantalla, que solo muestra el turno en
-- curso.
--
-- Las columnas van con valor por defecto 0 y no nulas: un cierre viejo no tenia
-- estas cifras, y dejarlas nulas obligaria a todo lector a decidir que hacer
-- con el vacio. Cero dice lo mismo y no se puede malinterpretar como monto.
ALTER TABLE cierres_caja
  ADD COLUMN base_gravable    BIGINT  NOT NULL DEFAULT 0,
  ADD COLUMN base_no_gravada  BIGINT  NOT NULL DEFAULT 0,
  ADD COLUMN total_cargos     BIGINT  NOT NULL DEFAULT 0,
  ADD COLUMN porcentaje_inc   INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN descuentos       BIGINT  NOT NULL DEFAULT 0,
  ADD COLUMN comensales       INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN lineas_anuladas  INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN valor_anulado    BIGINT  NOT NULL DEFAULT 0;

COMMENT ON COLUMN cierres_caja.base_gravable IS
  'Alimentos y bebidas del turno, antes de impuesto y de propina. Base del INC.';
COMMENT ON COLUMN cierres_caja.base_no_gravada IS
  'Cargos adicionales y domicilios: no causan INC.';
COMMENT ON COLUMN cierres_caja.valor_anulado IS
  'Lo anulado en el turno. No entra en la venta; se guarda para poder auditarla.';

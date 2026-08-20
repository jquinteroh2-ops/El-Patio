-- El sistema entra a producción: se retira lo que solo servía para mostrarlo.
--
-- `simular_sin_conexion` era un interruptor para provocar una caída de WiFi
-- falsa durante una demostración. En un restaurante que ya está cobrando, un
-- botón que apaga la comandera a propósito no es una función: es un accidente
-- esperando. La pérdida de señal real la detecta el navegador solo.
ALTER TABLE ajustes DROP COLUMN simular_sin_conexion;

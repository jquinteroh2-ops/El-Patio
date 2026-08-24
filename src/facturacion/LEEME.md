# Este módulo está apagado

El Patio **no emite documentos fiscales**. Desde que el restaurante adoptó
Globalsoft como ERP, la factura electrónica, la numeración, el cálculo del
impuesto fiscal y el reporte a la DIAN son responsabilidad del ERP. Tener dos
sistemas emitiendo contra el mismo NIT es peor que no tener ninguno.

## Por qué el código sigue aquí

No es código muerto por descuido. Es la salida si Globalsoft no llega a ofrecer
integración y el restaurante tiene que emitir por su cuenta. Está completo y
funcionaba: modelo DIAN, catálogos oficiales, CUFE/CUDE con SHA-384, reparto del
INC por línea sin perder un peso, y la representación gráfica térmica.

Volver a encenderlo es poner `VITE_FACTURACION_INTERNA_HABILITADA=true` y llenar
la resolución en `compartido/config.ts`. No hay que reescribir nada.

## Qué NO hacer

- No importar nada de aquí desde una pantalla. Hoy el subgrafo está cerrado:
  solo `impresion/FacturaTermica.tsx` importa este módulo, y a él no lo importa
  nadie. Esa es la comprobación de que está desconectado, y conviene que siga
  dando ese resultado.
- No llenar `NUMERACION_DIAN` con datos reales «para dejarlo listo». Con el
  interruptor apagado no sirve de nada, y con el interruptor encendido por
  accidente haría que el sistema imprima documentos que la DIAN no conoce.

## Lo que sí quedó vivo

El cobro. La cuenta de la mesa, el anticipo antes de mandar a cocina y el
comprobante interno que se le entrega al cliente son operación del restaurante,
no documentos fiscales, y ninguno se llama factura en ninguna pantalla.

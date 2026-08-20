# Facturación electrónica ante la DIAN

Análisis para decidir cuándo y cómo El Patio pasa a emitir documentos
electrónicos. **No es una implementación**: es lo que hay que saber antes de
construirla.

---

## Antes de leer: qué está verificado y qué no

Este documento lo escribió el mismo desarrollo que construyó el sistema, no un
contador ni un abogado tributarista. Conviene separar tres cosas:

**Lo que sí puedo afirmar con certeza**, porque lo leí en el código de este
repositorio: qué datos guarda hoy el sistema, qué le falta, y cuánto trabajo es
agregarlo. Las secciones *«Qué le falta al modelo»* y *«Estimación de esfuerzo»*
son firmes.

**Lo que está sujeto a verificación**: los detalles de la norma. La Resolución
000165 de 2023 fijó un calendario por grupos de contribuyentes con fechas que ya
pasaron, y desde entonces la DIAN ha emitido resoluciones que la modifican. El
valor de la UVT cambia cada año.

> **Antes de tomar cualquier decisión con plata de por medio, confirme con el
> contador del restaurante:**
>
> 1. El texto vigente de la Resolución 000165 de 2023 y sus modificaciones.
> 2. El valor de la UVT del año en curso.
> 3. Si El Patio ya está obligado y desde cuándo, según su grupo.
> 4. Si hay sanciones acumuladas por no haber empezado.
>
> Nada de lo que sigue reemplaza esa consulta.

---

## 1. Lo que exige la norma

### El tiquete POS dejó de ser un documento en papel

La Resolución 000165 de 2023 consolidó el marco de facturación electrónica en
Colombia y reguló los **documentos equivalentes electrónicos**, entre ellos el
**tiquete de máquina registradora con sistema P.O.S.** Lo que antes era un papel
que salía de una impresora térmica pasó a ser un documento con estructura XML,
firmado, numerado con autorización de la DIAN y transmitido a la entidad.

El papel sigue existiendo, pero cambia de naturaleza: deja de ser el documento y
pasa a ser su **representación gráfica**. Por eso lleva el código QR y el CUDE.

### El límite de 5 UVT es lo que de verdad afecta a El Patio

Aquí está el punto que cambia la conversación entera. La Ley 2155 de 2021
modificó el artículo 616-1 del Estatuto Tributario y limitó el tiquete POS a
operaciones de **hasta 5 UVT**. Por encima de ese monto **no se puede emitir
tiquete POS**: hay que emitir **factura electrónica de venta**.

Con una UVT del orden de los $50.000 —**confirme el valor del año**—, 5 UVT
rondan los **$250.000**.

Ahora mire lo que eso significa para un restaurante de mantel:

| Situación típica | ¿Cabe en tiquete POS? |
|---|---|
| Un domicilio de $55.000 | Sí |
| Dos personas, $130.000 | Sí |
| Cuatro personas con cócteles, $280.000 | **No. Factura electrónica** |
| Una mesa de ocho, $600.000 | **No. Factura electrónica** |
| Zona privada con descorche, $900.000 | **No. Factura electrónica** |

**El Patio no puede resolver esto solo con el tiquete POS electrónico.** Las
mesas grandes —que son justamente las de mayor margen— necesitan factura
electrónica de venta, y una factura exige identificar al adquiriente: tipo y
número de documento, nombre o razón social, correo electrónico.

Eso no es un cambio técnico. Es un cambio en **cómo se cobra una mesa**: hay que
pedirle la cédula al cliente antes de cerrar la cuenta.

Para saber cuánto pesa esto en el caso concreto de El Patio, con datos reales y
no con suposiciones, corra esto cuando lleve unos meses de operación:

```sql
-- Cuántas cuentas superan las 5 UVT. Ajuste el valor de la UVT del año.
SELECT
  count(*) FILTER (WHERE total <= 5 * 50000) AS caben_en_pos,
  count(*) FILTER (WHERE total  > 5 * 50000) AS exigen_factura,
  round(100.0 * count(*) FILTER (WHERE total > 5 * 50000) / count(*), 1)
    AS porcentaje_que_exige_factura,
  sum(total) FILTER (WHERE total > 5 * 50000) AS venta_que_exige_factura
FROM pagos
WHERE fecha_hora >= now() - interval '90 days';
```

Si el porcentaje es alto —y en un restaurante de este tipo probablemente lo
sea—, el proyecto no es «poner el tiquete POS electrónico»: es **implementar
factura electrónica de venta**, que es notablemente más trabajo.

### El otro efecto del 616-1: el cliente puede exigir factura

El tiquete POS **no le da al comprador derecho a impuestos descontables ni a
costos y deducciones**. Por eso el artículo permite que quien lo necesite
—alguien que va a almorzar de negocios y quiere deducir el gasto— **exija la
factura electrónica**, sin importar el monto.

Traducido a la caja: aunque la cuenta sea de $80.000, si el cliente pide
factura, hay que poder emitirla. **No hay forma de vivir solo con el tiquete
POS.**

### Qué debe llevar el documento

Independientemente de si es tiquete POS electrónico o factura de venta, hay un
conjunto de elementos que hoy el sistema no produce:

- **Numeración autorizada por la DIAN**: prefijo, rango desde–hasta y vigencia.
  Es continua y no se reinicia; no tiene nada que ver con el consecutivo diario
  que el sistema usa hoy para las comandas.
- **CUFE** (factura) o **CUDE** (documento equivalente): un código único
  calculado con un algoritmo de hash sobre campos específicos del documento más
  la clave técnica que la DIAN entrega al habilitarse.
- **Firma digital** del emisor o del proveedor tecnológico.
- **XML en formato UBL** con la estructura que exige la DIAN.
- **Código QR** en la representación gráfica.
- **Desglose de tributos por línea**, con el código del tributo. El INC tiene su
  propio código en el catálogo de la DIAN.
- **Identificación del adquiriente** (en factura; en tiquete POS puede ser
  consumidor final).
- **Notas crédito electrónicas** para anular. Hoy el sistema anula internamente
  con motivo, que es correcto para auditoría interna pero **no reemplaza la nota
  crédito** ante la DIAN.

---

## 2. Los tres proveedores

El Patio no va a implementar el protocolo de la DIAN por su cuenta: eso implica
habilitarse como facturador propio, firmar digitalmente, manejar el envío y la
contingencia. Lo razonable es contratar un **proveedor tecnológico autorizado**
que exponga una API y se encargue del trámite.

> Los tres son proveedores colombianos conocidos. **Los precios no aparecen aquí
> a propósito**: cambian seguido y publicarlos desactualizados sería peor que no
> ponerlos. Pida cotización a los tres con el volumen real del restaurante
> (documentos por mes).

### Factus

**Qué es.** Un servicio de facturación electrónica pensado para desarrolladores:
API primero, sin un ERP encima.

**A favor**
- Es el que menos fricción tiene para integrarse a un sistema que ya existe,
  como este. Se consume como una API y ya.
- No obliga a adoptar un software contable ni a cambiar la operación.
- Al ser más pequeño, suele ser más barato por documento en volúmenes bajos.

**En contra**
- Empresa más pequeña que las otras dos: menos respaldo si algo se rompe un
  viernes por la noche.
- Menos integraciones con software contable, si el contador ya usa otro.
- Comunidad y documentación más reducidas: menos gente ha resuelto el mismo
  problema antes.

**Para quién.** Para El Patio, si lo que se quiere es **exactamente esto**:
conectar el sistema que ya funciona con la DIAN, sin cambiar nada más.

### Alegra

**Qué es.** Software contable en la nube con presencia en varios países de
Latinoamérica y facturación electrónica incluida, con API documentada.

**A favor**
- La API está bien documentada y es de las más usadas en Colombia: es fácil
  encontrar quién ya resolvió un problema parecido.
- Si el contador ya trabaja con Alegra, la contabilidad y la facturación quedan
  en el mismo sitio y se ahorra el traspaso manual.
- Respaldo de una empresa consolidada.

**En contra**
- Es un producto más grande de lo que el restaurante necesita si solo quiere
  facturar; se paga por funciones que no se van a usar.
- Puede empujar a llevar inventario y contabilidad ahí, que es justamente lo que
  el plan del proyecto dejó **fuera de alcance**.

**Para quién.** Si el contador ya usa Alegra o está dispuesto a cambiarse. Ahí
el ahorro no está en el desarrollo, está en el trabajo mensual del contador.

### Siigo

**Qué es.** Uno de los proveedores de software contable más grandes de Colombia,
con facturación electrónica y API.

**A favor**
- El más establecido de los tres y el más conocido por los contadores
  colombianos: es probable que el del restaurante ya lo maneje.
- Soporte y respaldo empresarial.
- Cubre nómina y contabilidad completa, si algún día hace falta.

**En contra**
- El más pesado de los tres. Su API es la menos cómoda para una integración
  puntual como esta.
- Orientado a la empresa mediana; para un restaurante de dieciocho mesas es
  bastante más de lo que se necesita.
- Suele ser el más costoso.

**Para quién.** Si el contador insiste en Siigo, o si el dueño ya prevé crecer a
varios locales con nómina formal.

### Recomendación

**Pregúntele primero al contador del restaurante qué usa.** Esa respuesta pesa
más que cualquier comparación técnica: si la facturación queda donde el contador
ya trabaja, se ahorra un traspaso manual todos los meses, y ese costo recurrente
supera rápido cualquier diferencia de precio por documento.

Si el contador no tiene preferencia, **Factus** es el que mejor encaja con lo
que hay construido: se integra como una API contra un sistema que ya funciona,
sin arrastrar un ERP que nadie pidió.

Sea cual sea, exija dos cosas antes de firmar:

1. **Ambiente de pruebas** (habilitación) separado del de producción. Sin él no
   se puede probar nada sin emitir documentos reales.
2. **Que el proveedor gestione el trámite de habilitación** ante la DIAN. Es
   papeleo y toma semanas; que lo haga quien lo hace todos los días.

---

## 3. Qué le falta al modelo actual

Esta sección sí es firme: sale de leer el código de este repositorio.

### Lo que ya está y sirve

| Ya existe | Dónde |
|---|---|
| Detalle de cada producto con nombre, cantidad y precio unitario | `items_orden` |
| Modificadores con su precio, congelado al momento de la venta | `items_orden.modificadores_seleccionados` |
| INC calculado y **guardado** con el pago | `pagos.inc` |
| Base gravable separada de lo no gravado | `pagos.subtotal` vs. `cargos_adicionales`, `costo_envio` |
| Propina identificada y separada del total gravado | `pagos.propina` |
| Medio de pago, con desglose si fue mixto | `pagos.metodo`, `pagos.divisiones` |
| Fecha y hora exacta del cobro | `pagos.fecha_hora` |
| Datos fiscales del emisor, con espacio previsto para la resolución | `config.ts → DATOS_FISCALES` |
| Anulación con motivo y comanda de reemplazo | `ordenes.motivo_anulacion`, `orden_reemplazo_id` |

El trabajo de las fases anteriores dejó esto en buena forma. **La base gravable
está bien separada**, que es lo que suele estar mal en sistemas que se adaptan a
la carrera: el INC no toca los cargos adicionales ni el domicilio, y la propina
está aparte. Eso se traduce directo a las líneas del XML.

### Lo que falta

#### a) Identificación del adquiriente

Hoy solo hay datos de contacto, y **solo en domicilios**:

```text
ordenes.cliente_nombre, cliente_telefono, cliente_direccion, cliente_barrio
```

Una mesa del salón **no guarda nada del cliente**. Para factura electrónica
hacen falta:

| Campo | Para qué |
|---|---|
| `tipo_documento` | CC, NIT, CE, pasaporte (catálogo de la DIAN) |
| `numero_documento` | Con dígito de verificación si es NIT |
| `razon_social` o nombre completo | Como aparece en el RUT |
| `correo_electronico` | La DIAN exige enviarle el documento al adquiriente |
| `direccion_fiscal` y `municipio` | Con código DANE del municipio |
| `responsabilidad_fiscal` | Código del catálogo de la DIAN |

Esto **cambia la operación de la caja**, no solo el software: alguien tiene que
pedirle la cédula al cliente. Vale la pena diseñar la pantalla para que sea
rápido —recordar clientes frecuentes por teléfono, por ejemplo— o va a doler
todas las noches.

#### b) Numeración autorizada

Hoy existe `ordenes.numero`: un consecutivo **diario que se reinicia cada
jornada**. Sirve perfectamente para lo que fue hecho —identificar comandas en el
salón— pero **no sirve como numeración fiscal**, que es continua, tiene prefijo,
rango autorizado y fecha de vencimiento.

Hace falta una tabla nueva:

```sql
CREATE TABLE numeracion_dian (
  id            TEXT PRIMARY KEY,
  tipo          TEXT NOT NULL,   -- factura / tiquete pos / nota credito
  prefijo       TEXT NOT NULL,
  numero_desde  BIGINT NOT NULL,
  numero_hasta  BIGINT NOT NULL,
  numero_actual BIGINT NOT NULL,
  resolucion    TEXT NOT NULL,
  vigente_hasta DATE NOT NULL,
  clave_tecnica TEXT             -- la entrega la DIAN al habilitarse
);
```

Con el mismo bloqueo de fila que ya usa el consecutivo diario, que resolvió el
problema de dos meseros abriendo mesa a la vez y sirve igual aquí. **Y con una
alerta cuando el rango se esté agotando o la resolución esté por vencer**: si se
acaba la numeración un sábado a las nueve de la noche, el restaurante no puede
cobrar.

#### c) El documento electrónico en sí

Tabla nueva para lo que devuelve la DIAN:

```sql
CREATE TABLE documentos_electronicos (
  id             TEXT PRIMARY KEY,
  pago_id        TEXT NOT NULL REFERENCES pagos (id),
  tipo           TEXT NOT NULL,   -- factura / tiquete / nota_credito
  prefijo        TEXT NOT NULL,
  numero         BIGINT NOT NULL,
  cufe           TEXT,            -- CUFE o CUDE
  estado         TEXT NOT NULL,   -- pendiente / enviado / aceptado / rechazado
  xml            TEXT,
  url_pdf        TEXT,
  qr             TEXT,
  enviado_en     TIMESTAMPTZ,
  respuesta_dian TEXT,
  intentos       INTEGER NOT NULL DEFAULT 0
);
```

#### d) Códigos que hoy son propios y deben mapearse

El sistema usa sus propios códigos, que son legibles y están bien para operar,
pero la DIAN tiene catálogos:

| Nuestro | Debe mapearse a |
|---|---|
| `metodo: efectivo / tarjeta / transferencia` | Código de medio de pago de la DIAN |
| `id` del producto (`p01`, `p43`) | Código del producto, con su estándar |
| — | Unidad de medida (falta por completo) |
| `porcentajeInc` global | Tributo por línea, con código de INC |
| `Rol`, `Destino`, etc. | No aplican: son internos |

Ojo con el INC: hoy se calcula **sobre el subtotal completo**. El XML lo quiere
**por línea**. No es difícil —el dato está—, pero hay que redistribuirlo con
cuidado para que la suma de las líneas dé exactamente el total, sin un peso de
diferencia. El mismo problema del redondeo que ya se resolvió en la división de
cuentas.

#### e) Contingencia

**Es la parte que más se subestima.** ¿Qué pasa si la DIAN o el proveedor no
responden a las nueve de la noche de un sábado?

La respuesta no puede ser «el restaurante no cobra». La norma contempla
mecanismos de contingencia, y el sistema necesita:

- Cobrar igual y **encolar** el documento.
- Reintentar solo, con reintentos espaciados.
- Un indicador visible de cuántos documentos están pendientes de transmitir.
- Alerta si algo lleva demasiado tiempo sin salir.

La buena noticia: **esto ya existe conceptualmente**. La cola de envíos
pendientes de `conexion.ts` resolvió el mismo problema para las comandas, y el
patrón se reutiliza.

#### f) Notas crédito

Hoy anular una comanda cobrada genera una anulación interna con motivo y una
comanda de reemplazo. Está bien para auditoría, pero ante la DIAN **una factura
emitida solo se corrige con una nota crédito electrónica**, que también se
numera, se firma y se transmite.

El punto donde engancha ya existe: `ServicioComandas.anularOrden` es el único
camino para anular. Ahí se agrega la emisión de la nota.

---

## 4. Estimación de esfuerzo

En días de trabajo de una persona con este sistema ya conocido. **No incluye el
trámite de habilitación ante la DIAN**, que va por fuera y toma semanas.

| Trabajo | Días | Notas |
|---|---|---|
| Modelo de datos y migraciones | 1–2 | Las tres tablas nuevas y los campos del adquiriente |
| Integración con la API del proveedor | 3–5 | Mapeo, autenticación, manejo de errores. El rango depende del proveedor |
| Numeración autorizada, con bloqueo y alertas | 1–2 | Se reutiliza el patrón del consecutivo diario |
| Captura del adquiriente en las pantallas | 2–3 | Caja, recepción y sitio público. **La parte más delicada de diseño** |
| Desglose de tributos por línea | 1–2 | El dato existe; el cuidado está en el redondeo |
| Notas crédito para anulaciones | 2 | Engancha en `anularOrden` |
| Representación gráfica: QR y CUFE en la térmica | 1–2 | El módulo de impresión ya está aislado |
| Contingencia y cola de reintentos | 2–3 | Se reutiliza el patrón de `conexion.ts` |
| Pruebas en ambiente de habilitación | 2–3 | Contra el ambiente de pruebas del proveedor |
| **Desarrollo** | **15–24** | ≈ 3 a 5 semanas |

Más, por fuera del desarrollo:

| Fuera del desarrollo | Cuánto |
|---|---|
| Trámite de habilitación ante la DIAN | Semanas. Lo hace el proveedor |
| Definir el procedimiento de caja con el personal | 1–2 días de acompañamiento |
| Acompañamiento del contador | A convenir |

**Rango honesto: entre tres y cinco semanas de desarrollo**, más el trámite. La
horquilla es amplia porque depende de dos cosas que hoy no se saben: qué
proveedor se escoge y si hay que emitir factura de venta además del tiquete POS
—que, como se explicó arriba, probablemente sí—.

---

## 5. Qué hacer primero

En este orden, y las tres primeras no son trabajo de programación:

1. **Hable con el contador del restaurante.** Confirme si El Patio ya está
   obligado, desde cuándo, si hay algo pendiente, y qué proveedor usa. Esa
   conversación puede cambiar todo lo demás.

2. **Averigüe cuántas cuentas superan las 5 UVT**, con la consulta de la
   sección 1 sobre datos reales. Define si el proyecto es «tiquete POS» o
   «factura electrónica», que no cuestan lo mismo.

3. **Pida cotización a los tres proveedores** con el volumen real de documentos.

4. **Complete `DATOS_FISCALES`** en `src/compartido/config.ts` con la razón
   social, el NIT y las responsabilidades reales del RUT. Los que están son de
   ejemplo, y ese campo se llena de todos modos, con o sin facturación
   electrónica.

5. **Recién entonces**, empiece el desarrollo.

---

## Lo que este sistema ya hizo bien para este momento

Vale la pena decirlo, porque ahorra trabajo cuando llegue el día:

- **El comprobante que se imprime hoy nunca dijo ser una factura.** No se titula
  «Factura», no lleva un número de resolución inventado, y el pie dice
  textualmente que es un comprobante interno. Cuando llegue la resolución de la
  DIAN, se llena el campo que ya está previsto y el documento cambia de
  naturaleza sin reescribirlo.

- **La base gravable está bien separada desde el principio.** El INC se calcula
  sobre alimentos y bebidas, nunca sobre cargos adicionales ni sobre el
  domicilio, y la propina está identificada aparte. Eso se traduce directo a las
  líneas del XML. Sistemas que meten todo en un solo total tienen que
  reconstruirlo, y ahí es donde aparecen las diferencias de un peso que la DIAN
  rechaza.

- **El consecutivo ya se entrega bajo bloqueo de fila**, sin saltos ni
  repetidos. La numeración autorizada necesita exactamente esa garantía, y el
  patrón se reutiliza tal cual.

- **La impresión está detrás de una interfaz.** Agregar el QR y el CUFE al
  ticket es tocar un componente, no las cinco pantallas que imprimen.

- **La cola de reintentos ya existe** y resolvió el mismo problema para las
  comandas. La contingencia de facturación es el mismo patrón.

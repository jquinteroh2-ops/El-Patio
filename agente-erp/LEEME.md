# Agente ERP de El Patio

El mensajero entre El Patio, que corre en la nube, y **Globalsoft**, que está
instalado en un computador del restaurante.

## Por qué existe

Globalsoft está detrás del router del restaurante. Desde la nube no hay forma de
llamarlo sin abrir puertos, y abrir puertos hacia la contabilidad del
restaurante no es buena idea. El agente le da la vuelta al problema: vive en el
computador de Globalsoft y es **él** el que sale a preguntarle a la nube. Al
router solo le pasan conexiones de salida por HTTPS, igual que un navegador.

```
 Restaurante (red local)                         Nube (Railway)
┌──────────────────────────────┐               ┌──────────────────────────┐
│ Globalsoft  ◄── entrega ──  Agente ── HTTPS ──►  El Patio /api/agente-erp │
│                              │   (pregunta)  │  bandeja de salida (erp_outbox)
└──────────────────────────────┘               └──────────────────────────┘
```

## Cómo trabaja

1. Cada 30 s le pide a la nube las ventas pendientes (`POST /reclamar`). Cada
   una queda reservada 10 minutos. Si el agente no contesta en ese tiempo,
   vuelve sola a la cola.
2. Se la entrega a Globalsoft con el **Entregador** configurado.
3. Anota la entrega en `datos/entregadas.jsonl` **antes** de avisar.
4. Le cuenta a la nube cómo le fue (`POST /envios/{id}/resultado`):
   `confirmado` (con número de documento), `rechazado` (se reintenta) o
   `en_espera` (entregada, sin número todavía).

Si la nube vuelve a ofrecer una venta que ya se entregó (porque se fue la luz
justo antes de avisar), el agente la encuentra en su registro por la llave de
idempotencia y **repite la respuesta sin entregarla otra vez**. Una venta nunca
llega dos veces a Globalsoft.

Si se cae el internet, el agente espera cada vez más, hasta 5 minutos, y sigue
solo cuando vuelve. Las ventas no se pierden: quedan en la nube hasta que el
agente diga que las entregó.

## Lo que falta: cómo recibe Globalsoft

Es la única pieza que no se puede terminar sin ver Globalsoft. Hoy existe un
solo modo:

| `entrega.modo` | Qué hace | Estado |
|---|---|---|
| `carpeta` | Deja un archivo JSON por venta en una carpeta | Listo. Sirve para probar y para ver los datos |
| (formato de Globalsoft) | Archivo en el formato que importe Globalsoft, su base de datos o su servicio web | **Falta**: depende de lo que se averigüe en el restaurante |

Para agregarlo se escribe otra clase que implemente `Entregador` y se suma al
`switch` de `Agente.main`. Lo demás no cambia.

Hay que averiguar en el restaurante:

- ¿Globalsoft importa ventas o facturas desde un archivo (Excel, CSV, TXT, XML)?
  Si sí, un archivo de ejemplo y la pantalla de importación.
- ¿Tiene servicio web o API? Preguntarle al ingeniero que lo instaló.
- La lista de productos de Globalsoft con sus códigos, para cruzarla con la carta.
- El Windows de ese computador y si queda prendido todo el día.

## Activarlo en la nube (Railway, servicio backend)

```
ELPATIO_ERP_ADAPTADOR=agente
ELPATIO_ERP_AGENTE_TOKEN=<llave larga y al azar>
```

Con `agente`, la tarea de cada minuto del backend deja de mandar ventas: el
agente es el único que las lleva. La misma llave va en `nube.llave` del agente.
Para generar una:

```powershell
[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }) -as [byte[]])
```

## Armar el instalador (en el computador de desarrollo)

Hace falta JDK 21 (trae `jpackage`) y Maven.

```powershell
powershell -ExecutionPolicy Bypass -File empaquetar.ps1
```

Queda `dist\AgenteElPatio\` con `AgenteElPatio.exe`, su propio Java adentro,
`instalar.ps1` y el ejemplo de configuración. Se copia entera a una memoria.

## Instalarlo en el restaurante

En el computador de Globalsoft, **con permiso de los dueños**:

1. Copiar la carpeta `AgenteElPatio` (por ejemplo, al escritorio).
2. Abrir PowerShell **como administrador**, entrar a esa carpeta y correr:
   `powershell -ExecutionPolicy Bypass -File instalar.ps1`
3. Se abre el Bloc de notas con `agente.properties`: escribir la llave en
   `nube.llave`, guardar y cerrar.
4. El instalador hace una pasada de prueba y deja el agente arrancando solo
   con el computador (Programador de tareas → "Agente El Patio").

Bitácora: `C:\AgenteElPatio\datos\registros\agente-0.log`.

Para quitarlo: `instalar.ps1 -Desinstalar`.

## Probar sin instalar

```powershell
java -jar target\agente-erp.jar ruta\agente.properties --una-vez
```

`--una-vez` hace una sola pasada y termina.

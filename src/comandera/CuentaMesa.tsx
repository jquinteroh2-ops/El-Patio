import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Printer,
  Receipt,
  SplitSquareHorizontal,
  Tag,
  Wallet,
} from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { OrdenDetallada } from '@/compartido/mockApi'
import { calcularCuenta, precioItem } from '@/compartido/calculos'
import { useSesionActiva } from '@/compartido/auth'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Orden, Pago } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { ControlPropina, type Propina } from './ControlPropina'
import { HojaDivision } from './HojaDivision'
import { HojaPago, type PartePago } from './HojaPago'
import { Comprobante } from './Comprobante'
import { imprimir } from '@/impresion/impresora'
import { ComprobanteTermico } from '@/impresion/ComprobanteTermico'
import { FacturaTermica } from '@/impresion/FacturaTermica'
import { emisor } from '@/facturacion/emisor'
import { NOMBRE_MEDIO_PAGO } from '@/facturacion/catalogos'
import type { DocumentoElectronico } from '@/facturacion/tipos'

interface Cobrado {
  pago: Pago
  orden: Orden
  mesaEtiqueta: string
  meseroNombre: string
  /**
   * El documento ante la DIAN.
   *
   * Puede venir en nulo, y esa posibilidad es deliberada: si la emisión falla,
   * el cobro ya ocurrió y no se deshace. La caja tiene que poder entregar un
   * papel igual, aunque sea el comprobante interno, y el documento sale después.
   */
  documento: DocumentoElectronico | null
}

export default function CuentaMesa() {
  const { mesaId = '' } = useParams()
  const navegar = useNavigate()
  const sesion = useSesionActiva()
  const { mostrar } = useAvisos()

  const { datos: detalle, cargando } = useSyncedState<OrdenDetallada | null>(
    () => api.obtenerOrdenDeMesa(mesaId),
    null,
    [mesaId],
    ['ordenes', 'mesas', 'todo'],
  )

  const [propina, setPropina] = useState<Propina>({ porcentaje: 0, valor: 0 })
  const [partes, setPartes] = useState<PartePago[]>([
    { nombre: 'Pago único', valor: 0, metodo: 'efectivo' },
  ])
  const [dividiendo, setDividiendo] = useState(false)
  const [cobrando, setCobrando] = useState(false)
  const [hojaPago, setHojaPago] = useState(false)
  const [cobrado, setCobrado] = useState<Cobrado | null>(null)

  const orden = detalle?.orden ?? null
  const porcentajeInc = detalle?.porcentajeInc ?? 8

  const cuenta = useMemo(
    () =>
      orden
        ? calcularCuenta(orden, porcentajeInc, propina.porcentaje ?? 0, propina.valor)
        : null,
    [orden, porcentajeInc, propina],
  )

  const total = cuenta?.total ?? 0

  // Si cambia el total, la division deja de cuadrar: se vuelve a un solo pago.
  useEffect(() => {
    setPartes((actuales) => {
      if (actuales.length === 1) {
        return actuales[0].valor === total ? actuales : [{ ...actuales[0], valor: total }]
      }
      return [{ nombre: 'Pago único', valor: total, metodo: actuales[0].metodo }]
    })
  }, [total])

  if (cargando) return <Cargando pantallaCompleta mensaje="Preparando la cuenta" />

  /**
   * Saca el comprobante en la termica despues de cobrar.
   *
   * Si la cuenta se dividio, sale un tiquete por parte: cada comensal se lleva
   * el suyo con lo que le toco pagar. Sin eso, cuatro personas que dividieron
   * se quedarian con un solo papel y ninguno podria justificar su gasto.
   */
  const imprimirComprobanteFinal = () => {
    if (!cobrado) return
    const cuentaFinal = calcularCuenta(cobrado.orden, porcentajeInc, 0, cobrado.pago.propina)
    const divisiones = cobrado.pago.divisiones ?? []

    // El documento fiscal es UNO por venta, aunque la cuenta se haya repartido
    // entre cuatro personas: la comida es una sola y se consumió en una mesa.
    // Sale primero.
    //
    // PENDIENTE CON EL CONTADOR: si los comensales necesitan cada uno su
    // factura a su nombre —el caso del almuerzo de negocios que cada quien
    // deduce—, lo correcto es emitir un documento por parte, con sus propias
    // líneas. Eso cambia el reparto del impuesto y no se decide desde aquí.
    if (cobrado.documento) {
      imprimir(
        <FacturaTermica
          documento={cobrado.documento}
          etiqueta={cobrado.mesaEtiqueta}
          atendidoPor={cobrado.meseroNombre}
          medioPagoLegible={NOMBRE_MEDIO_PAGO[cobrado.pago.metodo]}
        />,
      )
    } else {
      // Sin documento emitido no se imprime algo que parezca uno. Sale el
      // comprobante interno, que dice lo que es.
      imprimir(
        <ComprobanteTermico
          orden={cobrado.orden}
          cuenta={cuentaFinal}
          etiqueta={cobrado.mesaEtiqueta}
          atendidoPor={cobrado.meseroNombre}
          metodo={cobrado.pago.metodo}
        />,
      )
    }

    // Y detrás, un papel por comensal con lo que le tocó pagar. Estos NO son
    // documentos fiscales y no lo dicen a medias: son el comprobante interno,
    // que ya se titula como tal.
    if (divisiones.length > 1) {
      for (const parte of divisiones) {
        imprimir(
          <ComprobanteTermico
            orden={cobrado.orden}
            cuenta={cuentaFinal}
            etiqueta={cobrado.mesaEtiqueta}
            atendidoPor={cobrado.meseroNombre}
            parte={parte}
          />,
        )
      }
    }
  }

  // ---- Comprobante, despues de cobrar ----
  if (cobrado) {
    return (
      <div className="flex min-h-dvh flex-col bg-noche-950 px-4 py-6">
        <div className="mx-auto mb-5 flex w-full max-w-md items-center gap-2 text-estado-listo">
          <CheckCircle2 className="h-5 w-5" aria-hidden />
          <p className="font-semibold">
            Cuenta cobrada · {cobrado.mesaEtiqueta} quedó libre
          </p>
        </div>

        {/* El cobro salió; el documento no. Se queda escrito en la pantalla y no
            en un aviso pasajero, porque es una tarea pendiente de la caja: esa
            venta tiene que quedar documentada antes del cierre del día. */}
        {!cobrado.documento && (
          <div className="mx-auto mb-4 flex w-full max-w-md items-start gap-2 rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <p>
              El documento electrónico de esta venta quedó pendiente. El pago sí
              quedó registrado; avise en caja para emitirlo antes del cierre.
            </p>
          </div>
        )}

        <Comprobante {...cobrado} />

        <div className="mx-auto mt-5 flex w-full max-w-md flex-col gap-2">
          <Boton
            variante="secundario"
            tamano="grande"
            bloque
            icono={<Printer className="h-5 w-5" aria-hidden />}
            onClick={() => imprimirComprobanteFinal()}
          >
            {cobrado.documento ? 'Imprimir documento' : 'Imprimir comprobante'}
          </Boton>
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            onClick={() => navegar('/comandera', { replace: true })}
          >
            Volver al mapa de mesas
          </Boton>
        </div>
      </div>
    )
  }

  if (!orden || !detalle || !cuenta) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-noche-950 px-6">
        <Vacio
          icono={Receipt}
          titulo="Esta mesa no tiene cuenta abierta"
          descripcion="Puede que ya la hayan cobrado desde otra pestaña."
          accion={
            <Boton variante="principal" onClick={() => navegar('/comandera')}>
              Volver al mapa de mesas
            </Boton>
          }
        />
      </div>
    )
  }

  const etiquetaMesa = detalle.mesa.nombre ?? `Mesa ${detalle.mesa.numero}`
  const vigentes = orden.items.filter((i) => i.estado !== 'anulado')

  /**
   * Emite el documento electrónico de la venta ya cobrada.
   *
   * Nunca tumba el cobro. Si algo falla —no hay red, el proveedor no responde,
   * el documento no cuadra— se avisa en pantalla y se sigue: el pago está hecho
   * y la mesa tiene que quedar libre. El documento pendiente es un problema de
   * la caja, no del cliente que ya pagó y quiere irse.
   *
   * Hoy el emisor de pruebas no transmite nada. Cuando entre el proveedor real,
   * aquí mismo es donde el documento se encola para reintentar.
   */
  const emitirDocumento = async (pago: Pago): Promise<DocumentoElectronico | null> => {
    if (!orden) return null
    try {
      // La cuenta se recalcula con la propina REAL que quedó en el pago, no con
      // la que estaba en pantalla: entre una y otra el cajero pudo cambiarla.
      const cuentaFinal = calcularCuenta(orden, porcentajeInc, 0, pago.propina)
      return await emisor.emitir({ orden, cuenta: cuentaFinal, metodo: pago.metodo })
    } catch (e) {
      mostrar(
        e instanceof Error
          ? `Cobro registrado, pero el documento quedó pendiente: ${e.message}`
          : 'Cobro registrado, pero el documento electrónico quedó pendiente',
        'error',
      )
      return null
    }
  }

  const cobrar = async () => {
    setCobrando(true)
    try {
      const metodosDistintos = new Set(partes.map((p) => p.metodo))
      const metodo =
        partes.length === 1 || metodosDistintos.size === 1 ? partes[0].metodo : 'mixto'

      const pago = await api.registrarPago({
        ordenId: orden.id,
        porcentajePropina: propina.porcentaje ?? 0,
        propina: propina.valor,
        metodo,
        divisiones: partes.length > 1 ? partes : undefined,
        recibidoPor: sesion.nombre,
      })

      setHojaPago(false)

      // El documento electrónico se emite DESPUÉS de que el cobro quedó
      // registrado, nunca antes. Si se hiciera al revés, un fallo de la DIAN o
      // del proveedor dejaría al restaurante sin poder cobrar una mesa, y esa
      // no puede ser la respuesta un sábado a las nueve de la noche.
      const documento = await emitirDocumento(pago)

      setCobrado({
        pago,
        orden,
        mesaEtiqueta: etiquetaMesa,
        meseroNombre: detalle.meseroNombre,
        documento,
      })
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo registrar el pago', 'error')
    } finally {
      setCobrando(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950 pb-28">
      <header className="sticky top-0 z-30 flex items-center gap-2 border-b border-noche-800 bg-noche-900/95 px-3 py-2.5 backdrop-blur">
        <button
          type="button"
          onClick={() => navegar(`/comandera/mesa/${mesaId}`)}
          aria-label="Volver a la comanda"
          className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl text-noche-300 transition hover:bg-noche-800"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden />
        </button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-semibold text-crema-100">Cuenta · {etiquetaMesa}</h1>
          <p className="truncate text-xs text-noche-400">
            Comanda #{orden.numero} · {orden.comensales} comensales · {detalle.meseroNombre}
          </p>
        </div>
      </header>

      <main className="flex-1 space-y-4 px-3 py-4">
        {/* ---------- Consumo ---------- */}
        <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
          <h2 className="mb-2.5 text-sm font-semibold text-crema-100">Consumo</h2>
          <ul className="space-y-1.5">
            {vigentes.map((item) => (
              <li key={item.id} className="flex items-start justify-between gap-3 text-sm">
                <span className="min-w-0">
                  <span className="text-crema-100">
                    {item.cantidad} × {item.nombre}
                  </span>
                  {item.modificadoresSeleccionados.length > 0 && (
                    <span className="block text-xs text-noche-400">
                      {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                    </span>
                  )}
                </span>
                <span className="shrink-0 tabular-nums text-noche-300">
                  {formatoCOP(precioItem(item))}
                </span>
              </li>
            ))}
          </ul>
        </section>

        {/* ---------- Cargos, siempre a la vista antes de cobrar ---------- */}
        {orden.cargosAdicionales.length > 0 && (
          <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-crema-100">
              <Tag className="h-4 w-4" aria-hidden />
              Cargos adicionales
            </h2>
            <ul className="space-y-1.5 text-sm">
              {orden.cargosAdicionales.map((cargo) => (
                <li key={cargo.id} className="flex justify-between gap-3">
                  <span className="min-w-0">
                    <span className="block truncate text-crema-100">{cargo.nombre}</span>
                    <span className="block text-xs text-noche-500">
                      Agregado por {cargo.agregadoPor}
                    </span>
                  </span>
                  <span className="shrink-0 tabular-nums text-noche-300">
                    {formatoCOP(cargo.valor)}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}

        <Boton
          variante="secundario"
          bloque
          icono={<Printer className="h-5 w-5" aria-hidden />}
          onClick={() =>
            imprimir(
              <ComprobanteTermico
                orden={orden}
                cuenta={cuenta}
                etiqueta={etiquetaMesa}
                atendidoPor={detalle.meseroNombre}
                esPrecuenta
              />,
            )
          }
        >
          Imprimir precuenta
        </Boton>

        <ControlPropina
          subtotal={cuenta.subtotal}
          propina={propina}
          onCambiar={(nueva) => {
            // Cambiar la propina mueve el total, asi que una division previa
            // dejaria de cuadrar. Se rehace, y se avisa en vez de hacerlo callado.
            if (partes.length > 1) mostrar('La división se reinició: cambió el total', 'info')
            setPropina(nueva)
          }}
        />

        {/* ---------- Totales ---------- */}
        <section className="rounded-2xl border border-ambar-500/30 bg-noche-900 p-3">
          <dl className="space-y-1.5 text-sm">
            <div className="flex justify-between text-noche-300">
              <dt>Subtotal</dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.subtotal)}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>Impuesto al consumo {cuenta.porcentajeInc}%</dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.inc)}</dd>
            </div>
            {cuenta.cargosAdicionales > 0 && (
              <div className="flex justify-between text-noche-300">
                <dt>Cargos adicionales</dt>
                <dd className="tabular-nums">{formatoCOP(cuenta.cargosAdicionales)}</dd>
              </div>
            )}
            <div className="flex justify-between text-noche-300">
              <dt>
                Propina <span className="text-xs text-noche-500">(voluntaria)</span>
              </dt>
              <dd className="tabular-nums">{formatoCOP(cuenta.propina)}</dd>
            </div>
            <div className="flex justify-between border-t border-noche-700 pt-2 text-xl font-bold text-crema-100">
              <dt>Total</dt>
              <dd className="tabular-nums text-ambar-300">{formatoCOP(cuenta.total)}</dd>
            </div>
          </dl>
        </section>

        {/* ---------- Division aplicada ---------- */}
        {partes.length > 1 && (
          <section className="animate-entrada rounded-2xl border border-noche-800 bg-noche-900 p-3">
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-crema-100">
              <SplitSquareHorizontal className="h-4 w-4" aria-hidden />
              Cuenta dividida en {partes.length}
            </h2>
            <ul className="space-y-1 text-sm">
              {partes.map((parte, i) => (
                <li key={i} className="flex justify-between text-noche-300">
                  <span>{parte.nombre}</span>
                  <span className="tabular-nums">{formatoCOP(parte.valor)}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        <Boton
          bloque
          icono={<SplitSquareHorizontal className="h-4 w-4" />}
          onClick={() => setDividiendo(true)}
        >
          Dividir la cuenta
        </Boton>
      </main>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-noche-700 bg-noche-900/98 px-3 py-3 pb-segura backdrop-blur">
        <Boton
          variante="exito"
          tamano="grande"
          bloque
          icono={<Wallet className="h-5 w-5" />}
          onClick={() => setHojaPago(true)}
        >
          Cobrar {formatoCOP(cuenta.total)}
        </Boton>
      </div>

      <HojaDivision
        abierta={dividiendo}
        cuenta={cuenta}
        items={orden.items}
        comensales={orden.comensales}
        onCerrar={() => setDividiendo(false)}
        onAplicar={(nuevas) =>
          setPartes(nuevas.map((p) => ({ ...p, metodo: 'efectivo' as const })))
        }
      />

      <HojaPago
        abierta={hojaPago}
        total={cuenta.total}
        partes={partes}
        cobrando={cobrando}
        onCambiarPartes={setPartes}
        onCerrar={() => setHojaPago(false)}
        onCobrar={cobrar}
      />
    </div>
  )
}

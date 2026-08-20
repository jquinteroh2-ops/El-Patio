import { useState } from 'react'
import {
  Banknote,
  Bike,
  CreditCard,
  Lock,
  Printer,
  ShoppingBag,
  Smartphone,
  TrendingDown,
  TrendingUp,
  Utensils,
} from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { ResumenTurno } from '@/compartido/mockApi'
import { useSesionActiva } from '@/compartido/auth'
import { formatoCOP, formatoFecha, formatoFechaHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { CierreCaja } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { imprimir } from '@/impresion/impresora'
import { CierreTermico } from '@/impresion/CierreTermico'

const VACIO: ResumenTurno = {
  fecha: '',
  turno: 'cena',
  ventaTotal: 0,
  totalEfectivo: 0,
  totalTarjeta: 0,
  totalTransferencia: 0,
  propinasTotales: 0,
  incTotal: 0,
  ordenesAtendidas: 0,
  ticketPromedio: 0,
  totalSalon: 0,
  totalDomicilio: 0,
  totalLlevar: 0,
  totalEnvios: 0,
  ventaDiaAnterior: 0,
  ordenesDiaAnterior: 0,
}

export default function Cierre() {
  const sesion = useSesionActiva()
  const { mostrar } = useAvisos()

  const { datos: resumen } = useSyncedState<ResumenTurno>(
    () => api.resumenTurnoActual(),
    VACIO,
    [],
    ['pagos', 'ordenes', 'cierres', 'todo'],
  )
  const { datos: cierres } = useSyncedState<CierreCaja[]>(
    () => api.listarCierres(),
    [],
    [],
    ['cierres', 'todo'],
  )

  const [confirmando, setConfirmando] = useState(false)
  const [cerrando, setCerrando] = useState(false)

  const diferencia = resumen.ventaTotal - resumen.ventaDiaAnterior
  const variacion =
    resumen.ventaDiaAnterior > 0 ? Math.round((diferencia / resumen.ventaDiaAnterior) * 100) : null

  const sumaMetodos =
    resumen.totalEfectivo + resumen.totalTarjeta + resumen.totalTransferencia
  const cuadra = sumaMetodos === resumen.ventaTotal

  // La caja tiene que cuadrar por las dos lecturas: por medio de pago, que es
  // contra lo que se cuenta el efectivo del cajon, y por canal, que es lo que
  // responde de donde vino la venta. Si una de las dos no da, hay algo mal.
  const sumaCanales = resumen.totalSalon + resumen.totalDomicilio + resumen.totalLlevar
  const cuadranCanales = sumaCanales === resumen.ventaTotal

  const canales = [
    { icono: Utensils, etiqueta: 'Salón', valor: resumen.totalSalon },
    { icono: Bike, etiqueta: 'Domicilio', valor: resumen.totalDomicilio },
    { icono: ShoppingBag, etiqueta: 'Para llevar', valor: resumen.totalLlevar },
  ]

  const imprimirCierre = () =>
    imprimir(<CierreTermico resumen={resumen} cerradoPor={sesion.nombre} />)

  const cerrar = async () => {
    setCerrando(true)
    try {
      await api.cerrarTurno(sesion.nombre)
      setConfirmando(false)
      mostrar('Turno cerrado', 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo cerrar el turno', 'error')
    } finally {
      setCerrando(false)
    }
  }

  const medios = [
    { icono: Banknote, etiqueta: 'Efectivo', valor: resumen.totalEfectivo },
    { icono: CreditCard, etiqueta: 'Tarjeta', valor: resumen.totalTarjeta },
    { icono: Smartphone, etiqueta: 'Transferencia', valor: resumen.totalTransferencia },
  ]

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-3">
        {/* ---------- Resumen del turno ---------- */}
        <section className="rounded-2xl border border-ambar-500/30 bg-noche-900 p-4 lg:col-span-2">
          <div className="mb-3 flex items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-crema-100">
                Turno de {resumen.turno === 'cena' ? 'cena' : 'almuerzo'}
              </h2>
              <p className="text-xs text-noche-400">
                {resumen.fecha ? formatoFecha(resumen.fecha) : ''}
              </p>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold tabular-nums text-ambar-300">
                {formatoCOP(resumen.ventaTotal)}
              </p>
              {variacion !== null && (
                <p
                  className={`flex items-center justify-end gap-1 text-xs font-medium ${
                    diferencia >= 0 ? 'text-estado-listo' : 'text-estado-demorado'
                  }`}
                >
                  {diferencia >= 0 ? (
                    <TrendingUp className="h-3 w-3" aria-hidden />
                  ) : (
                    <TrendingDown className="h-3 w-3" aria-hidden />
                  )}
                  {diferencia >= 0 ? '+' : ''}
                  {variacion}% frente a ayer
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            {medios.map(({ icono: Icono, etiqueta, valor }) => (
              <div key={etiqueta} className="rounded-xl border border-noche-800 bg-noche-850 p-3">
                <Icono className="mb-1.5 h-4 w-4 text-noche-500" aria-hidden />
                <p className="text-base font-semibold tabular-nums text-crema-100">
                  {formatoCOP(valor)}
                </p>
                <p className="text-xs text-noche-400">{etiqueta}</p>
              </div>
            ))}
          </div>

          {/* ---------- Por canal ---------- */}
          <div className="mt-3 border-t border-noche-800 pt-3">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-noche-500">
              De dónde vino la venta
            </p>
            <div className="grid grid-cols-3 gap-2">
              {canales.map(({ icono: Icono, etiqueta, valor }) => (
                <div key={etiqueta} className="rounded-xl border border-noche-800 bg-noche-850 p-3">
                  <Icono className="mb-1.5 h-4 w-4 text-noche-500" aria-hidden />
                  <p className="text-base font-semibold tabular-nums text-crema-100">
                    {formatoCOP(valor)}
                  </p>
                  <p className="text-xs text-noche-400">{etiqueta}</p>
                </div>
              ))}
            </div>
            {resumen.totalEnvios > 0 && (
              <p className="mt-2 text-xs text-noche-500">
                De lo del domicilio, {formatoCOP(resumen.totalEnvios)} son envíos: no es venta de
                cocina, es un costo que se le traslada al cliente.
              </p>
            )}
            {!cuadranCanales && resumen.ventaTotal > 0 && (
              <p className="mt-2 text-xs font-medium text-estado-demorado">
                Los canales suman {formatoCOP(sumaCanales)} y la venta es{' '}
                {formatoCOP(resumen.ventaTotal)}. Avise al administrador antes de cerrar.
              </p>
            )}
          </div>

          <dl className="mt-3 space-y-1.5 border-t border-noche-800 pt-3 text-sm">
            <div className="flex justify-between text-noche-300">
              <dt>Cuentas cobradas</dt>
              <dd className="tabular-nums">{resumen.ordenesAtendidas}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>Ticket promedio</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.ticketPromedio)}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>Propinas del turno</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.propinasTotales)}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>INC recaudado</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.incTotal)}</dd>
            </div>
            <div className="flex justify-between border-t border-noche-800 pt-1.5 text-base font-semibold text-crema-100">
              <dt>Total del turno</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.ventaTotal)}</dd>
            </div>
          </dl>

          {!cuadra && (
            <p className="mt-3 rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
              Los medios de pago suman {formatoCOP(sumaMetodos)} y el turno {formatoCOP(resumen.ventaTotal)}.
              Revisa antes de cerrar.
            </p>
          )}

          <div className="mt-4 space-y-2">
            <Boton
              variante="secundario"
              tamano="grande"
              bloque
              onClick={imprimirCierre}
              icono={<Printer className="h-5 w-5" />}
            >
              Imprimir el corte
            </Boton>
            <Boton
              variante="principal"
              tamano="grande"
              bloque
              icono={<Lock className="h-5 w-5" />}
              disabled={resumen.ordenesAtendidas === 0}
              onClick={() => setConfirmando(true)}
            >
              Cerrar turno
            </Boton>
            {resumen.ordenesAtendidas === 0 && (
              <p className="mt-2 text-center text-xs text-noche-500">
                No hay cuentas cobradas en este turno todavía.
              </p>
            )}
          </div>
        </section>

        {/* ---------- Cierres anteriores ---------- */}
        <section className="rounded-2xl border border-noche-800 bg-noche-900 p-3">
          <h2 className="mb-2.5 text-sm font-semibold text-crema-100">Cierres anteriores</h2>
          {cierres.length === 0 ? (
            <Vacio icono={Lock} titulo="Sin cierres registrados" />
          ) : (
            <ul className="space-y-1.5">
              {cierres.slice(0, 12).map((cierre) => (
                <li
                  key={cierre.id}
                  className="rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-crema-100">
                        {formatoFecha(cierre.fecha)}
                        <span className="ml-1.5 text-xs font-normal text-noche-400">
                          {cierre.turno}
                        </span>
                      </p>
                      <p className="truncate text-xs text-noche-500">
                        {cierre.ordenesAtendidas} cuentas · {cierre.cerradoPor}
                      </p>
                    </div>
                    <span className="shrink-0 text-sm font-semibold tabular-nums text-crema-100">
                      {formatoCOP(cierre.ventaTotal)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <HojaInferior
        abierta={confirmando}
        titulo="Cerrar el turno"
        descripcion={`${resumen.ordenesAtendidas} cuentas · ${formatoCOP(resumen.ventaTotal)}`}
        onCerrar={() => setConfirmando(false)}
        pie={
          <Boton variante="principal" tamano="grande" bloque cargando={cerrando} onClick={cerrar}>
            Confirmar cierre
          </Boton>
        }
      >
        <div className="space-y-3 text-sm">
          <p className="text-noche-300">
            Se guarda el corte con lo cobrado hasta ahora. Las mesas que sigan abiertas no se ven
            afectadas y entran al siguiente corte.
          </p>
          <dl className="space-y-1.5 rounded-xl border border-noche-700 bg-noche-850 p-3">
            <div className="flex justify-between text-noche-300">
              <dt>Efectivo en caja</dt>
              <dd className="tabular-nums font-semibold text-crema-100">
                {formatoCOP(resumen.totalEfectivo)}
              </dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>Tarjeta</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.totalTarjeta)}</dd>
            </div>
            <div className="flex justify-between text-noche-300">
              <dt>Transferencia</dt>
              <dd className="tabular-nums">{formatoCOP(resumen.totalTransferencia)}</dd>
            </div>
            <div className="flex justify-between border-t border-noche-700 pt-1.5 text-noche-300">
              <dt>Propinas a repartir</dt>
              <dd className="tabular-nums font-semibold text-ambar-300">
                {formatoCOP(resumen.propinasTotales)}
              </dd>
            </div>
          </dl>
          <p className="text-xs text-noche-500">
            Quedará registrado a nombre de {sesion.nombre}, {formatoFechaHora(new Date())}.
          </p>
        </div>
      </HojaInferior>
    </div>
  )
}

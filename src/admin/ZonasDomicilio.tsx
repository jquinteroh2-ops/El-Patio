import { useState } from 'react'
import { Bike, Plus, Trash2 } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { MINUTOS_ESTIMADOS_POR_DEFECTO, MONTO_MINIMO_DOMICILIO } from '@/compartido/config'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Ajustes, ZonaDomicilio } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Interruptor } from '@/componentes/ui/Interruptor'
import { useAvisos } from '@/componentes/ui/Avisos'

/**
 * Administración del canal de domicilios.
 *
 * Las zonas viven en la base y no en config.ts porque el dueño sube la tarifa
 * de un barrio cuando sube la gasolina, y eso no puede necesitar un despliegue.
 * El interruptor de pausa es lo primero que se busca un viernes a las nueve de
 * la noche, así que va arriba y grande.
 */

const ZONA_NUEVA = (): ZonaDomicilio => ({
  id: '',
  nombre: '',
  tarifa: 5000,
  montoMinimo: MONTO_MINIMO_DOMICILIO,
  minutosEstimados: MINUTOS_ESTIMADOS_POR_DEFECTO,
  activa: true,
  orden: 99,
})

/** El backend entrega hh:mm:ss y el campo de hora del navegador espera hh:mm. */
const aHoraCorta = (valor: string): string => valor.slice(0, 5)

export function ZonasDomicilio({ ajustes }: { ajustes: Ajustes }) {
  const { mostrar } = useAvisos()

  const { datos: zonas } = useSyncedState<ZonaDomicilio[]>(
    () => api.listarZonasDomicilio(),
    [],
    [],
    ['zonas', 'ajustes', 'todo'],
  )

  const [editando, setEditando] = useState<ZonaDomicilio | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [desde, setDesde] = useState<string | null>(null)
  const [hasta, setHasta] = useState<string | null>(null)

  const horaDesde = desde ?? aHoraCorta(ajustes.domiciliosDesde)
  const horaHasta = hasta ?? aHoraCorta(ajustes.domiciliosHasta)
  const horarioCambio =
    horaDesde !== aHoraCorta(ajustes.domiciliosDesde) ||
    horaHasta !== aHoraCorta(ajustes.domiciliosHasta)

  const conAviso = async (accion: () => Promise<unknown>, exito: string) => {
    setGuardando(true)
    try {
      await accion()
      mostrar(exito, 'exito')
      return true
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar', 'error')
      return false
    } finally {
      setGuardando(false)
    }
  }

  // Va por `actualizarCanalPedidos` y no por `actualizarAjustes`: ese otro
  // endpoint tambien mueve el impuesto al consumo y solo lo abre administracion.
  // Pausar el canal lo decide quien esta mirando la cocina, que suele ser
  // recepcion, y por eso esta pantalla se monta tambien en el mostrador.
  const alternarPausa = (pausado: boolean) =>
    void conAviso(
      () => api.actualizarCanalPedidos({ pausados: pausado }),
      pausado
        ? 'Canal pausado. El sitio público deja de recibir pedidos.'
        : 'Canal abierto. Ya se pueden recibir pedidos.',
    )

  const guardarHorario = () =>
    void conAviso(
      () =>
        api.actualizarCanalPedidos({
          desde: `${horaDesde}:00`,
          hasta: `${horaHasta}:00`,
        }),
      'Horario de domicilios actualizado',
    )

  const guardarZona = async () => {
    if (!editando) return
    const ok = await conAviso(
      () => api.guardarZonaDomicilio(editando),
      `Zona «${editando.nombre}» guardada`,
    )
    if (ok) setEditando(null)
  }

  const eliminarZona = (zona: ZonaDomicilio) =>
    void conAviso(() => api.eliminarZonaDomicilio(zona.id), `Zona «${zona.nombre}» eliminada`)

  const campo =
    'min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 focus:border-oro-500 focus:outline-none'
  const etiqueta = 'mb-1.5 block text-xs uppercase tracking-wide text-noche-400'

  return (
    <section className="revelar-corto rounded-2xl border border-noche-800 bg-noche-900 p-4 lg:col-span-2">
      <h2 className="mb-1 flex items-center gap-2 text-sm font-semibold text-crema-100">
        <Bike className="h-4 w-4" aria-hidden />
        Domicilios y para llevar
      </h2>
      <p className="mb-4 text-xs leading-relaxed text-noche-400">
        El envío no causa impuesto al consumo ni entra en la propina: es una línea aparte, después
        del impuesto.
      </p>

      {/* ---------- Pausa del canal ---------- */}
      <div className="mb-4 rounded-xl border border-oro-500/30 bg-noche-850 p-3">
        <Interruptor
          activo={ajustes.domiciliosPausados}
          onCambiar={alternarPausa}
          etiqueta="Pausar el canal de pedidos"
          descripcion="Cuando la cocina está saturada. El sitio público deja de aceptar pedidos al instante, sin recargar."
          deshabilitado={guardando}
        />
      </div>

      {/* ---------- Horario ---------- */}
      <div className="mb-4 flex flex-wrap items-end gap-2">
        <label className="block">
          <span className={etiqueta}>Recibimos desde</span>
          <input
            type="time"
            value={horaDesde}
            onChange={(e) => setDesde(e.target.value)}
            className={campo}
          />
        </label>
        <label className="block">
          <span className={etiqueta}>Hasta</span>
          <input
            type="time"
            value={horaHasta}
            onChange={(e) => setHasta(e.target.value)}
            className={campo}
          />
        </label>
        <Boton
          variante="principal"
          cargando={guardando}
          disabled={!horarioCambio}
          onClick={guardarHorario}
        >
          Guardar horario
        </Boton>
      </div>

      {/* ---------- Zonas ---------- */}
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-noche-400">
          Zonas de domicilio
        </h3>
        <Boton
          variante="secundario"
          tamano="compacto"
          onClick={() => setEditando(ZONA_NUEVA())}
          icono={<Plus className="h-4 w-4" aria-hidden />}
        >
          Agregar zona
        </Boton>
      </div>

      <ul className="space-y-2">
        {zonas.length === 0 && (
          <li className="rounded-xl border border-noche-800 bg-noche-850 p-3 text-xs text-noche-400">
            Todavía no hay zonas. Sin zonas activas, el sitio público no deja pedir a domicilio.
          </li>
        )}
        {zonas.map((zona) => (
          <li
            key={zona.id}
            className={`flex items-center justify-between gap-3 rounded-xl border p-3 ${
              zona.activa ? 'border-noche-800 bg-noche-850' : 'border-noche-800 bg-noche-900 opacity-60'
            }`}
          >
            <button
              type="button"
              onClick={() => setEditando({ ...zona })}
              className="min-w-0 flex-1 text-left"
            >
              <span className="block truncate text-sm font-medium text-crema-100">
                {zona.nombre}
                {!zona.activa && <span className="ml-2 text-xs text-noche-500">(inactiva)</span>}
              </span>
              <span className="block text-xs text-noche-400">
                Envío {formatoCOP(zona.tarifa)} · mínimo {formatoCOP(zona.montoMinimo)} ·{' '}
                {zona.minutosEstimados} min
              </span>
            </button>
            <button
              type="button"
              onClick={() => eliminarZona(zona)}
              disabled={guardando}
              className="shrink-0 rounded-lg p-2 text-noche-500 transition hover:text-estado-demorado"
              aria-label={`Eliminar ${zona.nombre}`}
            >
              <Trash2 className="h-4 w-4" aria-hidden />
            </button>
          </li>
        ))}
      </ul>

      {/* ---------- Edición ---------- */}
      <HojaInferior
        abierta={editando !== null}
        titulo={editando?.id ? `Zona ${editando.nombre}` : 'Nueva zona'}
        descripcion="La tarifa es lo que se le cobra al cliente por llevarle el pedido."
        onEnviar={guardarZona}
        onCerrar={() => setEditando(null)}
        pie={
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={guardando}
            disabled={!editando?.nombre.trim()}
            type="submit"
          >
            Guardar zona
          </Boton>
        }
      >
        {editando && (
          <div className="space-y-3">
            <label className="block">
              <span className={etiqueta}>Barrio</span>
              <input
                className={campo}
                value={editando.nombre}
                onChange={(e) => setEditando({ ...editando, nombre: e.target.value })}
                placeholder="Centro, El Cerrito…"
              />
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className={etiqueta}>Tarifa del envío</span>
                <input
                  className={campo}
                  inputMode="numeric"
                  value={String(editando.tarifa)}
                  onChange={(e) =>
                    setEditando({ ...editando, tarifa: Number(e.target.value.replace(/\D/g, '')) })
                  }
                />
              </label>
              <label className="block">
                <span className={etiqueta}>Pedido mínimo</span>
                <input
                  className={campo}
                  inputMode="numeric"
                  value={String(editando.montoMinimo)}
                  onChange={(e) =>
                    setEditando({
                      ...editando,
                      montoMinimo: Number(e.target.value.replace(/\D/g, '')),
                    })
                  }
                />
              </label>
            </div>

            <label className="block">
              <span className={etiqueta}>Tiempo estimado (minutos)</span>
              <input
                className={campo}
                inputMode="numeric"
                value={String(editando.minutosEstimados)}
                onChange={(e) =>
                  setEditando({
                    ...editando,
                    minutosEstimados: Number(e.target.value.replace(/\D/g, '')) || 1,
                  })
                }
              />
              <span className="mt-1.5 block text-xs text-noche-500">
                Es lo que se le propone a recepción al aceptar. Quien acepta puede ajustarlo.
              </span>
            </label>

            <Interruptor
              activo={editando.activa}
              onCambiar={(activa) => setEditando({ ...editando, activa })}
              etiqueta="Zona activa"
              descripcion="Una zona inactiva no aparece en el sitio público."
            />
          </div>
        )}
      </HojaInferior>
    </section>
  )
}

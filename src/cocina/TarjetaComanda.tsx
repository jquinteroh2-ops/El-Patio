import { useState } from 'react'
import { Check, ChefHat, Clock, Minus, Pencil, Plus, StickyNote, UserRound } from 'lucide-react'
import type { TurnoEnCocina } from '@/compartido/mockApi'
import { formatoHora, minutosDesde } from '@/compartido/formato'
import { colorPorAvance } from '@/compartido/estados'
import type { EstadoItem } from '@/compartido/tipos'

interface Props {
  bloque: TurnoEnCocina
  /** Minutos que deberia tardar el turno: el plato mas lento. */
  estimado: number
  /** Tiempo de preparacion de cada plato de la carta, por id. */
  tiempos: Map<string, number>
  /** Recien llegada: se resalta unos segundos para que nadie la pase por alto. */
  reciente: boolean
  ocupado: boolean
  onCambiarItem: (itemId: string, estado: EstadoItem) => void
  onCambiarTurno: (estado: EstadoItem) => void
  /** El cocinero corrige el tiempo de un plato de la carta. */
  onCambiarTiempo: (itemCartaId: string, minutos: number) => Promise<void>
}

const ACCION: Record<TurnoEnCocina['estado'], { etiqueta: string; siguiente: EstadoItem; clase: string }> = {
  pendiente: {
    etiqueta: 'Empezar',
    siguiente: 'en_preparacion',
    clase: 'bg-estado-proceso text-noche-950 hover:brightness-110',
  },
  en_preparacion: {
    etiqueta: 'Marcar listo',
    siguiente: 'listo',
    clase: 'bg-estado-listo text-noche-950 hover:brightness-110',
  },
  listo: {
    etiqueta: 'Entregado a mesa',
    siguiente: 'servido',
    clase: 'bg-noche-700 text-crema-100 hover:bg-noche-600',
  },
  servido: {
    etiqueta: 'Servido',
    siguiente: 'servido',
    clase: 'bg-noche-800 text-noche-400',
  },
}

/** Sin tiempo en la carta, el plato corre contra el umbral general. */
const TIEMPO_SIN_DEFINIR = 20

/**
 * Una comanda de un turno. Pensada para leerse desde un metro de distancia:
 * mesa grande, cronometro con fondo solido y un solo boton que ocupa el ancho.
 */
export function TarjetaComanda({
  bloque,
  estimado,
  tiempos,
  reciente,
  ocupado,
  onCambiarItem,
  onCambiarTurno,
  onCambiarTiempo,
}: Props) {
  const espera = minutosDesde(bloque.enviadoEn)
  const accion = ACCION[bloque.estado]
  const restante = estimado - espera
  // Lo que ya salio de cocina no corre contra el reloj.
  const corriendo = bloque.estado === 'pendiente' || bloque.estado === 'en_preparacion'
  const tarde = corriendo && restante < 0
  const color = colorPorAvance(espera, estimado)
  const avance = Math.min(100, Math.round((espera / Math.max(estimado, 1)) * 100))

  /** El plato cuyo tiempo se esta corrigiendo, con el valor en edicion. */
  const [editando, setEditando] = useState<{ itemCartaId: string; minutos: number } | null>(null)
  const [guardando, setGuardando] = useState(false)

  const guardarTiempo = async () => {
    if (!editando) return
    setGuardando(true)
    try {
      await onCambiarTiempo(editando.itemCartaId, editando.minutos)
      setEditando(null)
    } catch {
      // El aviso del error ya lo dio la pantalla; el editor queda abierto
      // para volver a intentar.
    } finally {
      setGuardando(false)
    }
  }

  return (
    <article
      className={`overflow-hidden rounded-2xl border-2 bg-noche-900 transition-colors duration-500 ${
        reciente ? 'animate-destello border-oro-400' : tarde ? 'border-estado-demorado' : 'border-noche-700'
      }`}
    >
      {/* La barra se llena con el tiempo y va pasando de verde a rojo: se lee
          de lejos sin tener que mirar los números. */}
      {corriendo && (
        <div className="h-1.5 w-full bg-noche-800" aria-hidden>
          <div
            className={`h-full transition-[width,background-color] duration-1000 ${tarde ? 'animate-latido' : ''}`}
            style={{ width: `${avance}%`, backgroundColor: color }}
          />
        </div>
      )}

      <header className="flex items-center justify-between gap-2 border-b border-noche-800 px-3 py-2.5">
        <div className="min-w-0">
          <p className="truncate text-xl font-bold leading-tight text-crema-100">
            {bloque.mesaEtiqueta}
          </p>
          <p className="text-xs text-noche-400">
            Turno {bloque.turno} · #{bloque.numeroOrden} · {formatoHora(bloque.enviadoEn)}
          </p>
          {/* Quién tomó el pedido: a quién llamar cuando sale el plato o
              cuando hay una duda con la comanda. */}
          {bloque.meseroNombre && (
            <p className="mt-0.5 flex items-center gap-1 truncate text-sm font-medium text-oro-300">
              <UserRound className="h-3.5 w-3.5 shrink-0" aria-hidden />
              {bloque.meseroNombre}
            </p>
          )}
        </div>
        <div className="shrink-0 text-right">
          <span
            className={`inline-block rounded-xl border px-2.5 py-1.5 text-xl font-bold tabular-nums ${
              corriendo ? 'text-noche-950' : 'border-noche-700 bg-noche-800 text-noche-300'
            }`}
            style={corriendo ? { backgroundColor: color, borderColor: color } : undefined}
          >
            {espera}
            <span className="text-sm font-semibold opacity-75">/{estimado}</span>
            <span className="ml-0.5 text-xs font-semibold">min</span>
          </span>
          {corriendo && (
            <p
              className={`mt-0.5 text-xs font-semibold ${
                restante < 0 ? 'text-estado-demorado' : 'text-noche-400'
              }`}
            >
              {restante < 0
                ? `${-restante} min tarde`
                : restante === 0
                  ? 'Ya debe salir'
                  : `Faltan ${restante} min`}
            </p>
          )}
        </div>
      </header>

      {bloque.notas && (
        <p className="flex items-start gap-1.5 border-b border-noche-800 bg-oro-500/10 px-3 py-2 text-sm text-oro-200">
          <StickyNote className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
          {bloque.notas}
        </p>
      )}

      <ul className="divide-y divide-noche-800">
        {bloque.items.map((item) => {
          const listo = item.estado === 'listo'
          const tiempo = tiempos.get(item.itemCartaId)
          const enEdicion = editando?.itemCartaId === item.itemCartaId
          return (
            <li key={item.id}>
              <div className="flex items-stretch">
                <button
                  type="button"
                  disabled={ocupado}
                  onClick={() => onCambiarItem(item.id, listo ? 'en_preparacion' : 'listo')}
                  className={`flex min-w-0 flex-1 items-start gap-2.5 px-3 py-2.5 text-left transition active:scale-[0.99] ${
                    listo ? 'bg-estado-listo-suave' : 'hover:bg-noche-850'
                  }`}
                >
                  <span
                    className={`mt-0.5 flex h-7 min-w-[28px] items-center justify-center rounded-lg px-1 text-base font-bold ${
                      listo ? 'bg-estado-listo text-noche-950' : 'bg-noche-700 text-crema-100'
                    }`}
                  >
                    {item.cantidad}
                  </span>

                  <span className="min-w-0 flex-1">
                    <span
                      className={`block text-base font-semibold leading-snug ${
                        listo ? 'text-estado-listo line-through' : 'text-crema-100'
                      }`}
                    >
                      {item.nombre}
                    </span>
                    {item.modificadoresSeleccionados.length > 0 && (
                      <span className="mt-0.5 block text-sm font-medium text-oro-300">
                        {item.modificadoresSeleccionados.map((m) => m.valor).join(' · ')}
                      </span>
                    )}
                    {item.notaCocina && (
                      <span className="mt-0.5 block text-sm italic text-estado-proceso">
                        {item.notaCocina}
                      </span>
                    )}
                  </span>

                  {listo && <Check className="mt-1 h-5 w-5 shrink-0 text-estado-listo" aria-hidden />}
                </button>

                {/* El tiempo del plato, aparte de la fila: tocarlo lo corrige
                    sin marcar el plato como listo. Va como botón dorado con
                    reloj para que el cocinero lo encuentre sin que se lo
                    señalen. */}
                {!listo && (
                  <div className="flex shrink-0 items-center pr-2">
                    <button
                      type="button"
                      onClick={() =>
                        setEditando(
                          enEdicion
                            ? null
                            : { itemCartaId: item.itemCartaId, minutos: tiempo || TIEMPO_SIN_DEFINIR },
                        )
                      }
                      title="Cambiar el tiempo de preparación de este plato"
                      className={`flex min-h-[40px] items-center gap-1.5 rounded-lg border px-2.5 text-sm font-semibold tabular-nums transition active:scale-95 ${
                        enEdicion
                          ? 'border-oro-400 bg-oro-500 text-noche-950'
                          : 'border-oro-500/60 bg-oro-500/10 text-oro-200 hover:bg-oro-500/20'
                      }`}
                    >
                      <Clock className="h-4 w-4" aria-hidden />
                      {tiempo ? `${tiempo} min` : 'Poner tiempo'}
                      <Pencil className="h-3.5 w-3.5 opacity-80" aria-hidden />
                    </button>
                  </div>
                )}
              </div>

              {enEdicion && editando && (
                <div className="border-t-2 border-oro-500/60 bg-noche-850 px-3 py-3">
                  <p className="flex items-center gap-1.5 text-sm font-semibold text-oro-200">
                    <Clock className="h-4 w-4" aria-hidden />
                    ¿Cuánto tarda «{item.nombre}»?
                  </p>
                  <p className="mt-0.5 text-xs text-noche-400">
                    El cambio queda para todas las comandas que lleven este plato.
                  </p>

                  <div className="mt-2.5 flex items-center justify-center gap-1.5">
                    {[-5, -1].map((paso) => (
                      <button
                        key={paso}
                        type="button"
                        aria-label={`${-paso} ${paso === -1 ? 'minuto' : 'minutos'} menos`}
                        onClick={() =>
                          setEditando({ ...editando, minutos: Math.max(1, editando.minutos + paso) })
                        }
                        className="flex h-12 min-w-[48px] items-center justify-center rounded-lg bg-noche-700 px-2 text-base font-bold text-crema-100 active:scale-95"
                      >
                        {paso === -1 ? <Minus className="h-5 w-5" aria-hidden /> : '−5'}
                      </button>
                    ))}
                    <span className="min-w-[5.5rem] text-center text-2xl font-bold tabular-nums text-crema-100">
                      {editando.minutos}
                      <span className="ml-1 text-sm font-semibold text-noche-300">min</span>
                    </span>
                    {[1, 5].map((paso) => (
                      <button
                        key={paso}
                        type="button"
                        aria-label={`${paso} ${paso === 1 ? 'minuto' : 'minutos'} más`}
                        onClick={() =>
                          setEditando({ ...editando, minutos: Math.min(240, editando.minutos + paso) })
                        }
                        className="flex h-12 min-w-[48px] items-center justify-center rounded-lg bg-noche-700 px-2 text-base font-bold text-crema-100 active:scale-95"
                      >
                        {paso === 1 ? <Plus className="h-5 w-5" aria-hidden /> : '+5'}
                      </button>
                    ))}
                  </div>

                  <div className="mt-2.5 grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => setEditando(null)}
                      className="h-11 rounded-lg border border-noche-600 text-sm font-semibold text-crema-100 transition hover:bg-noche-800"
                    >
                      Cancelar
                    </button>
                    <button
                      type="button"
                      disabled={guardando}
                      onClick={guardarTiempo}
                      className="h-11 rounded-lg bg-oro-500 text-sm font-bold text-noche-950 transition hover:bg-oro-400 disabled:opacity-50"
                    >
                      {guardando ? 'Guardando…' : 'Guardar'}
                    </button>
                  </div>
                </div>
              )}
            </li>
          )
        })}
      </ul>

      <footer className="p-2">
        <button
          type="button"
          disabled={ocupado}
          onClick={() => onCambiarTurno(accion.siguiente)}
          className={`flex min-h-[52px] w-full items-center justify-center gap-2 rounded-xl text-base font-bold transition active:scale-[0.98] disabled:opacity-50 ${accion.clase}`}
        >
          <ChefHat className="h-5 w-5" aria-hidden />
          {accion.etiqueta}
        </button>
      </footer>
    </article>
  )
}

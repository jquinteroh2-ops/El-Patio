import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Bell, BellOff, ChefHat, GlassWater, Maximize2, Minimize2, Soup } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { TurnoEnCocina } from '@/compartido/mockApi'
import { UMBRALES_COCINA } from '@/compartido/config'
import { minutosDesde } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import type { Destino, EstadoItem } from '@/compartido/tipos'
import { BarraOperativa } from '@/componentes/BarraOperativa'
import { AvisoDemo } from '@/componentes/AvisoDemo'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { TarjetaComanda } from './TarjetaComanda'
import { habilitarSonido, useAvisoNuevaComanda } from './avisoNuevaComanda'

type EstadoColumna = 'pendiente' | 'en_preparacion' | 'listo'

const COLUMNAS: { estado: EstadoColumna; titulo: string; acento: string }[] = [
  { estado: 'pendiente', titulo: 'Pendientes', acento: 'text-crema-100' },
  { estado: 'en_preparacion', titulo: 'En preparación', acento: 'text-estado-proceso' },
  { estado: 'listo', titulo: 'Listos', acento: 'text-estado-listo' },
]

/** Los turnos de una misma mesa se muestran juntos, pero cada uno en su tarjeta. */
function agruparPorMesa(bloques: TurnoEnCocina[]): TurnoEnCocina[][] {
  const mapa = new Map<string, TurnoEnCocina[]>()
  for (const bloque of bloques) {
    const lista = mapa.get(bloque.mesaId) ?? []
    lista.push(bloque)
    mapa.set(bloque.mesaId, lista)
  }
  return [...mapa.values()]
    .map((grupo) => grupo.sort((a, b) => a.turno - b.turno))
    .sort(
      (a, b) => new Date(a[0].enviadoEn).getTime() - new Date(b[0].enviadoEn).getTime(),
    )
}

export default function PantallaCocina({ destino = 'cocina' }: { destino?: Destino }) {
  const esBar = destino === 'bar'
  const navegar = useNavigate()
  const { mostrar } = useAvisos()

  const { datos: bloques } = useSyncedState<TurnoEnCocina[]>(
    () => api.comandasActivas(destino),
    [],
    [destino],
    ['ordenes', 'cocina', 'mesas', 'todo'],
  )

  // Los cronometros mandan en esta pantalla: se refrescan solos cada 15 s.
  useReloj(15000)

  const [sonido, setSonido] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [pantallaCompleta, setPantallaCompleta] = useState(false)

  const claves = useMemo(() => bloques.map((b) => `${b.ordenId}:${b.turno}`), [bloques])
  const recientes = useAvisoNuevaComanda(claves, sonido)

  const demoradas = bloques.filter(
    (b) => b.estado !== 'listo' && minutosDesde(b.enviadoEn) >= UMBRALES_COCINA.demorado,
  )

  const alternarSonido = async () => {
    if (sonido) {
      setSonido(false)
      return
    }
    const listo = await habilitarSonido()
    setSonido(listo)
    if (!listo) mostrar('Este navegador no permitió activar el sonido', 'error')
  }

  const alternarPantallaCompleta = async () => {
    try {
      if (!document.fullscreenElement) {
        await document.documentElement.requestFullscreen()
        setPantallaCompleta(true)
      } else {
        await document.exitFullscreen()
        setPantallaCompleta(false)
      }
    } catch {
      mostrar('El navegador no permitió pantalla completa', 'error')
    }
  }

  const ejecutar = useCallback(
    async (accion: () => Promise<void>) => {
      setOcupado(true)
      try {
        await accion()
      } catch (e) {
        mostrar(e instanceof Error ? e.message : 'No se pudo actualizar la comanda', 'error')
      } finally {
        setOcupado(false)
      }
    },
    [mostrar],
  )

  const cambiarItem = (bloque: TurnoEnCocina) => (itemId: string, estado: EstadoItem) =>
    ejecutar(() => api.cambiarEstadoItem(bloque.ordenId, itemId, estado))

  const cambiarTurno = (bloque: TurnoEnCocina) => (estado: EstadoItem) =>
    ejecutar(() => api.cambiarEstadoTurno(bloque.ordenId, bloque.turno, destino, estado))

  return (
    <div className="flex min-h-screen flex-col bg-noche-950">
      <BarraOperativa
        titulo={esBar ? 'Barra' : 'Cocina'}
        subtitulo={`${bloques.length} ${bloques.length === 1 ? 'comanda activa' : 'comandas activas'}`}
        acciones={
          <div className="flex items-center gap-1.5">
            <div className="flex rounded-xl border border-noche-700 bg-noche-850 p-1">
              <button
                type="button"
                onClick={() => navegar('/cocina')}
                className={`flex h-10 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition ${
                  !esBar ? 'bg-ambar-500 text-noche-950' : 'text-noche-300 hover:bg-noche-800'
                }`}
              >
                <Soup className="h-4 w-4" aria-hidden />
                Cocina
              </button>
              <button
                type="button"
                onClick={() => navegar('/cocina/bar')}
                className={`flex h-10 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition ${
                  esBar ? 'bg-ambar-500 text-noche-950' : 'text-noche-300 hover:bg-noche-800'
                }`}
              >
                <GlassWater className="h-4 w-4" aria-hidden />
                Barra
              </button>
            </div>

            <button
              type="button"
              onClick={alternarSonido}
              title={sonido ? 'Silenciar avisos' : 'Activar aviso sonoro'}
              aria-label={sonido ? 'Silenciar avisos' : 'Activar aviso sonoro'}
              className={`flex h-toque w-11 items-center justify-center rounded-xl transition ${
                sonido
                  ? 'bg-ambar-500/15 text-ambar-300'
                  : 'text-noche-400 hover:bg-noche-800 hover:text-crema-100'
              }`}
            >
              {sonido ? <Bell className="h-5 w-5" aria-hidden /> : <BellOff className="h-5 w-5" aria-hidden />}
            </button>

            <button
              type="button"
              onClick={alternarPantallaCompleta}
              title="Pantalla completa"
              aria-label="Pantalla completa"
              className="hidden h-toque w-11 items-center justify-center rounded-xl text-noche-400 transition hover:bg-noche-800 hover:text-crema-100 sm:flex"
            >
              {pantallaCompleta ? (
                <Minimize2 className="h-5 w-5" aria-hidden />
              ) : (
                <Maximize2 className="h-5 w-5" aria-hidden />
              )}
            </button>
          </div>
        }
      />

      {demoradas.length > 0 && (
        <div className="flex items-center gap-2 border-b border-estado-demorado/40 bg-estado-demorado/15 px-4 py-2.5 text-sm font-semibold text-estado-demorado">
          <AlertTriangle className="h-4 w-4 shrink-0 animate-latido" aria-hidden />
          {demoradas.length === 1
            ? `${demoradas[0].mesaEtiqueta} pasa de ${UMBRALES_COCINA.demorado} minutos`
            : `${demoradas.length} comandas pasan de ${UMBRALES_COCINA.demorado} minutos`}
        </div>
      )}

      {!sonido && (
        <button
          type="button"
          onClick={alternarSonido}
          className="border-b border-noche-800 bg-noche-900 px-4 py-2 text-left text-xs text-noche-400 transition hover:bg-noche-850"
        >
          El navegador exige un toque para permitir sonido.{' '}
          <span className="font-semibold text-ambar-300">Activar aviso sonoro</span>
        </button>
      )}

      <main className="grid flex-1 grid-cols-1 gap-px bg-noche-800 md:grid-cols-3">
        {COLUMNAS.map((columna) => {
          const deLaColumna = bloques.filter((b) => b.estado === columna.estado)
          const grupos = agruparPorMesa(deLaColumna)

          return (
            <section key={columna.estado} className="flex min-h-[40vh] flex-col bg-noche-950">
              <header className="sticky top-16 z-10 flex items-center justify-between gap-2 border-b border-noche-800 bg-noche-950/95 px-3 py-2.5 backdrop-blur">
                <h2 className={`text-sm font-bold uppercase tracking-wider ${columna.acento}`}>
                  {columna.titulo}
                </h2>
                <span className="rounded-lg bg-noche-800 px-2 py-0.5 text-sm font-bold tabular-nums text-crema-100">
                  {deLaColumna.length}
                </span>
              </header>

              <div className="scroll-fino flex-1 space-y-3 overflow-y-auto p-3">
                {grupos.length === 0 ? (
                  <Vacio
                    icono={columna.estado === 'listo' ? ChefHat : esBar ? GlassWater : Soup}
                    titulo="Nada por aquí"
                  />
                ) : (
                  grupos.map((grupo) => (
                    <div
                      key={`${columna.estado}-${grupo[0].mesaId}`}
                      className={
                        grupo.length > 1
                          ? 'space-y-2 rounded-2xl border-l-4 border-ambar-500/70 bg-noche-900/40 py-2 pl-2 pr-0.5'
                          : 'space-y-2'
                      }
                    >
                      {grupo.length > 1 && (
                        <p className="px-1 text-xs font-semibold uppercase tracking-wider text-ambar-300">
                          {grupo[0].mesaEtiqueta} · {grupo.length} turnos
                        </p>
                      )}
                      {grupo.map((bloque) => (
                        <TarjetaComanda
                          key={`${bloque.ordenId}-${bloque.turno}`}
                          bloque={bloque}
                          reciente={recientes.has(`${bloque.ordenId}:${bloque.turno}`)}
                          ocupado={ocupado}
                          onCambiarItem={cambiarItem(bloque)}
                          onCambiarTurno={cambiarTurno(bloque)}
                        />
                      ))}
                    </div>
                  ))
                )}
              </div>
            </section>
          )
        })}
      </main>

      <footer className="border-t border-noche-800 px-4 py-2.5 text-center">
        <AvisoDemo />
      </footer>
    </div>
  )
}

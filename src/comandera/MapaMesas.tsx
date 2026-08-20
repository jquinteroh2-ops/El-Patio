import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LayoutGrid, Users } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { useSesionActiva } from '@/compartido/auth'
import { useReintentoAutomatico } from '@/compartido/conexion'
import { formatoCOP } from '@/compartido/formato'
import { useReloj, useSyncedState } from '@/compartido/useSyncedState'
import type { Zona } from '@/compartido/tipos'
import { BarraOperativa } from '@/componentes/BarraOperativa'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { useAvisos } from '@/componentes/ui/Avisos'
import { TarjetaMesa } from './TarjetaMesa'
import { NOMBRE_ZONA } from '@/compartido/estados'

const ZONAS: Zona[] = ['salon', 'terraza', 'privado']

export default function MapaMesas() {
  const sesion = useSesionActiva()
  const navegar = useNavigate()
  const { mostrar } = useAvisos()

  const { datos: mesas, cargando } = useSyncedState<MesaEnMapa[]>(
    () => api.listarMesas(),
    [],
    [],
    ['mesas', 'ordenes', 'cocina', 'todo'],
  )

  // Mantiene vivos los cronometros de las mesas sin un intervalo por tarjeta.
  useReloj(20000)

  // Si la comandera quedo con envios en cola, salen solos al volver la senal.
  const procesar = useCallback(async () => {
    const enviadas = await api.procesarCola()
    if (enviadas > 0) mostrar(`Se enviaron ${enviadas} comandas que estaban en cola`, 'exito')
  }, [mostrar])
  useReintentoAutomatico(procesar)

  const [zonaActiva, setZonaActiva] = useState<Zona | 'todas'>('todas')
  const [mesaAAbrir, setMesaAAbrir] = useState<MesaEnMapa | null>(null)
  const [comensales, setComensales] = useState(2)
  const [abriendo, setAbriendo] = useState(false)

  const resumen = useMemo(() => {
    const ocupadas = mesas.filter((m) => m.estado !== 'libre')
    return {
      ocupadas: ocupadas.length,
      total: mesas.length,
      listos: mesas.reduce((s, m) => s + m.itemsListos, 0),
      porCobrar: mesas.filter((m) => m.estado === 'cuenta_pedida').length,
      venta: ocupadas.reduce((s, m) => s + m.total, 0),
    }
  }, [mesas])

  const visibles = useMemo(
    () => (zonaActiva === 'todas' ? mesas : mesas.filter((m) => m.zona === zonaActiva)),
    [mesas, zonaActiva],
  )

  const tocarMesa = (mesa: MesaEnMapa) => {
    if (mesa.ordenActivaId) {
      navegar(`/comandera/mesa/${mesa.id}`)
      return
    }
    setComensales(Math.min(mesa.capacidad, 2))
    setMesaAAbrir(mesa)
  }

  const abrirMesa = async () => {
    if (!mesaAAbrir) return
    setAbriendo(true)
    try {
      await api.abrirMesa(mesaAAbrir.id, sesion.usuarioId, comensales)
      const destino = mesaAAbrir.id
      setMesaAAbrir(null)
      mostrar('Mesa abierta', 'exito')
      navegar(`/comandera/mesa/${destino}`)
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo abrir la mesa', 'error')
    } finally {
      setAbriendo(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-noche-950">
      <BarraOperativa titulo="Mesas" subtitulo={sesion.nombre} mostrarConexion />

      <div className="sticky top-16 z-20 border-b border-noche-800 bg-noche-950/95 backdrop-blur">
        <div className="flex gap-4 px-4 py-2.5 text-xs">
          <span className="text-noche-400">
            Ocupadas <strong className="text-crema-100">{resumen.ocupadas}/{resumen.total}</strong>
          </span>
          {resumen.listos > 0 && (
            <span className="font-medium text-estado-listo">{resumen.listos} por servir</span>
          )}
          {resumen.porCobrar > 0 && (
            <span className="font-medium text-estado-proceso">{resumen.porCobrar} por cobrar</span>
          )}
          <span className="ml-auto text-noche-400">
            En salón <strong className="text-crema-100">{formatoCOP(resumen.venta)}</strong>
          </span>
        </div>

        <div className="sin-scrollbar flex gap-2 overflow-x-auto px-4 pb-2.5">
          {(['todas', ...ZONAS] as const).map((zona) => (
            <button
              key={zona}
              type="button"
              onClick={() => setZonaActiva(zona)}
              className={`min-h-[38px] shrink-0 rounded-xl border px-3.5 text-sm font-medium transition ${
                zonaActiva === zona
                  ? 'border-ambar-500 bg-ambar-500/15 text-ambar-300'
                  : 'border-noche-700 bg-noche-900 text-noche-300 hover:bg-noche-800'
              }`}
            >
              {zona === 'todas' ? 'Todas' : NOMBRE_ZONA[zona]}
            </button>
          ))}
        </div>
      </div>

      <main className="flex-1 px-4 py-4">
        {cargando ? (
          <Cargando mensaje="Cargando el salón" />
        ) : (
          <div className="space-y-6">
            {ZONAS.filter((z) => visibles.some((m) => m.zona === z)).map((zona) => (
              <section key={zona}>
                <h2 className="mb-2.5 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-noche-400">
                  <LayoutGrid className="h-3.5 w-3.5" aria-hidden />
                  {NOMBRE_ZONA[zona]}
                </h2>
                <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
                  {visibles
                    .filter((m) => m.zona === zona)
                    .map((mesa) => (
                      <TarjetaMesa key={mesa.id} mesa={mesa} onTocar={tocarMesa} />
                    ))}
                </div>
              </section>
            ))}
          </div>
        )}
      </main>


      <HojaInferior
        abierta={!!mesaAAbrir}
        titulo={mesaAAbrir ? `Abrir ${mesaAAbrir.nombre ?? `mesa ${mesaAAbrir.numero}`}` : ''}
        descripcion={mesaAAbrir ? `${mesaAAbrir.capacidad} puestos · ${NOMBRE_ZONA[mesaAAbrir.zona]}` : undefined}
        onCerrar={() => setMesaAAbrir(null)}
        pie={
          <Boton variante="principal" tamano="grande" bloque cargando={abriendo} onClick={abrirMesa}>
            Abrir mesa
          </Boton>
        }
      >
        <div className="space-y-4">
          <div>
            <p className="mb-2 flex items-center gap-2 text-sm text-noche-300">
              <Users className="h-4 w-4" aria-hidden />
              ¿Cuántos comensales?
            </p>
            <div className="flex items-center justify-between gap-3">
              <Contador valor={comensales} onCambiar={setComensales} minimo={1} maximo={20} />
              <div className="flex gap-2">
                {[2, 4, 6, 8].map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setComensales(n)}
                    className={`h-toque w-12 rounded-xl border text-base font-semibold transition ${
                      comensales === n
                        ? 'border-ambar-500 bg-ambar-500/15 text-ambar-300'
                        : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                    }`}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {mesaAAbrir && comensales > mesaAAbrir.capacidad && (
            <p className="rounded-xl border border-estado-proceso/40 bg-estado-proceso-suave px-3 py-2 text-sm text-estado-proceso">
              La mesa tiene {mesaAAbrir.capacidad} puestos. Puedes abrirla igual, pero avisa en salón.
            </p>
          )}
        </div>
      </HojaInferior>
    </div>
  )
}

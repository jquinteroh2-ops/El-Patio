import { useState } from 'react'
import { CloudOff, Percent, RotateCcw, Users, Utensils } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { useEstadoConexion, vaciarCola } from '@/compartido/conexion'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Ajustes, Rol, Usuario } from '@/compartido/tipos'
import { NOMBRE_ZONA } from '@/compartido/estados'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Insignia } from '@/componentes/ui/Insignia'
import { Interruptor } from '@/componentes/ui/Interruptor'
import { useAvisos } from '@/componentes/ui/Avisos'

const NOMBRE_ROL: Record<Rol, string> = {
  mesero: 'Mesero',
  cocina: 'Cocina',
  cajero: 'Cajero',
  administrador: 'Administrador',
}

const AJUSTES_VACIOS: Ajustes = {
  porcentajeInc: 8,
  simularSinConexion: false,
  consecutivoOrden: 0,
  fechaConsecutivo: '',
}

export default function Configuracion() {
  const { mostrar } = useAvisos()
  const conexion = useEstadoConexion()

  const { datos: ajustes } = useSyncedState<Ajustes>(
    () => api.obtenerAjustes(),
    AJUSTES_VACIOS,
    [],
    ['ajustes', 'todo'],
  )
  const { datos: usuarios } = useSyncedState<Usuario[]>(
    () => api.listarUsuarios(),
    [],
    [],
    ['usuarios', 'todo'],
  )
  const { datos: mesas } = useSyncedState<MesaEnMapa[]>(
    () => api.listarMesas(),
    [],
    [],
    ['mesas', 'ordenes', 'todo'],
  )

  const [inc, setInc] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [reiniciando, setReiniciando] = useState(false)
  const [confirmarReinicio, setConfirmarReinicio] = useState(false)

  const valorInc = inc ?? String(ajustes.porcentajeInc)

  const guardarInc = async () => {
    const numero = Number(valorInc.replace(',', '.'))
    if (Number.isNaN(numero) || numero < 0 || numero > 20) {
      mostrar('El porcentaje debe estar entre 0 y 20', 'error')
      return
    }
    setGuardando(true)
    try {
      await api.actualizarAjustes({ porcentajeInc: numero })
      setInc(null)
      mostrar(`Impuesto al consumo actualizado a ${numero}%`, 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const alternarConexion = (sinConexion: boolean) => {
    api.alternarSinConexion(sinConexion)
    mostrar(
      sinConexion
        ? 'Modo sin conexión activado. La comandera sigue funcionando.'
        : 'Conexión restablecida. Las comandas en cola salen solas.',
      sinConexion ? 'info' : 'exito',
    )
  }

  const reiniciar = async () => {
    setReiniciando(true)
    try {
      vaciarCola()
      await api.reiniciarDemo()
      setConfirmarReinicio(false)
      mostrar('Salón reiniciado con datos frescos', 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo reiniciar', 'error')
    } finally {
      setReiniciando(false)
    }
  }

  const porZona = (['salon', 'terraza', 'privado'] as const).map((zona) => ({
    zona,
    mesas: mesas.filter((m) => m.zona === zona),
  }))

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {/* ---------- Impuesto ---------- */}
      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
        <h2 className="mb-1 flex items-center gap-2 text-sm font-semibold text-crema-100">
          <Percent className="h-4 w-4" aria-hidden />
          Impuesto al consumo
        </h2>
        <p className="mb-4 text-xs leading-relaxed text-noche-400">
          A los restaurantes en Colombia les aplica el Impuesto Nacional al Consumo, no IVA. Se
          calcula sobre el subtotal de alimentos y bebidas, antes de la propina.
        </p>

        <div className="flex items-end gap-2">
          <label className="block flex-1">
            <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
              Porcentaje
            </span>
            <div className="relative">
              <input
                inputMode="decimal"
                value={valorInc}
                onChange={(e) => setInc(e.target.value)}
                className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 pr-9 text-crema-100 focus:border-ambar-500 focus:outline-none"
              />
              <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-noche-400">%</span>
            </div>
          </label>
          <Boton
            variante="principal"
            cargando={guardando}
            disabled={valorInc === String(ajustes.porcentajeInc)}
            onClick={guardarInc}
          >
            Guardar
          </Boton>
        </div>

        <p className="mt-3 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2 text-xs text-noche-400">
          Una cuenta de {formatoCOP(100000)} en consumo paga{' '}
          <strong className="text-crema-100">
            {formatoCOP(Math.round((100000 * Number(valorInc.replace(',', '.') || 0)) / 100))}
          </strong>{' '}
          de impuesto.
        </p>
      </section>

      {/* ---------- Demostración ---------- */}
      <section className="rounded-2xl border border-ambar-500/30 bg-noche-900 p-4">
        <h2 className="mb-1 flex items-center gap-2 text-sm font-semibold text-crema-100">
          <CloudOff className="h-4 w-4" aria-hidden />
          Demostración
        </h2>
        <p className="mb-4 text-xs leading-relaxed text-noche-400">
          Herramientas para mostrar el sistema en vivo. No forman parte de la operación diaria.
        </p>

        <div className="space-y-3">
          <div className="flex items-start justify-between gap-3 rounded-xl border border-noche-700 bg-noche-850 p-3">
            <div className="min-w-0">
              <p className="text-sm font-medium text-crema-100">Simular pérdida de WiFi</p>
              <p className="mt-0.5 text-xs leading-relaxed text-noche-400">
                La comandera sigue tomando pedidos y los guarda en una cola local. Al apagar el
                interruptor salen solos hacia cocina.
              </p>
              {conexion.pendientes > 0 && (
                <p className="mt-2">
                  <Insignia tono="proceso">
                    {conexion.pendientes} {conexion.pendientes === 1 ? 'comanda' : 'comandas'} en cola
                  </Insignia>
                </p>
              )}
            </div>
            <Interruptor
              activo={ajustes.simularSinConexion}
              onCambiar={alternarConexion}
              etiqueta="Simular sin conexión"
              descripcion={ajustes.simularSinConexion ? 'Sin señal' : 'En línea'}
            />
          </div>

          <div className="flex items-center justify-between gap-3 rounded-xl border border-noche-700 bg-noche-850 p-3">
            <div className="min-w-0">
              <p className="text-sm font-medium text-crema-100">Reiniciar el salón</p>
              <p className="mt-0.5 text-xs text-noche-400">
                Vuelve a sembrar mesas, comandas e histórico con la hora actual.
              </p>
            </div>
            <Boton
              icono={<RotateCcw className="h-4 w-4" />}
              onClick={() => setConfirmarReinicio(true)}
            >
              Reiniciar
            </Boton>
          </div>
        </div>
      </section>

      {/* ---------- Usuarios ---------- */}
      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-crema-100">
          <Users className="h-4 w-4" aria-hidden />
          Personal
        </h2>
        <ul className="space-y-1.5">
          {usuarios.map((usuario) => (
            <li
              key={usuario.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5"
            >
              <div className="min-w-0">
                <p className="truncate text-sm text-crema-100">{usuario.nombre}</p>
                <p className="text-xs text-noche-500">
                  usuario: <span className="text-noche-400">{usuario.usuario}</span>
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Insignia tono={usuario.activo ? 'neutro' : 'demorado'}>
                  {NOMBRE_ROL[usuario.rol]}
                </Insignia>
                <Interruptor
                  activo={usuario.activo}
                  etiqueta={`Acceso de ${usuario.nombre}`}
                  onCambiar={async (activo) => {
                    await api.guardarUsuario({ ...usuario, activo })
                    mostrar(
                      activo ? `${usuario.nombre} puede entrar` : `Acceso suspendido a ${usuario.nombre}`,
                      activo ? 'exito' : 'info',
                    )
                  }}
                />
              </div>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-noche-500">
          Al suspender a alguien, deja de poder iniciar sesión. Las sesiones ya abiertas terminan al
          cerrar la pestaña.
        </p>
      </section>

      {/* ---------- Mesas ---------- */}
      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-crema-100">
          <Utensils className="h-4 w-4" aria-hidden />
          Mesas y zonas
        </h2>
        <div className="space-y-3">
          {porZona.map(({ zona, mesas: deLaZona }) => (
            <div key={zona}>
              <p className="mb-1.5 flex items-baseline justify-between text-xs uppercase tracking-wider text-noche-400">
                {NOMBRE_ZONA[zona]}
                <span className="text-noche-500">
                  {deLaZona.length} mesas · {deLaZona.reduce((s, m) => s + m.capacidad, 0)} puestos
                </span>
              </p>
              <div className="flex flex-wrap gap-1.5">
                {deLaZona.map((mesa) => (
                  <span
                    key={mesa.id}
                    title={`${mesa.nombre ?? `Mesa ${mesa.numero}`} · ${mesa.capacidad} puestos`}
                    className="flex h-10 min-w-[40px] flex-col items-center justify-center rounded-lg border border-noche-700 bg-noche-850 px-1.5 text-xs"
                  >
                    <span className="font-semibold text-crema-100">{mesa.numero}</span>
                    <span className="text-[0.6rem] text-noche-500">{mesa.capacidad}p</span>
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs text-noche-500">
          {mesas.length} mesas en total, {mesas.reduce((s, m) => s + m.capacidad, 0)} puestos.
        </p>
      </section>

      <HojaInferior
        abierta={confirmarReinicio}
        titulo="Reiniciar el salón"
        descripcion="Solo para la demostración"
        onCerrar={() => setConfirmarReinicio(false)}
        pie={
          <Boton variante="peligro" tamano="grande" bloque cargando={reiniciando} onClick={reiniciar}>
            Sí, reiniciar todo
          </Boton>
        }
      >
        <p className="text-sm leading-relaxed text-noche-300">
          Se borran las comandas, los pagos y las reservas de esta demostración, y se vuelven a
          sembrar con la hora actual: seis mesas ocupadas, tres comandas en cocina y diez días de
          histórico. Las demás pestañas abiertas se actualizan solas.
        </p>
      </HojaInferior>
    </div>
  )
}

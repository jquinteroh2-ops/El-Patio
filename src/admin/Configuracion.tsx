import { useState } from 'react'
import { Percent, PlusCircle, UserPlus, Users, Utensils } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { MesaEnMapa } from '@/compartido/mockApi'
import { formatoCOP } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Ajustes, Mesa, Usuario } from '@/compartido/tipos'
import { NOMBRE_ROL, NOMBRE_ZONA } from '@/compartido/estados'
import { Boton } from '@/componentes/ui/Boton'
import { Insignia } from '@/componentes/ui/Insignia'
import { Interruptor } from '@/componentes/ui/Interruptor'
import { useAvisos } from '@/componentes/ui/Avisos'
import { EditorMesa } from './EditorMesa'
import { FichaDelSitio } from './FichaDelSitio'
import { EditorUsuario } from './EditorUsuario'
import { ZonasDomicilio } from './ZonasDomicilio'

const AJUSTES_VACIOS: Ajustes = {
  porcentajeInc: 8,
  consecutivoOrden: 0,
  fechaConsecutivo: '',
  domiciliosPausados: false,
  domiciliosDesde: '11:30:00',
  domiciliosHasta: '21:30:00',
}

export default function Configuracion() {
  const { mostrar } = useAvisos()

  const { datos: ajustes } = useSyncedState<Ajustes>(
    () => api.obtenerAjustes(),
    AJUSTES_VACIOS,
    [],
    ['ajustes', 'todo'],
  )
  const { datos: usuarios, refrescar: refrescarUsuarios } = useSyncedState<Usuario[]>(
    () => api.listarUsuarios(),
    [],
    [],
    ['usuarios', 'todo'],
  )
  const { datos: mesas, refrescar: refrescarMesas } = useSyncedState<MesaEnMapa[]>(
    () => api.listarMesas(),
    [],
    [],
    ['mesas', 'ordenes', 'todo'],
  )

  const [inc, setInc] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  // `editando` en null con el editor abierto significa cuenta nueva.
  const [editorAbierto, setEditorAbierto] = useState(false)
  const [editando, setEditando] = useState<Usuario | null>(null)
  const [guardandoUsuario, setGuardandoUsuario] = useState(false)

  // Igual para el editor de mesas.
  const [editorMesaAbierto, setEditorMesaAbierto] = useState(false)
  const [editandoMesa, setEditandoMesa] = useState<Mesa | null>(null)
  const [guardandoMesa, setGuardandoMesa] = useState(false)
  const [eliminandoMesa, setEliminandoMesa] = useState(false)

  const abrirEditor = (usuario: Usuario | null) => {
    setEditando(usuario)
    setEditorAbierto(true)
  }

  const guardarUsuario = async (borrador: Usuario) => {
    setGuardandoUsuario(true)
    try {
      await api.guardarUsuario(borrador)
      setEditorAbierto(false)
      refrescarUsuarios()
      mostrar(
        borrador.id ? `${borrador.nombre}: cuenta actualizada` : `${borrador.nombre} ya puede entrar`,
        'exito',
      )
    } catch (error) {
      // El aviso se queda en la hoja abierta a proposito: el borrador no se
      // pierde y se corrige lo que el servidor rechazo sin volver a escribirlo.
      mostrar(error instanceof Error ? error.message : 'No se pudo guardar la cuenta', 'error')
    } finally {
      setGuardandoUsuario(false)
    }
  }

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

  const abrirEditorMesa = (mesa: Mesa | null) => {
    setEditandoMesa(mesa)
    setEditorMesaAbierto(true)
  }

  const guardarMesa = async (borrador: Mesa) => {
    setGuardandoMesa(true)
    try {
      await api.guardarMesa(borrador)
      setEditorMesaAbierto(false)
      refrescarMesas()
      mostrar(
        borrador.id ? `Mesa ${borrador.numero}: actualizada` : `Mesa ${borrador.numero} creada`,
        'exito',
      )
    } catch (error) {
      mostrar(error instanceof Error ? error.message : 'No se pudo guardar la mesa', 'error')
    } finally {
      setGuardandoMesa(false)
    }
  }

  const eliminarMesa = async (mesaId: string) => {
    setEliminandoMesa(true)
    try {
      await api.eliminarMesa(mesaId)
      setEditorMesaAbierto(false)
      refrescarMesas()
      mostrar('Mesa eliminada', 'exito')
    } catch (error) {
      mostrar(error instanceof Error ? error.message : 'No se pudo eliminar la mesa', 'error')
    } finally {
      setEliminandoMesa(false)
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
                className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 pr-9 text-crema-100 focus:border-oro-500 focus:outline-none"
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

      {/* ---------- Horario y contacto del sitio ---------- */}
      <FichaDelSitio />

      {/* ---------- Domicilios ---------- */}
      <ZonasDomicilio ajustes={ajustes} />

      {/* ---------- Usuarios ---------- */}
      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-crema-100">
            <Users className="h-4 w-4" aria-hidden />
            Personal
          </h2>
          <Boton
            variante="secundario"
            icono={<UserPlus className="h-4 w-4" aria-hidden />}
            onClick={() => abrirEditor(null)}
          >
            Nueva cuenta
          </Boton>
        </div>
        <ul className="space-y-1.5">
          {usuarios.map((usuario) => (
            <li
              key={usuario.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5"
            >
              {/*
                El nombre es el boton de editar. Va aparte del interruptor y no
                envolviendolo, porque un boton dentro de otro deja de anunciarse
                bien y con el pulgar se termina suspendiendo a alguien sin
                querer al intentar abrir su ficha.
              */}
              <button
                type="button"
                onClick={() => abrirEditor(usuario)}
                className="min-w-0 flex-1 rounded-lg text-left transition hover:opacity-80"
              >
                <p className="truncate text-sm text-crema-100">{usuario.nombre}</p>
                <p className="truncate text-xs text-noche-500">
                  usuario: <span className="text-noche-400">{usuario.usuario}</span>
                  {usuario.correo && (
                    <> · <span className="text-noche-400">{usuario.correo}</span></>
                  )}
                </p>
              </button>
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
          Toca un nombre para cambiarle el rol, el correo o la clave. Al suspender a alguien, deja de
          poder iniciar sesión y las sesiones que tuviera abiertas terminan de inmediato.
        </p>
      </section>

      <EditorUsuario
        abierto={editorAbierto}
        usuario={editando}
        guardando={guardandoUsuario}
        onCerrar={() => setEditorAbierto(false)}
        onGuardar={guardarUsuario}
      />

      {/* ---------- Mesas ---------- */}
      <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-crema-100">
            <Utensils className="h-4 w-4" aria-hidden />
            Mesas y zonas
          </h2>
          <Boton
            variante="secundario"
            icono={<PlusCircle className="h-4 w-4" aria-hidden />}
            onClick={() => abrirEditorMesa(null)}
          >
            Nueva mesa
          </Boton>
        </div>
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
                  <button
                    key={mesa.id}
                    type="button"
                    title={`${mesa.nombre ?? `Mesa ${mesa.numero}`} · ${mesa.capacidad} puestos · toca para editar`}
                    onClick={() => abrirEditorMesa(mesa)}
                    className="flex h-10 min-w-[40px] flex-col items-center justify-center rounded-lg border border-noche-700 bg-noche-850 px-1.5 text-xs transition hover:border-oro-500"
                  >
                    <span className="font-semibold text-crema-100">{mesa.numero}</span>
                    <span className="text-[0.6rem] text-noche-500">{mesa.capacidad}p</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs text-noche-500">
          {mesas.length} mesas en total, {mesas.reduce((s, m) => s + m.capacidad, 0)} puestos. Toca una
          mesa para editarla o quitarla.
        </p>
      </section>

      <EditorMesa
        abierto={editorMesaAbierto}
        mesa={editandoMesa}
        guardando={guardandoMesa}
        eliminando={eliminandoMesa}
        onCerrar={() => setEditorMesaAbierto(false)}
        onGuardar={guardarMesa}
        onEliminar={eliminarMesa}
      />
    </div>
  )
}

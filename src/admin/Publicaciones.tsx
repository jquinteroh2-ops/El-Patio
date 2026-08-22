import { useMemo, useState } from 'react'
import { CalendarDays, Image as IconoImagen, Megaphone, Plus, Sparkles } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { formatoFecha } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Publicacion, TipoPublicacion } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'
import { EditorPublicacion } from './EditorPublicacion'

const FILTROS: { valor: TipoPublicacion | 'todas'; etiqueta: string }[] = [
  { valor: 'todas', etiqueta: 'Todas' },
  { valor: 'promocion', etiqueta: 'Promociones' },
  { valor: 'evento', etiqueta: 'Eventos' },
  { valor: 'galeria', etiqueta: 'Fotos del local' },
]

const ETIQUETA_TIPO: Record<TipoPublicacion, string> = {
  promocion: 'Promoción',
  evento: 'Evento',
  galeria: 'Foto del local',
}

const ICONO_TIPO: Record<TipoPublicacion, typeof Megaphone> = {
  promocion: Megaphone,
  evento: Sparkles,
  galeria: IconoImagen,
}

/**
 * Por qué esta publicación no la está viendo nadie.
 *
 * Es la pregunta que el dueño se va a hacer, y la respuesta tiene dos causas
 * distintas que conviene no confundir: puede estar sin publicar, o publicada
 * pero fuera de fecha. Decir solo «no visible» lo dejaría buscando el problema
 * en el lugar equivocado.
 */
function porQueNoSeVe(p: Publicacion, hoy: string): string | null {
  if (!p.publicada) return 'Borrador: nadie más la ve'
  if (p.desde && hoy < p.desde) return `Empieza el ${formatoFecha(p.desde)}`
  if (p.hasta && hoy > p.hasta) return `Terminó el ${formatoFecha(p.hasta)}`
  return null
}

export default function Publicaciones() {
  const { mostrar } = useAvisos()
  const { datos: publicaciones, refrescar } = useSyncedState<Publicacion[]>(
    () => api.listarPublicaciones(),
    [],
    [],
    ['publicaciones', 'todo'],
  )

  const [filtro, setFiltro] = useState<TipoPublicacion | 'todas'>('todas')
  const [editando, setEditando] = useState<Publicacion | null>(null)
  const [creando, setCreando] = useState(false)
  const [guardando, setGuardando] = useState(false)

  const hoy = new Date().toISOString().slice(0, 10)

  const visibles = useMemo(
    () => publicaciones.filter((p) => (filtro === 'todas' ? true : p.tipo === filtro)),
    [publicaciones, filtro],
  )

  const enVivo = publicaciones.filter((p) => !porQueNoSeVe(p, hoy)).length

  const guardar = async (publicacion: Publicacion) => {
    setGuardando(true)
    try {
      await api.guardarPublicacion(publicacion)
      mostrar(publicacion.id ? 'Publicación actualizada' : 'Publicación creada', 'exito')
      setEditando(null)
      setCreando(false)
      refrescar()
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const eliminar = async (id: string) => {
    setGuardando(true)
    try {
      await api.eliminarPublicacion(id)
      mostrar('Publicación eliminada', 'exito')
      setEditando(null)
      refrescar()
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo eliminar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-titulo text-2xl text-crema-100">Publicaciones</h1>
          <p className="mt-0.5 text-sm text-noche-300">
            {enVivo === 0
              ? 'Ahora mismo el cliente no está viendo ninguna.'
              : `${enVivo} ${enVivo === 1 ? 'se está viendo' : 'se están viendo'} en el sitio.`}
          </p>
        </div>
        <Boton
          variante="principal"
          icono={<Plus className="h-4 w-4" aria-hidden />}
          onClick={() => setCreando(true)}
        >
          Nueva publicación
        </Boton>
      </header>

      <div className="flex flex-wrap gap-2">
        {FILTROS.map((f) => (
          <button
            key={f.valor}
            type="button"
            onClick={() => setFiltro(f.valor)}
            className={`min-h-toque rounded-xl border px-3.5 text-sm transition ${
              filtro === f.valor
                ? 'border-ambar-500 bg-ambar-500/10 text-ambar-300'
                : 'border-noche-700 text-noche-300'
            }`}
          >
            {f.etiqueta}
          </button>
        ))}
      </div>

      {visibles.length === 0 ? (
        <Vacio
          icono={Megaphone}
          titulo="Todavía no hay nada publicado"
          descripcion="Aquí se suben las promociones, los eventos y las fotos del local que ve el cliente en el sitio."
          accion={
            <Boton variante="principal" onClick={() => setCreando(true)}>
              Crear la primera
            </Boton>
          }
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {visibles.map((p) => {
            const razon = porQueNoSeVe(p, hoy)
            const Icono = ICONO_TIPO[p.tipo]
            return (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => setEditando(p)}
                  className="flex w-full flex-col overflow-hidden rounded-2xl border border-noche-800 bg-noche-900 text-left transition hover:border-noche-600"
                >
                  {p.imagen ? (
                    <img
                      src={api.urlImagen(p.imagen, 500)}
                      alt=""
                      loading="lazy"
                      className="h-36 w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-36 w-full items-center justify-center bg-noche-850 text-noche-500">
                      <Icono className="h-7 w-7" aria-hidden />
                    </div>
                  )}

                  <div className="flex flex-1 flex-col gap-1.5 p-3.5">
                    <div className="flex items-center gap-2">
                      <Insignia tono={razon ? 'neutro' : 'listo'}>
                        {razon ? 'No se ve' : 'En el sitio'}
                      </Insignia>
                      <span className="text-xs text-noche-400">{ETIQUETA_TIPO[p.tipo]}</span>
                    </div>

                    <p className="font-medium text-crema-100">{p.titulo}</p>

                    {p.cuerpo && (
                      <p className="line-clamp-2 text-sm text-noche-300">{p.cuerpo}</p>
                    )}

                    {(p.desde || p.hasta) && (
                      <p className="flex items-center gap-1.5 text-xs text-noche-400">
                        <CalendarDays className="h-3.5 w-3.5" aria-hidden />
                        {p.desde ? formatoFecha(p.desde) : 'siempre'} —{' '}
                        {p.hasta ? formatoFecha(p.hasta) : 'sin fin'}
                      </p>
                    )}

                    {razon && <p className="text-xs text-noche-400">{razon}</p>}
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <EditorPublicacion
        abierto={creando || editando !== null}
        publicacion={editando}
        guardando={guardando}
        onCerrar={() => {
          setEditando(null)
          setCreando(false)
        }}
        onGuardar={(p) => void guardar(p)}
        onEliminar={(id) => void eliminar(id)}
      />
    </div>
  )
}

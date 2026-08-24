import { useState } from 'react'
import { Eye, EyeOff, Save } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { ContenidoInstitucional } from '@/compartido/mockApi'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Boton } from '@/componentes/ui/Boton'
import { Cargando } from '@/componentes/ui/Cargando'
import { Interruptor } from '@/componentes/ui/Interruptor'
import { useAvisos } from '@/componentes/ui/Avisos'

/**
 * El editor del texto institucional.
 *
 * Una tarjeta por sección, cada una con su propio botón de guardar. No hay un
 * «guardar todo» a propósito: quien corrige la misión no quiere arriesgarse a
 * publicar de paso un borrador a medias de la visión.
 */
export default function Institucional() {
  const { mostrar } = useAvisos()

  const { datos, cargando, refrescar } = useSyncedState<ContenidoInstitucional[]>(
    () => api.contenidoInstitucionalCompleto(),
    [],
    [],
    ['institucional', 'todo'],
  )

  if (cargando) return <Cargando mensaje="Cargando el contenido" />

  return (
    <div className="space-y-4">
      <p className="text-sm leading-relaxed text-noche-400">
        Este es el texto que se lee en la página de inicio del sitio público. Los cambios se
        ven de inmediato, sin desplegar nada.
      </p>

      {datos.map((bloque) => (
        <Tarjeta
          key={bloque.clave}
          bloque={bloque}
          onGuardado={(mensaje) => {
            mostrar(mensaje, 'exito')
            refrescar()
          }}
          onError={(mensaje) => mostrar(mensaje, 'error')}
        />
      ))}
    </div>
  )
}

function Tarjeta({
  bloque,
  onGuardado,
  onError,
}: {
  bloque: ContenidoInstitucional
  onGuardado: (mensaje: string) => void
  onError: (mensaje: string) => void
}) {
  const [titulo, setTitulo] = useState(bloque.titulo)
  const [cuerpo, setCuerpo] = useState(bloque.cuerpo)
  const [visible, setVisible] = useState(bloque.visible)
  const [guardando, setGuardando] = useState(false)

  const cambiado =
    titulo !== bloque.titulo || cuerpo !== bloque.cuerpo || visible !== bloque.visible

  // El texto de la migración viene marcado así. Mientras siga ahí, la sección
  // está publicada con un marcador de posición y conviene que se vea en el
  // panel, no solo en el sitio.
  const esBorrador = bloque.cuerpo.includes('PENDIENTE:')

  const guardar = async () => {
    setGuardando(true)
    try {
      await api.editarContenidoInstitucional(bloque.clave, { titulo, cuerpo, visible })
      onGuardado(`«${titulo}» guardado`)
    } catch (e) {
      onError(e instanceof Error ? e.message : 'No se pudo guardar')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {visible ? (
            <Eye className="h-4 w-4 text-oro-400" aria-hidden />
          ) : (
            <EyeOff className="h-4 w-4 text-noche-500" aria-hidden />
          )}
          <span className="text-xs uppercase tracking-wide text-noche-400">{bloque.clave}</span>
          {esBorrador && (
            <span className="rounded-lg border border-estado-proceso/35 bg-estado-proceso/15 px-2 py-0.5 text-xs font-medium text-estado-proceso">
              Sin escribir
            </span>
          )}
        </div>

        <Interruptor
          activo={visible}
          onCambiar={setVisible}
          etiqueta={visible ? 'Se ve en el sitio' : 'Oculta'}
        />
      </div>

      <label className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Título</label>
      <input
        value={titulo}
        onChange={(e) => setTitulo(e.target.value)}
        className="mb-3 w-full min-h-toque rounded-xl border border-noche-700 bg-noche-950 px-3 text-crema-100 focus:border-oro-500 focus:outline-none"
      />

      <label className="mb-1 block text-xs uppercase tracking-wide text-noche-400">Texto</label>
      <textarea
        value={cuerpo}
        onChange={(e) => setCuerpo(e.target.value)}
        rows={7}
        className="w-full rounded-xl border border-noche-700 bg-noche-950 p-3 text-sm leading-relaxed text-crema-100 focus:border-oro-500 focus:outline-none"
        placeholder="Los saltos de línea se respetan tal como los escriba."
      />
      <p className="mt-1.5 text-xs text-noche-500">
        Texto normal. Los saltos de línea se respetan; no se puede usar HTML ni negritas.
      </p>

      <Boton
        variante="principal"
        icono={<Save className="h-4 w-4" aria-hidden />}
        cargando={guardando}
        disabled={!cambiado}
        onClick={() => void guardar()}
        className="mt-3"
      >
        {cambiado ? 'Guardar cambios' : 'Sin cambios'}
      </Boton>
    </section>
  )
}

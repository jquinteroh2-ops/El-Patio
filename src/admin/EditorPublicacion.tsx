import { useEffect, useRef, useState } from 'react'
import { ImagePlus, Trash2, X } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { Publicacion, TipoPublicacion } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Campo, CampoArea, CampoSelect } from '@/componentes/ui/Campo'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Interruptor } from '@/componentes/ui/Interruptor'

/** Como se llama cada tipo en la pantalla, y que se espera de el. */
const TIPOS: { valor: TipoPublicacion; etiqueta: string; ayuda: string }[] = [
  {
    valor: 'promocion',
    etiqueta: 'Promoción',
    ayuda: 'Una oferta con fecha. Sale de primera en el sitio.',
  },
  {
    valor: 'evento',
    etiqueta: 'Evento',
    ayuda: 'Una noche de música, un menú especial, una fecha.',
  },
  {
    valor: 'galeria',
    etiqueta: 'Foto del local',
    ayuda: 'Cómo se ve el restaurante por dentro. No vence.',
  },
]

const vacia = (): Publicacion => ({
  id: '',
  tipo: 'promocion',
  titulo: '',
  cuerpo: '',
  imagen: null,
  desde: null,
  hasta: null,
  publicada: false,
  orden: 0,
})

interface Props {
  abierto: boolean
  publicacion: Publicacion | null
  guardando: boolean
  onCerrar: () => void
  onGuardar: (publicacion: Publicacion) => void
  onEliminar?: (id: string) => void
}

export function EditorPublicacion({
  abierto,
  publicacion,
  guardando,
  onCerrar,
  onGuardar,
  onEliminar,
}: Props) {
  const [borrador, setBorrador] = useState<Publicacion>(vacia())
  const [error, setError] = useState<string | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const archivo = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!abierto) return
    setError(null)
    setBorrador(publicacion ? structuredClone(publicacion) : vacia())
  }, [abierto, publicacion])

  const cambiar = (cambios: Partial<Publicacion>) => setBorrador((b) => ({ ...b, ...cambios }))

  const elegirFoto = async (archivos: FileList | null) => {
    const elegido = archivos?.[0]
    if (!elegido) return
    setError(null)
    setSubiendo(true)
    try {
      // La foto sube ya, antes de guardar la publicación. Así el dueño la ve en
      // pantalla y decide, y si después cambia el texto no tiene que volver a
      // subir los megas desde el celular.
      const nombre = await api.subirImagenPublicacion(elegido)
      cambiar({ imagen: nombre })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo subir la foto')
    } finally {
      setSubiendo(false)
      if (archivo.current) archivo.current.value = ''
    }
  }

  const guardar = () => {
    if (!borrador.titulo.trim()) {
      setError('La publicación necesita un título')
      return
    }
    if (borrador.desde && borrador.hasta && borrador.desde > borrador.hasta) {
      setError('La vigencia terminaría antes de empezar')
      return
    }
    onGuardar({
      ...borrador,
      titulo: borrador.titulo.trim(),
      cuerpo: borrador.cuerpo.trim(),
      // Un campo de fecha vacío entrega cadena vacía, no nulo, y el servidor
      // espera nulo para decir «sin vencimiento».
      desde: borrador.desde || null,
      hasta: borrador.hasta || null,
    })
  }

  const esGaleria = borrador.tipo === 'galeria'

  return (
    <HojaInferior
      abierta={abierto}
      titulo={publicacion ? 'Editar publicación' : 'Nueva publicación'}
      descripcion={
        publicacion ? publicacion.titulo : 'Promoción, evento o foto del local'
      }
      onCerrar={onCerrar}
      pie={
        <div className="flex gap-2">
          {publicacion && onEliminar && (
            <Boton
              variante="peligro"
              tamano="grande"
              icono={<Trash2 className="h-4 w-4" />}
              onClick={() => onEliminar(publicacion.id)}
            >
              Eliminar
            </Boton>
          )}
          <Boton
            variante="principal"
            tamano="grande"
            bloque
            cargando={guardando}
            onClick={guardar}
          >
            Guardar
          </Boton>
        </div>
      }
    >
      <div className="space-y-4">
        <CampoSelect
          etiqueta="Qué es"
          value={borrador.tipo}
          ayuda={TIPOS.find((t) => t.valor === borrador.tipo)?.ayuda}
          onChange={(e) => cambiar({ tipo: e.target.value as TipoPublicacion })}
        >
          {TIPOS.map((t) => (
            <option key={t.valor} value={t.valor}>
              {t.etiqueta}
            </option>
          ))}
        </CampoSelect>

        <Campo
          etiqueta="Título"
          value={borrador.titulo}
          maxLength={80}
          placeholder={esGaleria ? 'La terraza al atardecer' : '2x1 en cócteles los jueves'}
          onChange={(e) => cambiar({ titulo: e.target.value })}
        />

        <CampoArea
          etiqueta="Texto"
          value={borrador.cuerpo}
          maxLength={600}
          placeholder="Lo que el cliente necesita saber. Puede ir vacío."
          onChange={(e) => cambiar({ cuerpo: e.target.value })}
        />

        {/* ---------- La foto ---------- */}
        <div>
          <span className="mb-1.5 block text-sm font-medium text-crema-100">Foto</span>
          {borrador.imagen ? (
            <div className="relative overflow-hidden rounded-xl border border-noche-700">
              <img
                src={api.urlImagen(borrador.imagen, 700)}
                alt=""
                className="max-h-56 w-full object-cover"
              />
              <button
                type="button"
                aria-label="Quitar la foto"
                onClick={() => cambiar({ imagen: null })}
                className="absolute right-2 top-2 rounded-full bg-noche-950/80 p-2 text-crema-100"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => archivo.current?.click()}
              disabled={subiendo}
              className="flex min-h-[104px] w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-noche-700 text-noche-300"
            >
              <ImagePlus className="h-6 w-6" aria-hidden />
              <span className="text-sm">
                {subiendo ? 'Subiendo la foto…' : 'Tocar para elegir una foto'}
              </span>
            </button>
          )}
          <input
            ref={archivo}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => void elegirFoto(e.target.files)}
          />
          <p className="mt-1.5 text-xs text-noche-400">
            Se reduce sola al subirla: no hace falta recortarla antes.
          </p>
        </div>

        {/* ---------- Vigencia ----------
            Se ofrece siempre, pero para las fotos del local no tiene sentido y
            se dice: una foto de la terraza no vence. */}
        <div className="grid grid-cols-2 gap-3">
          <Campo
            etiqueta="Desde"
            type="date"
            value={borrador.desde ?? ''}
            onChange={(e) => cambiar({ desde: e.target.value || null })}
          />
          <Campo
            etiqueta="Hasta"
            type="date"
            value={borrador.hasta ?? ''}
            onChange={(e) => cambiar({ hasta: e.target.value || null })}
          />
        </div>
        <p className="-mt-2 text-xs text-noche-400">
          {esGaleria
            ? 'Una foto del local no suele vencer: puede dejar las dos fechas vacías.'
            : 'Vacías, se muestra mientras esté publicada. Con fecha, aparece y desaparece sola.'}
        </p>

        <Campo
          etiqueta="Orden"
          type="number"
          inputMode="numeric"
          value={borrador.orden}
          ayuda="Menor sale primero. Con el mismo número, primero la más nueva."
          onChange={(e) => cambiar({ orden: Number(e.target.value) || 0 })}
        />

        <Interruptor
          activo={borrador.publicada}
          etiqueta="Publicada"
          descripcion={
            borrador.publicada
              ? 'El cliente la ve, si está dentro de la vigencia'
              : 'Guardada como borrador: nadie más la ve'
          }
          onCambiar={(publicada) => cambiar({ publicada })}
        />

        {error && (
          <p className="rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
            {error}
          </p>
        )}
      </div>
    </HojaInferior>
  )
}

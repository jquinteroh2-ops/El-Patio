import { useEffect, useState } from 'react'
import type { Mesa, Zona } from '@/compartido/tipos'
import { NOMBRE_ZONA } from '@/compartido/estados'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'

interface Props {
  abierto: boolean
  /** null cuando se esta creando una mesa nueva. */
  mesa: Mesa | null
  guardando: boolean
  eliminando: boolean
  onCerrar: () => void
  onGuardar: (mesa: Mesa) => void
  onEliminar: (mesaId: string) => void
}

const ZONAS: Zona[] = ['salon', 'terraza', 'privado']

const CAMPO =
  'min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none'

const vacia = (): Mesa => ({
  id: '',
  numero: 1,
  nombre: '',
  zona: 'salon',
  capacidad: 4,
  estado: 'libre',
})

export function EditorMesa({ abierto, mesa, guardando, eliminando, onCerrar, onGuardar, onEliminar }: Props) {
  const [borrador, setBorrador] = useState<Mesa>(vacia())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!abierto) return
    setError(null)
    setBorrador(mesa ?? vacia())
  }, [abierto, mesa])

  const cambiar = (cambios: Partial<Mesa>) => setBorrador((b) => ({ ...b, ...cambios }))

  const guardar = () => {
    const nombre = (borrador.nombre ?? '').trim()

    if (!Number.isInteger(borrador.numero) || borrador.numero < 1) {
      return setError('El número de mesa debe ser un entero mayor a 0')
    }
    if (!Number.isInteger(borrador.capacidad) || borrador.capacidad < 1) {
      return setError('La capacidad debe ser un entero mayor a 0')
    }

    setError(null)
    onGuardar({ ...borrador, nombre: nombre || undefined })
  }

  return (
    <HojaInferior
      abierta={abierto}
      titulo={mesa ? 'Editar mesa' : 'Nueva mesa'}
      descripcion={mesa ? `Mesa ${mesa.numero}` : 'Se agrega al mapa de sala y al QR del menú'}
      onCerrar={onCerrar}
      pie={
        <div className="flex gap-2">
          {mesa && (
            <Boton
              variante="secundario"
              cargando={eliminando}
              disabled={!!mesa.ordenActivaId}
              onClick={() => onEliminar(mesa.id)}
            >
              Eliminar
            </Boton>
          )}
          <Boton variante="principal" bloque cargando={guardando} onClick={guardar}>
            Guardar
          </Boton>
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <p className="rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
            {error}
          </p>
        )}

        {mesa?.ordenActivaId && (
          <p className="rounded-xl border border-noche-700 bg-noche-850 px-3 py-2 text-xs text-noche-400">
            Esta mesa tiene una cuenta abierta: no se puede eliminar hasta que se cierre.
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">Número</span>
          <input
            type="number"
            inputMode="numeric"
            value={borrador.numero}
            onChange={(e) => cambiar({ numero: Number(e.target.value) })}
            className={CAMPO}
          />
          <span className="mt-1.5 block text-xs text-noche-500">
            Es el que se imprime en el QR: <code>?mesa={borrador.numero}</code>
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Nombre <span className="text-noche-500">· opcional</span>
          </span>
          <input
            value={borrador.nombre ?? ''}
            onChange={(e) => cambiar({ nombre: e.target.value })}
            placeholder="Terraza 3"
            className={CAMPO}
          />
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">Zona</span>
          <select
            value={borrador.zona}
            onChange={(e) => cambiar({ zona: e.target.value as Zona })}
            className={`${CAMPO} py-3`}
          >
            {ZONAS.map((zona) => (
              <option key={zona} value={zona}>
                {NOMBRE_ZONA[zona]}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">Capacidad</span>
          <input
            type="number"
            inputMode="numeric"
            value={borrador.capacidad}
            onChange={(e) => cambiar({ capacidad: Number(e.target.value) })}
            className={CAMPO}
          />
        </label>
      </div>
    </HojaInferior>
  )
}

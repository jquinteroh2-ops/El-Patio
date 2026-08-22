import { useEffect, useState } from 'react'
import type { Rol, Usuario } from '@/compartido/tipos'
import { NOMBRE_ROL } from '@/compartido/estados'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Interruptor } from '@/componentes/ui/Interruptor'

interface Props {
  abierto: boolean
  /** null cuando se esta creando una cuenta nueva. */
  usuario: Usuario | null
  guardando: boolean
  onCerrar: () => void
  onGuardar: (usuario: Usuario) => void
}

const ROLES: Rol[] = ['mesero', 'cocina', 'recepcion', 'repartidor', 'cajero', 'administrador']

/** A donde entra cada rol. Se lee bajo el selector, para no elegir a ciegas. */
const ALCANCE: Record<Rol, string> = {
  mesero: 'Comandera: abre mesas, toma y envía comandas.',
  cocina: 'Pantalla de cocina y barra: ve y despacha lo que está en producción.',
  recepcion: 'Recepción: domicilios y pedidos para llevar.',
  repartidor: 'Sus entregas: los domicilios que salieron a su nombre, con mapa y confirmación.',
  cajero: 'Caja, cierre de turno y recepción.',
  administrador: 'Todo, y además carta, reportes, personal y anulaciones.',
}

const CAMPO =
  'min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none'

const vacio = (): Usuario => ({
  id: '',
  nombre: '',
  rol: 'mesero',
  usuario: '',
  clave: '',
  correo: '',
  activo: true,
})

export function EditorUsuario({ abierto, usuario, guardando, onCerrar, onGuardar }: Props) {
  const [borrador, setBorrador] = useState<Usuario>(vacio())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!abierto) return
    setError(null)
    // La clave nunca llega del servidor: el borrador arranca vacia siempre, y
    // en una edicion eso significa «dejela como esta».
    setBorrador(usuario ? { ...usuario, clave: '', correo: usuario.correo ?? '' } : vacio())
  }, [abierto, usuario])

  const cambiar = (cambios: Partial<Usuario>) => setBorrador((b) => ({ ...b, ...cambios }))

  const guardar = () => {
    const nombre = borrador.nombre.trim()
    const nombreDeUsuario = borrador.usuario.trim()
    const correo = (borrador.correo ?? '').trim()

    if (!nombre) return setError('Falta el nombre de la persona')
    if (!nombreDeUsuario) return setError('Falta el usuario con el que va a entrar')
    if (/\s/.test(nombreDeUsuario)) return setError('El usuario no puede llevar espacios')
    if (!usuario && !borrador.clave) return setError('Una cuenta nueva necesita una clave')
    if (borrador.clave && borrador.clave.length < 4) {
      return setError('La clave necesita al menos 4 caracteres')
    }
    if (correo && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) {
      return setError(`«${correo}» no parece un correo`)
    }

    setError(null)
    onGuardar({ ...borrador, nombre, usuario: nombreDeUsuario, correo })
  }

  return (
    <HojaInferior
      abierta={abierto}
      titulo={usuario ? 'Editar cuenta' : 'Nueva cuenta'}
      descripcion={usuario ? usuario.nombre : 'Se crea el acceso para una persona del equipo'}
      onCerrar={onCerrar}
      pie={
        <Boton variante="principal" tamano="grande" bloque cargando={guardando} onClick={guardar}>
          Guardar
        </Boton>
      }
    >
      <div className="space-y-4">
        {error && (
          <p className="rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
            {error}
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Nombre y apellido
          </span>
          <input
            value={borrador.nombre}
            onChange={(e) => cambiar({ nombre: e.target.value })}
            placeholder="María Fernanda Ospina"
            className={CAMPO}
          />
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Usuario
          </span>
          <input
            value={borrador.usuario}
            onChange={(e) => cambiar({ usuario: e.target.value })}
            autoCapitalize="none"
            spellCheck={false}
            placeholder="mafe"
            className={CAMPO}
          />
          <span className="mt-1.5 block text-xs text-noche-500">
            Es lo que escribe para entrar. Corto: se teclea de pie y con prisa.
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Correo <span className="text-noche-500">· opcional</span>
          </span>
          <input
            type="email"
            value={borrador.correo ?? ''}
            onChange={(e) => cambiar({ correo: e.target.value })}
            autoCapitalize="none"
            spellCheck={false}
            placeholder="mafe@elpatio.co"
            className={CAMPO}
          />
          <span className="mt-1.5 block text-xs text-noche-500">
            Para avisarle un cambio de clave o de turno. No sirve para entrar.
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">Rol</span>
          <select
            value={borrador.rol}
            onChange={(e) => cambiar({ rol: e.target.value as Rol })}
            className={`${CAMPO} py-3`}
          >
            {ROLES.map((rol) => (
              <option key={rol} value={rol}>
                {NOMBRE_ROL[rol]}
              </option>
            ))}
          </select>
          <span className="mt-1.5 block text-xs text-noche-500">{ALCANCE[borrador.rol]}</span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            {usuario ? 'Clave nueva' : 'Clave'}
          </span>
          <input
            type="password"
            value={borrador.clave}
            onChange={(e) => cambiar({ clave: e.target.value })}
            autoComplete="new-password"
            placeholder={usuario ? 'Dejar en blanco para no cambiarla' : 'Mínimo 4 caracteres'}
            className={CAMPO}
          />
          {usuario && (
            <span className="mt-1.5 block text-xs text-noche-500">
              Cambiarla cierra todas las sesiones que esta persona tenga abiertas.
            </span>
          )}
        </label>

        <div className="flex items-center justify-between gap-3 rounded-xl border border-noche-800 bg-noche-850 px-3 py-2.5">
          <div className="min-w-0">
            <p className="text-sm text-crema-100">Puede entrar</p>
            <p className="text-xs text-noche-500">
              Al suspenderla, la persona sale del sistema de inmediato.
            </p>
          </div>
          <Interruptor
            activo={borrador.activo}
            etiqueta="Acceso de la cuenta"
            onCambiar={(activo) => cambiar({ activo })}
          />
        </div>
      </div>
    </HojaInferior>
  )
}

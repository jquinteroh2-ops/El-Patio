import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, LogIn } from 'lucide-react'
import { useSesion } from '@/compartido/auth'
import { RESTAURANTE } from '@/compartido/config'
import { CREDENCIALES_DEMO } from '@/compartido/datosSemilla'
import { Boton } from '@/componentes/ui/Boton'
import { Campo } from '@/componentes/ui/Campo'
import { AvisoDemo } from '@/componentes/AvisoDemo'

export default function Acceso() {
  const { sesion, ingresar, rutaInicial } = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()

  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  // Cada pestana tiene su propia sesion, asi que esto solo afecta a esta pestana.
  if (sesion) return <Navigate to={rutaInicial(sesion.rol)} replace />

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const nueva = await ingresar(usuario, clave)
      const destino = (ubicacion.state as { desde?: string } | null)?.desde
      navegar(destino ?? rutaInicial(nueva.rol), { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo ingresar')
      setEnviando(false)
    }
  }

  const usarCredencial = (u: string, c: string) => {
    setUsuario(u)
    setClave(c)
    setError(null)
  }

  return (
    <div className="flex min-h-screen flex-col bg-noche-950 px-4 py-8">
      <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center">
        <Link
          to="/"
          className="mb-8 inline-flex items-center gap-2 self-start text-sm text-noche-400 transition hover:text-crema-100"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Volver al sitio
        </Link>

        <div className="mb-8">
          <p className="font-marca text-2xl tracking-[0.2em] text-ambar-400">EL PATIO</p>
          <h1 className="mt-3 font-titulo text-3xl font-light text-crema-100">Acceso del personal</h1>
          <p className="mt-1 text-sm text-noche-400">
            {RESTAURANTE.ciudad} · Ingresa con el usuario que te asignó administración
          </p>
        </div>

        <form onSubmit={enviar} className="space-y-4">
          <Campo
            etiqueta="Usuario"
            value={usuario}
            onChange={(e) => setUsuario(e.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
          />
          <Campo
            etiqueta="Contraseña"
            type="password"
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            autoComplete="current-password"
            required
            error={error ?? undefined}
          />
          <Boton
            type="submit"
            variante="principal"
            tamano="grande"
            bloque
            cargando={enviando}
            icono={<LogIn className="h-5 w-5" aria-hidden />}
          >
            Ingresar
          </Boton>
        </form>

        <div className="mt-10 rounded-2xl border border-noche-700 bg-noche-900 p-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-ambar-400">
            Credenciales de prueba
          </p>
          <ul className="space-y-2">
            {CREDENCIALES_DEMO.map((c) => (
              <li key={c.usuario}>
                <button
                  type="button"
                  onClick={() => usarCredencial(c.usuario, c.clave)}
                  className="flex w-full items-center justify-between gap-3 rounded-xl border border-noche-700 bg-noche-850 px-3 py-2.5 text-left transition hover:border-ambar-500/50 hover:bg-noche-800"
                >
                  <span>
                    <span className="block text-sm font-medium text-crema-100">{c.rol}</span>
                    <span className="block text-xs text-noche-400">{c.destino}</span>
                  </span>
                  <span className="shrink-0 font-mono text-xs text-noche-300">
                    {c.usuario} / {c.clave}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs leading-relaxed text-noche-500">
            Toca una fila para llenar el formulario. Cada pestaña del navegador mantiene su propia
            sesión: puedes tener al mesero en una y a cocina en otra al mismo tiempo.
          </p>
        </div>
      </div>

      <footer className="mx-auto w-full max-w-md pt-8 text-center">
        <AvisoDemo />
      </footer>
    </div>
  )
}

import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, LogIn } from 'lucide-react'
import { useSesion } from '@/compartido/auth'
import { RESTAURANTE } from '@/compartido/config'
import { Boton } from '@/componentes/ui/Boton'
import { Campo } from '@/componentes/ui/Campo'

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

        {/*
          Aquí había una lista de credenciales de prueba con la clave escrita al
          lado. Servía para la demostración, pero el sistema ya maneja el dinero
          de la caja: una clave impresa en la pantalla de acceso es una clave
          pública. Las claves ahora las genera el servidor al arrancar por
          primera vez y se las entrega administración a cada persona.
        */}
        <div className="mt-10 rounded-2xl border border-noche-700 bg-noche-900 p-4">
          <p className="text-xs leading-relaxed text-noche-500">
            Cada pestaña del navegador mantiene su propia sesión: puedes tener al mesero en una y a
            cocina en otra al mismo tiempo. Si olvidaste tu clave, pídele a administración que te la
            cambie desde configuración.
          </p>
        </div>
      </div>

    </div>
  )
}

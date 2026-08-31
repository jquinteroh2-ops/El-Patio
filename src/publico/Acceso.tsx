import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, LogIn } from 'lucide-react'
import { useSesion } from '@/compartido/auth'
import { RESTAURANTE } from '@/compartido/config'
import { tomarAvisoDelCruce } from '@/compartido/cruce'
import { NOMBRE_ROL } from '@/compartido/estados'
import * as api from '@/compartido/mockApi'
import type { CuentasDemostracion } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Campo } from '@/componentes/ui/Campo'

const SIN_DEMOSTRACION: CuentasDemostracion = { activa: false, clave: '', cuentas: [] }

export default function Acceso() {
  const { sesion, ingresar, rutaInicial } = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()

  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [demostracion, setDemostracion] = useState<CuentasDemostracion>(SIN_DEMOSTRACION)

  /*
   * Por que aterrizo aqui quien venia del otro restaurante.
   *
   * Un pase rechazado -vencido, ya usado, o sin cuenta de administrador en esta
   * casa- deja al dueno frente a este formulario sin ninguna explicacion, y lo
   * que parece es que el boton del otro panel esta roto. El aviso lo deja
   * escrito el canje y se lee una sola vez.
   *
   * Se toma en el primer render y no en un efecto para que salga ya pintado:
   * un aviso que aparece medio segundo despues de la pantalla se lee como un
   * error nuevo, no como la razon de estar aqui.
   */
  const [avisoDelCruce] = useState(() => tomarAvisoDelCruce())

  // Lo decide el servidor, no el paquete compilado. Mientras la respuesta no
  // llega la pantalla se ve como la de produccion, que es como debe verse si
  // nunca llega.
  useEffect(() => {
    let vigente = true
    void api.cuentasDeDemostracion().then((respuesta) => {
      if (vigente) setDemostracion(respuesta)
    })
    return () => {
      vigente = false
    }
  }, [])

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

  const usarCuenta = (nombreDeUsuario: string) => {
    setUsuario(nombreDeUsuario)
    setClave(demostracion.clave)
    setError(null)
  }

  const hayCuentas = demostracion.activa && demostracion.cuentas.length > 0

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950 px-4 py-8">
      <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center">
        <Link
          to="/"
          className="mb-8 inline-flex items-center gap-2 self-start text-sm text-noche-400 transition hover:text-crema-100"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Volver al sitio
        </Link>

        <div className="mb-8">
          <p className="font-marca text-2xl tracking-[0.2em] text-oro-400">EL PATIO</p>
          <h1 className="mt-3 font-titulo text-3xl font-light text-crema-100">Acceso del personal</h1>
          <p className="mt-1 text-sm text-noche-400">
            {RESTAURANTE.ciudad} · Ingresa con el usuario que te asignó administración
          </p>
        </div>

        {avisoDelCruce && (
          <p
            role="status"
            className="mb-5 rounded-xl border border-estado-proceso/40 bg-estado-proceso-suave px-3.5 py-3 text-sm leading-relaxed text-crema-100"
          >
            {avisoDelCruce}
          </p>
        )}

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
          La lista con la clave escrita al lado solo aparece si el backend tiene
          encendido el modo demostracion. En un despliegue que cobra de verdad
          el endpoint responde una lista vacia y aqui no se pinta nada: una
          clave impresa en la pantalla de acceso es una clave publica, y eso solo
          es aceptable mientras el sistema se este enseñando.
        */}
        {hayCuentas ? (
          <div className="mt-10 rounded-2xl border border-noche-700 bg-noche-900 p-4">
            <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-oro-400">
              Cuentas de demostración
            </p>
            <ul className="space-y-2">
              {demostracion.cuentas.map((cuenta) => (
                <li key={cuenta.usuario}>
                  <button
                    type="button"
                    onClick={() => usarCuenta(cuenta.usuario)}
                    className="flex w-full items-center justify-between gap-3 rounded-xl border border-noche-700 bg-noche-850 px-3 py-2.5 text-left transition hover:border-oro-500/50 hover:bg-noche-800"
                  >
                    <span>
                      <span className="block text-sm font-medium text-crema-100">
                        {NOMBRE_ROL[cuenta.rol]}
                      </span>
                      <span className="block text-xs text-noche-400">{cuenta.destino}</span>
                    </span>
                    <span className="shrink-0 font-mono text-xs text-noche-300">
                      {cuenta.usuario} / {demostracion.clave}
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
        ) : (
          <div className="mt-10 rounded-2xl border border-noche-700 bg-noche-900 p-4">
            <p className="text-xs leading-relaxed text-noche-500">
              Cada pestaña del navegador mantiene su propia sesión: puedes tener al mesero en una y a
              cocina en otra al mismo tiempo. Si olvidaste tu clave, pídele a administración que te la
              cambie desde configuración.
            </p>
          </div>
        )}
      </div>

    </div>
  )
}

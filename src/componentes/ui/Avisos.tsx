import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react'

type Tono = 'exito' | 'error' | 'info'

interface Aviso {
  id: number
  mensaje: string
  tono: Tono
}

interface Valor {
  mostrar: (mensaje: string, tono?: Tono) => void
}

const Contexto = createContext<Valor | null>(null)

const ESTILOS: Record<Tono, { clase: string; icono: typeof Info }> = {
  exito: { clase: 'border-estado-listo/50 bg-estado-listo-suave text-estado-listo', icono: CheckCircle2 },
  error: { clase: 'border-estado-demorado/50 bg-estado-demorado-suave text-estado-demorado', icono: AlertTriangle },
  info: { clase: 'border-noche-600 bg-noche-800 text-crema-100', icono: Info },
}

let siguienteId = 0

/**
 * Confirmaciones de un vistazo. Cada accion del mesero responde de inmediato,
 * porque en el salon nadie se queda mirando si el toque sirvio o no.
 */
export function ProveedorAvisos({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([])

  const mostrar = useCallback((mensaje: string, tono: Tono = 'info') => {
    const id = ++siguienteId
    setAvisos((actuales) => [...actuales, { id, mensaje, tono }])
    window.setTimeout(() => setAvisos((actuales) => actuales.filter((a) => a.id !== id)), 3200)
  }, [])

  const valor = useMemo(() => ({ mostrar }), [mostrar])

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-2 z-[60] flex flex-col items-center gap-2 px-4">
        {avisos.map((aviso) => {
          const { clase, icono: Icono } = ESTILOS[aviso.tono]
          return (
            <div
              key={aviso.id}
              role="status"
              className={`flex w-full max-w-md animate-caer items-center gap-2.5 rounded-xl border px-3.5 py-2.5 text-sm font-medium shadow-lg shadow-black/40 ${clase}`}
            >
              <Icono className="h-4 w-4 shrink-0" aria-hidden />
              <span className="min-w-0">{aviso.mensaje}</span>
            </div>
          )
        })}
      </div>
    </Contexto.Provider>
  )
}

export function useAvisos(): Valor {
  const valor = useContext(Contexto)
  if (!valor) throw new Error('useAvisos debe usarse dentro de ProveedorAvisos')
  return valor
}

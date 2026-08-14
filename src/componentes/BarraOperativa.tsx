import type { ReactNode } from 'react'
import { LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useSesion } from '@/compartido/auth'
import { RESTAURANTE } from '@/compartido/config'
import { IndicadorConexion } from './IndicadorConexion'

interface Props {
  titulo: string
  subtitulo?: string
  /** Controles propios del area: pestanas, filtros, acciones. */
  acciones?: ReactNode
  mostrarConexion?: boolean
}

/** Encabezado comun de comandera, cocina y panel administrativo. */
export function BarraOperativa({ titulo, subtitulo, acciones, mostrarConexion = false }: Props) {
  const { sesion, salir } = useSesion()
  const navegar = useNavigate()

  const cerrar = () => {
    salir()
    navegar('/acceso', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 border-b border-noche-800 bg-noche-900/95 backdrop-blur">
      {/* Altura fija: las barras que se pegan debajo cuentan con ella (top-16). */}
      <div className="flex h-16 items-center gap-3 px-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h1 className="truncate text-lg font-semibold text-crema-100">{titulo}</h1>
            {mostrarConexion && <IndicadorConexion />}
          </div>
          <p className="truncate text-xs text-noche-400">
            {subtitulo ?? `${RESTAURANTE.nombre} · ${sesion?.nombre ?? ''}`}
          </p>
        </div>

        {acciones}

        <button
          type="button"
          onClick={cerrar}
          title="Cerrar sesión"
          aria-label="Cerrar sesión"
          className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl text-noche-400 transition hover:bg-noche-800 hover:text-crema-100"
        >
          <LogOut className="h-5 w-5" aria-hidden />
        </button>
      </div>
    </header>
  )
}

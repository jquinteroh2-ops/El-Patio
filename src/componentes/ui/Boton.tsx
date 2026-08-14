import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Loader2 } from 'lucide-react'

type Variante = 'principal' | 'secundario' | 'fantasma' | 'peligro' | 'exito'
type Tamano = 'normal' | 'grande' | 'compacto'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  tamano?: Tamano
  cargando?: boolean
  bloque?: boolean
  icono?: ReactNode
}

/**
 * Boton de las areas operativas. La altura minima es de 48 px porque se toca
 * de pie, con una mano y con afan.
 */
const VARIANTES: Record<Variante, string> = {
  principal: 'bg-ambar-500 text-noche-950 hover:bg-ambar-400 active:bg-ambar-600 font-semibold',
  secundario: 'bg-noche-700 text-crema-100 hover:bg-noche-600 active:bg-noche-800',
  fantasma: 'bg-transparent text-noche-300 hover:bg-noche-800 hover:text-crema-100',
  peligro: 'bg-estado-demorado/15 text-estado-demorado hover:bg-estado-demorado/25 border border-estado-demorado/40',
  exito: 'bg-estado-listo text-noche-950 hover:brightness-110 font-semibold',
}

const TAMANOS: Record<Tamano, string> = {
  compacto: 'min-h-[36px] px-3 text-sm',
  normal: 'min-h-toque px-4 text-[0.95rem]',
  grande: 'min-h-[60px] px-6 text-base',
}

export function Boton({
  variante = 'secundario',
  tamano = 'normal',
  cargando = false,
  bloque = false,
  icono,
  children,
  className = '',
  disabled,
  ...resto
}: Props) {
  return (
    <button
      {...resto}
      disabled={disabled || cargando}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-xl transition-all',
        'active:scale-[0.98] disabled:opacity-45 disabled:pointer-events-none',
        VARIANTES[variante],
        TAMANOS[tamano],
        bloque ? 'w-full' : '',
        className,
      ].join(' ')}
    >
      {cargando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : icono}
      {children}
    </button>
  )
}

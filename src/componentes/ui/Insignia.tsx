import type { ReactNode } from 'react'

type Tono = 'neutro' | 'listo' | 'proceso' | 'demorado' | 'reservada' | 'ambar'

const TONOS: Record<Tono, string> = {
  neutro: 'bg-noche-800 text-noche-300 border-noche-700',
  listo: 'bg-estado-listo/15 text-estado-listo border-estado-listo/35',
  proceso: 'bg-estado-proceso/15 text-estado-proceso border-estado-proceso/35',
  demorado: 'bg-estado-demorado/15 text-estado-demorado border-estado-demorado/35',
  reservada: 'bg-estado-reservada/15 text-estado-reservada border-estado-reservada/35',
  ambar: 'bg-ambar-500/15 text-ambar-300 border-ambar-500/35',
}

interface Props {
  tono?: Tono
  children: ReactNode
  className?: string
}

export function Insignia({ tono = 'neutro', children, className = '' }: Props) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-lg border px-2 py-0.5 text-xs font-medium ${TONOS[tono]} ${className}`}
    >
      {children}
    </span>
  )
}

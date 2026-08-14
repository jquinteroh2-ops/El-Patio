import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface Props {
  icono: LucideIcon
  titulo: string
  descripcion?: string
  accion?: ReactNode
}

export function Vacio({ icono: Icono, titulo, descripcion, accion }: Props) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <Icono className="mb-1 h-8 w-8 text-noche-600" aria-hidden />
      <p className="font-medium text-crema-100">{titulo}</p>
      {descripcion && <p className="max-w-xs text-sm text-noche-400">{descripcion}</p>}
      {accion && <div className="mt-3">{accion}</div>}
    </div>
  )
}

import { Loader2 } from 'lucide-react'

interface Props {
  mensaje?: string
  pantallaCompleta?: boolean
}

export function Cargando({ mensaje = 'Cargando', pantallaCompleta = false }: Props) {
  const contenido = (
    <div className="flex flex-col items-center gap-3 text-noche-400">
      <Loader2 className="h-6 w-6 animate-spin" aria-hidden />
      <span className="text-sm">{mensaje}</span>
    </div>
  )

  if (!pantallaCompleta) return <div className="flex justify-center py-10">{contenido}</div>

  return (
    <div className="flex min-h-dvh items-center justify-center bg-noche-950">{contenido}</div>
  )
}

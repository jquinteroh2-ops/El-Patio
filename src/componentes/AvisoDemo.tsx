import { ETIQUETA_DEMO } from '@/compartido/config'

/** Aviso discreto y permanente. Va en el pie de todas las areas. */
export function AvisoDemo({ className = '' }: { className?: string }) {
  return (
    <span className={`text-xs tracking-wide text-noche-500 ${className}`}>{ETIQUETA_DEMO}</span>
  )
}

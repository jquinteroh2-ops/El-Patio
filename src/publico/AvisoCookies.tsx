import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { decisionGuardada, guardarDecision } from '@/compartido/analitica'

/** Lo dispara el enlace «Cookies» del pie para volver a mostrar el aviso. */
export const EVENTO_ABRIR_AVISO = 'elpatio:abrir-aviso-cookies'

/**
 * El aviso de cookies del sitio publico.
 *
 * Sale una sola vez, hasta que el visitante elige; el pie tiene un enlace para
 * cambiar de opinion. Aceptar y rechazar pesan lo mismo a proposito: un «no»
 * escondido o mas dificil de tocar no es una autorizacion libre.
 *
 * Solo se monta en el sitio publico. Las pantallas del personal no lo ven ni se
 * miden: ver `analitica.ts`.
 */
export function AvisoCookies() {
  const [visible, setVisible] = useState(() => decisionGuardada() === null)

  useEffect(() => {
    const abrir = () => setVisible(true)
    window.addEventListener(EVENTO_ABRIR_AVISO, abrir)
    return () => window.removeEventListener(EVENTO_ABRIR_AVISO, abrir)
  }, [])

  if (!visible) return null

  const elegir = (decision: 'aceptada' | 'rechazada') => {
    guardarDecision(decision)
    setVisible(false)
  }

  return (
    <div
      role="region"
      aria-label="Aviso de cookies"
      className="fixed inset-x-0 bottom-0 z-50 border-t border-oro-500/25 bg-onix-900/95 shadow-[0_-12px_30px_-12px_rgba(0,0,0,0.9)] backdrop-blur"
    >
      <div className="mx-auto flex max-w-5xl flex-col gap-4 px-5 pt-4 sm:flex-row sm:items-center sm:gap-6 pb-segura">
        <p className="flex-1 text-sm leading-relaxed text-crema-100/75">
          Usamos cookies de Google Analytics para saber cuántas personas visitan el sitio y qué
          páginas les sirven. No las usamos para publicidad.{' '}
          <Link to="/politica-de-datos" className="text-oro-300 underline underline-offset-2 hover:text-oro-200">
            Política de datos
          </Link>
        </p>
        <div className="grid grid-cols-2 gap-3 sm:flex sm:shrink-0">
          <button
            type="button"
            onClick={() => elegir('rechazada')}
            className="min-h-[44px] rounded-sm border border-crema-100/30 px-5 text-xs uppercase tracking-[0.16em] text-crema-100 transition hover:border-oro-400 hover:text-oro-300"
          >
            Rechazar
          </button>
          <button
            type="button"
            onClick={() => elegir('aceptada')}
            className="min-h-[44px] rounded-sm bg-oro-500 px-5 text-xs font-semibold uppercase tracking-[0.16em] text-onix-950 transition hover:bg-oro-400"
          >
            Aceptar
          </button>
        </div>
      </div>
    </div>
  )
}

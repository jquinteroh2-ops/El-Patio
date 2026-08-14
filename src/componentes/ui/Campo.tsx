import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

const BASE =
  'w-full min-h-toque rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-crema-100 ' +
  'placeholder:text-noche-500 focus:border-ambar-500 focus:outline-none focus:ring-1 focus:ring-ambar-500/40 transition'

interface EnvoltorioProps {
  etiqueta?: string
  ayuda?: string
  error?: string
  children: ReactNode
}

function Envoltorio({ etiqueta, ayuda, error, children }: EnvoltorioProps) {
  return (
    <label className="block">
      {etiqueta && (
        <span className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-noche-400">
          {etiqueta}
        </span>
      )}
      {children}
      {error ? (
        <span className="mt-1.5 block text-sm text-estado-demorado">{error}</span>
      ) : (
        ayuda && <span className="mt-1.5 block text-sm text-noche-400">{ayuda}</span>
      )}
    </label>
  )
}

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta?: string
  ayuda?: string
  error?: string
}

export function Campo({ etiqueta, ayuda, error, className = '', ...resto }: CampoProps) {
  return (
    <Envoltorio etiqueta={etiqueta} ayuda={ayuda} error={error}>
      <input {...resto} className={`${BASE} ${error ? 'border-estado-demorado' : ''} ${className}`} />
    </Envoltorio>
  )
}

interface AreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  etiqueta?: string
  ayuda?: string
  error?: string
}

export function CampoArea({ etiqueta, ayuda, error, className = '', ...resto }: AreaProps) {
  return (
    <Envoltorio etiqueta={etiqueta} ayuda={ayuda} error={error}>
      <textarea {...resto} className={`${BASE} min-h-[96px] py-3 ${className}`} />
    </Envoltorio>
  )
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  etiqueta?: string
  ayuda?: string
  error?: string
}

export function CampoSelect({ etiqueta, ayuda, error, className = '', children, ...resto }: SelectProps) {
  return (
    <Envoltorio etiqueta={etiqueta} ayuda={ayuda} error={error}>
      <select {...resto} className={`${BASE} appearance-none pr-10 ${className}`}>
        {children}
      </select>
    </Envoltorio>
  )
}

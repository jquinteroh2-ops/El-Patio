interface Props {
  activo: boolean
  onCambiar: (activo: boolean) => void
  etiqueta: string
  /** Texto corto al lado, para no depender solo del color. */
  descripcion?: string
  deshabilitado?: boolean
}

/** Interruptor de un toque. Agotar un plato no puede costar más que esto. */
export function Interruptor({ activo, onCambiar, etiqueta, descripcion, deshabilitado }: Props) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={activo}
      aria-label={etiqueta}
      disabled={deshabilitado}
      onClick={() => onCambiar(!activo)}
      className="flex items-center gap-2.5 disabled:opacity-50"
    >
      <span
        className={`relative h-7 w-12 shrink-0 rounded-full transition-colors ${
          activo ? 'bg-estado-listo' : 'bg-noche-600'
        }`}
      >
        <span
          className={`absolute top-1 h-5 w-5 rounded-full bg-crema-50 transition-all ${
            activo ? 'left-6' : 'left-1'
          }`}
        />
      </span>
      {descripcion && (
        <span className={`text-sm ${activo ? 'text-estado-listo' : 'text-noche-400'}`}>
          {descripcion}
        </span>
      )}
    </button>
  )
}

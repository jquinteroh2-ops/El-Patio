import { CloudOff, RefreshCw, Wifi } from 'lucide-react'
import { useEstadoConexion } from '@/compartido/conexion'

/**
 * Estado de la senal en la comandera. Visible pero discreto: solo grita cuando
 * de verdad se cayo el WiFi, que es cuando el mesero necesita saberlo.
 */
export function IndicadorConexion({ className = '' }: { className?: string }) {
  const { enLinea, simulada, pendientes } = useEstadoConexion()

  if (enLinea && pendientes === 0) {
    return (
      <span
        className={`inline-flex items-center gap-1.5 text-xs text-noche-500 ${className}`}
        title="Conectado"
      >
        <Wifi className="h-3.5 w-3.5" aria-hidden />
        En línea
      </span>
    )
  }

  if (enLinea && pendientes > 0) {
    return (
      <span
        className={`inline-flex items-center gap-1.5 rounded-lg border border-estado-proceso/40 bg-estado-proceso/10 px-2 py-1 text-xs font-medium text-estado-proceso ${className}`}
      >
        <RefreshCw className="h-3.5 w-3.5 animate-spin" aria-hidden />
        Enviando {pendientes}
      </span>
    )
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-lg border border-estado-demorado/40 bg-estado-demorado/10 px-2 py-1 text-xs font-semibold text-estado-demorado ${className}`}
      title={simulada ? 'Modo sin conexión activado desde configuración' : 'El dispositivo perdió la señal'}
    >
      <CloudOff className="h-3.5 w-3.5" aria-hidden />
      Sin conexión
      {pendientes > 0 && <span className="text-estado-demorado/80">· {pendientes} en cola</span>}
    </span>
  )
}

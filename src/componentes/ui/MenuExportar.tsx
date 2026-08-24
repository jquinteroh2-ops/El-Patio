import { useEffect, useRef, useState } from 'react'
import { Download, FileSpreadsheet, FileText, Loader2 } from 'lucide-react'
import { descargarReporte } from '@/compartido/mockApi'
import { useAvisos } from './Avisos'

interface Props {
  /** El reporte a descargar: `ventas`, `productos`, `cierres`, `conciliacion`… */
  tipo: string
  desde: string
  hasta: string
  /** Filtros extra que el usuario tenga puestos en pantalla. */
  parametros?: Record<string, string | undefined>
  deshabilitado?: boolean
}

/**
 * El control de exportar, uno solo para todas las pantallas de reportes.
 *
 * Está aquí y no repetido en cada pantalla por la misma razón que el exportador
 * del backend es genérico: sin esto, la quinta pantalla acabaría con un botón
 * que descarga sin los filtros puestos, y nadie se daría cuenta hasta que las
 * cifras del archivo no cuadren con las de la pantalla.
 *
 * El botón NO es control de acceso. Quien no pueda ver el reporte tampoco puede
 * descargarlo, y eso lo comprueba el backend; esconder el botón solo evita
 * ofrecer algo que va a fallar.
 */
export function MenuExportar({ tipo, desde, hasta, parametros, deshabilitado }: Props) {
  const { mostrar } = useAvisos()
  const [abierto, setAbierto] = useState(false)
  const [bajando, setBajando] = useState<'xlsx' | 'pdf' | null>(null)
  const contenedor = useRef<HTMLDivElement>(null)

  // Cerrar al hacer clic fuera y con Escape: un menú que se queda abierto tapando
  // la tabla es más molesto que útil.
  useEffect(() => {
    if (!abierto) return
    const fuera = (e: MouseEvent) => {
      if (!contenedor.current?.contains(e.target as Node)) setAbierto(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAbierto(false)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto])

  const descargar = async (formato: 'xlsx' | 'pdf') => {
    setAbierto(false)
    setBajando(formato)
    try {
      await descargarReporte(tipo, formato, { desde, hasta, ...parametros })
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo descargar el reporte', 'error')
    } finally {
      setBajando(null)
    }
  }

  return (
    <div ref={contenedor} className="relative">
      <button
        type="button"
        disabled={deshabilitado || bajando !== null}
        onClick={() => setAbierto((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={abierto}
        className="flex min-h-[40px] items-center gap-2 rounded-xl border border-noche-700 bg-noche-900 px-3.5 text-sm font-medium text-noche-300 transition hover:bg-noche-800 disabled:opacity-50"
      >
        {bajando ? (
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        ) : (
          <Download className="h-4 w-4" aria-hidden />
        )}
        {bajando ? 'Generando…' : 'Exportar'}
      </button>

      {abierto && (
        <div
          role="menu"
          className="absolute right-0 z-30 mt-1.5 w-48 animate-caer overflow-hidden rounded-xl border border-noche-700 bg-noche-900 shadow-xl shadow-black/40"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => descargar('xlsx')}
            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-sm text-crema-100 transition hover:bg-noche-800"
          >
            <FileSpreadsheet className="h-4 w-4 text-oro-400" aria-hidden />
            Excel (.xlsx)
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => descargar('pdf')}
            className="flex w-full items-center gap-2.5 border-t border-noche-800 px-3.5 py-2.5 text-left text-sm text-crema-100 transition hover:bg-noche-800"
          >
            <FileText className="h-4 w-4 text-oro-400" aria-hidden />
            PDF
          </button>
        </div>
      )}
    </div>
  )
}

import { useState } from 'react'
import { RotateCcw, Trash2 } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import type { RegistroPapelera } from '@/compartido/mockApi'
import { formatoFechaHora } from '@/compartido/formato'
import { useSyncedState } from '@/compartido/useSyncedState'
import { Boton } from '@/componentes/ui/Boton'
import { EsqueletoTabla } from '@/componentes/ui/Esqueleto'
import { Insignia } from '@/componentes/ui/Insignia'
import { Vacio } from '@/componentes/ui/Vacio'
import { useAvisos } from '@/componentes/ui/Avisos'

/**
 * La papelera: todo lo que el administrador eliminó, con quién, cuándo y por
 * qué. Es también la auditoría, por eso nada se borra de aquí: lo restaurado
 * se queda en la lista, marcado como restaurado.
 */
export default function Papelera() {
  const { mostrar } = useAvisos()
  const [restaurando, setRestaurando] = useState<string | null>(null)

  const { datos, cargando, refrescar } = useSyncedState<RegistroPapelera[]>(
    () => api.listarPapelera(),
    [],
    [],
    ['papelera', 'pagos', 'todo'],
  )

  const restaurar = async (r: RegistroPapelera) => {
    setRestaurando(r.id)
    try {
      await api.restaurarDePapelera(r.id)
      mostrar('Venta restaurada: ya aparece otra vez en Ventas', 'exito')
      refrescar()
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo restaurar', 'error')
    } finally {
      setRestaurando(null)
    }
  }

  if (cargando) return <EsqueletoTabla filas={5} columnas={5} />

  if (datos.length === 0)
    return (
      <Vacio
        icono={Trash2}
        titulo="La papelera está vacía"
        descripcion="Cuando se elimine una venta desde Ventas, quedará aquí con su motivo y se podrá restaurar."
      />
    )

  return (
    <div className="space-y-3">
      <p className="text-sm text-noche-400">
        Lo eliminado sale de ventas, reportes y cierres, pero queda registrado aquí. Una venta de un
        turno que ya se cerró no se puede eliminar ni restaurar, para no descuadrar el cierre.
      </p>
      <div className="overflow-x-auto rounded-2xl border border-noche-800">
        <table className="w-full min-w-[52rem] text-sm">
          <thead className="bg-noche-900 text-left text-xs uppercase tracking-wide text-noche-400">
            <tr>
              <th className="px-3 py-2.5 font-medium">Venta</th>
              <th className="px-3 py-2.5 font-medium">Motivo</th>
              <th className="px-3 py-2.5 font-medium">Eliminada</th>
              <th className="px-3 py-2.5 font-medium">Estado</th>
              <th className="px-3 py-2.5" />
            </tr>
          </thead>
          <tbody className="divide-y divide-noche-800">
            {datos.map((r) => (
              <tr key={r.id} className="align-top">
                <td className="px-3 py-2.5 text-crema-100">{r.resumen}</td>
                <td className="max-w-xs px-3 py-2.5 text-noche-300">{r.motivo}</td>
                <td className="px-3 py-2.5 text-noche-300">
                  {formatoFechaHora(r.eliminadoEn)}
                  <span className="block text-xs text-noche-500">por {r.eliminadoPor}</span>
                </td>
                <td className="px-3 py-2.5">
                  {r.restauradoEn ? (
                    <>
                      <Insignia tono="listo">Restaurada</Insignia>
                      <span className="mt-1 block text-xs text-noche-500">
                        {formatoFechaHora(r.restauradoEn)} por {r.restauradoPor}
                      </span>
                    </>
                  ) : (
                    <Insignia tono="demorado">Eliminada</Insignia>
                  )}
                </td>
                <td className="px-3 py-2.5 text-right">
                  {!r.restauradoEn && (
                    <Boton
                      variante="secundario"
                      tamano="compacto"
                      cargando={restaurando === r.id}
                      icono={<RotateCcw className="h-4 w-4" aria-hidden />}
                      onClick={() => restaurar(r)}
                    >
                      Restaurar
                    </Boton>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

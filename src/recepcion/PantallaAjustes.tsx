import * as api from '@/compartido/mockApi'
import { useSyncedState } from '@/compartido/useSyncedState'
import type { Ajustes } from '@/compartido/tipos'
import { BarraOperativa } from '@/componentes/BarraOperativa'
import { FichaDelSitio } from '@/admin/FichaDelSitio'
import { ZonasDomicilio } from '@/admin/ZonasDomicilio'
import Institucional from '@/admin/Institucional'
import { BotonSonido } from './BotonSonido'
import { PestanasRecepcion } from './PestanasRecepcion'

/**
 * Lo que el mostrador puede cambiar del sitio público.
 *
 * Recepción es quien contesta «¿hasta qué hora abren hoy?» y quien primero se
 * entera de que el horario publicado quedó viejo. Hacerla pedirle el cambio a
 * administración es lo que hace que el sitio muestre un horario equivocado toda
 * una semana.
 *
 * Son los mismos editores que ve el panel administrativo, montados aquí sin lo
 * que no es del mostrador: nada de cuentas del personal, mesas ni impuesto al
 * consumo. Esa separación es el motivo de que esto sea una pantalla propia y no
 * un permiso más sobre /admin/configuracion.
 */
export default function PantallaAjustes() {
  const { datos: ajustes } = useSyncedState<Ajustes | null>(
    () => api.obtenerAjustes(),
    null,
    [],
    ['ajustes', 'todo'],
  )

  return (
    <div className="flex min-h-dvh flex-col bg-noche-950">
      <BarraOperativa
        titulo="Ajustes"
        subtitulo="Horario, contacto y canal de pedidos"
        mostrarConexion
        acciones={
          <div className="flex items-center gap-1.5">
            <PestanasRecepcion activa="ajustes" />
            <BotonSonido />
          </div>
        }
      />

      <main className="flex-1 px-3 py-4 sm:px-4">
        <div className="mx-auto grid max-w-5xl gap-4">
          <FichaDelSitio />

          {/* Pausar el canal es lo primero que se busca un viernes a las nueve. */}
          {ajustes && <ZonasDomicilio ajustes={ajustes} />}

          <section className="rounded-2xl border border-noche-800 bg-noche-900 p-4">
            <h2 className="mb-3 text-sm font-semibold text-crema-100">Textos del sitio</h2>
            <Institucional />
          </section>
        </div>
      </main>
    </div>
  )
}

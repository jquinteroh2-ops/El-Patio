import { BarraOperativa } from '@/componentes/BarraOperativa'
import PanelReservas from '@/admin/Reservas'
import { BotonSonido } from './BotonSonido'
import { PestanasRecepcion } from './PestanasRecepcion'

/**
 * Reservas en el mostrador.
 *
 * Es el mismo panel que ve administracion, con la cabecera de recepcion
 * alrededor: quien responde una solicitud hace lo mismo desde los dos lados y
 * no tiene por que aprender dos pantallas distintas para la misma tarea. Ahi
 * dentro esta tambien el boton para anotar la reserva que pidieron por
 * WhatsApp o por telefono, que son la mayoria.
 */
export default function PantallaReservas() {
  return (
    <div className="flex min-h-dvh flex-col bg-noche-950">
      <BarraOperativa
        titulo="Reservas"
        subtitulo="Del sitio, de WhatsApp y del teléfono"
        mostrarConexion
        acciones={
          <div className="flex items-center gap-1.5">
            <PestanasRecepcion activa="reservas" />
            <BotonSonido />
          </div>
        }
      />

      <main className="flex-1 px-3 py-4 sm:px-4">
        <PanelReservas />
      </main>
    </div>
  )
}

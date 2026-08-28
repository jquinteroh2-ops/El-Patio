import { Bell, BellOff } from 'lucide-react'
import { Boton } from '@/componentes/ui/Boton'
import { useAvisos } from '@/componentes/ui/Avisos'
import { useSonidoRecepcion } from './sonido'

/**
 * Enciende o apaga el aviso sonoro del mostrador, el de pedidos y el de
 * reservas a la vez. Va en la cabecera de las dos pantallas de recepcion, pero
 * el interruptor es uno solo.
 */
export function BotonSonido() {
  const { mostrar } = useAvisos()
  const { activo, alternar } = useSonidoRecepcion()

  const pulsar = async () => {
    const sonando = await alternar()
    if (!activo && !sonando) mostrar('El navegador no dejó activar el sonido', 'error')
  }

  return (
    <Boton
      variante="fantasma"
      tamano="compacto"
      onClick={pulsar}
      aria-label={activo ? 'Silenciar avisos' : 'Activar aviso sonoro'}
      icono={
        activo ? (
          <Bell className="h-4 w-4 text-oro-400" aria-hidden />
        ) : (
          <BellOff className="h-4 w-4" aria-hidden />
        )
      }
    >
      {activo ? '' : 'Activar sonido'}
    </Boton>
  )
}

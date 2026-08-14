import { useEffect, useState } from 'react'
import { Copy, ExternalLink, MessageCircle } from 'lucide-react'
import { enlaceWhatsApp } from '@/compartido/whatsapp'
import { formatoTelefono } from '@/compartido/formato'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { useAvisos } from '@/componentes/ui/Avisos'

interface Props {
  abierto: boolean
  titulo: string
  telefono: string
  mensaje: string
  onCerrar: () => void
}

/**
 * Vista previa del mensaje antes de abrir WhatsApp.
 *
 * El texto es editable a proposito: nada sale a nombre del restaurante sin que
 * alguien lo lea primero, y siempre hay un caso que pide una palabra distinta.
 */
export function ModalWhatsApp({ abierto, titulo, telefono, mensaje, onCerrar }: Props) {
  const { mostrar } = useAvisos()
  const [texto, setTexto] = useState(mensaje)

  useEffect(() => {
    if (abierto) setTexto(mensaje)
  }, [abierto, mensaje])

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(texto)
      mostrar('Mensaje copiado', 'exito')
    } catch {
      mostrar('El navegador no permitió copiar', 'error')
    }
  }

  return (
    <HojaInferior
      abierta={abierto}
      titulo={titulo}
      descripcion={`Se enviará a ${formatoTelefono(telefono)}`}
      onCerrar={onCerrar}
      pie={
        <div className="flex gap-2">
          <Boton icono={<Copy className="h-4 w-4" />} onClick={copiar}>
            Copiar
          </Boton>
          <a
            href={enlaceWhatsApp(telefono, texto)}
            target="_blank"
            rel="noopener noreferrer"
            onClick={onCerrar}
            className="flex min-h-[60px] flex-1 items-center justify-center gap-2 rounded-xl bg-estado-listo px-6 text-base font-semibold text-noche-950 transition hover:brightness-110 active:scale-[0.98]"
          >
            <MessageCircle className="h-5 w-5" aria-hidden />
            Abrir WhatsApp
            <ExternalLink className="h-4 w-4 opacity-70" aria-hidden />
          </a>
        </div>
      }
    >
      <div className="space-y-2">
        <p className="text-xs text-noche-400">
          Revisa el mensaje y ajústalo si hace falta. Se abre WhatsApp con el texto ya escrito.
        </p>
        <textarea
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          rows={9}
          className="w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 py-3 text-sm leading-relaxed text-crema-100 focus:border-ambar-500 focus:outline-none"
        />
      </div>
    </HojaInferior>
  )
}

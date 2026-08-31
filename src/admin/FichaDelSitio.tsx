import { useState } from 'react'
import { Clock, MapPin, Plus, Save, Trash2 } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { useFichaSitio } from '@/compartido/sitio'
import type { FichaSitio, FranjaHorario } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Campo } from '@/componentes/ui/Campo'
import { useAvisos } from '@/componentes/ui/Avisos'

/**
 * Horario de atención y datos de contacto del sitio público.
 *
 * Esto estaba escrito a mano en el código: cambiar el horario de una temporada
 * o corregir un dígito del teléfono costaba un despliegue. Se edita desde aquí
 * y el sitio cambia sin recargar.
 *
 * Se monta en dos sitios con la misma forma —el panel administrativo y los
 * ajustes del mostrador— porque quien primero se entera de que el horario
 * publicado quedó viejo es quien contesta el teléfono.
 *
 * <p>El horario es texto libre y no una lista de días con horas. Lo que se
 * pinta es una frase que el cliente lee de un vistazo, y «Viernes y Sábado» no
 * se vuelve a componer así desde días sueltos sin inventar reglas de redacción.
 */

const FRANJA_VACIA: FranjaHorario = { dias: '', horas: '' }

export function FichaDelSitio() {
  const { mostrar } = useAvisos()
  const guardada = useFichaSitio()

  // `borrador` en null significa «lo que está en pantalla es lo guardado». En
  // cuanto alguien escribe pasa a ser suyo y deja de pisarse con lo que llegue
  // del servidor: nada peor que perder media frase porque otra pestaña guardó.
  const [borrador, setBorrador] = useState<FichaSitio | null>(null)
  const [guardando, setGuardando] = useState(false)

  const ficha = borrador ?? guardada

  const cambiar = (campos: Partial<FichaSitio>) =>
    setBorrador({ ...ficha, ...campos })

  const cambiarFranja = (indice: number, campos: Partial<FranjaHorario>) =>
    cambiar({
      horario: ficha.horario.map((f, i) => (i === indice ? { ...f, ...campos } : f)),
    })

  const guardar = async () => {
    setGuardando(true)
    try {
      await api.guardarFichaSitio(ficha)
      setBorrador(null)
      mostrar('Horario y contacto actualizados en el sitio', 'exito')
    } catch (e) {
      mostrar(e instanceof Error ? e.message : 'No se pudo guardar', 'error')
    } finally {
      setGuardando(false)
    }
  }

  const hayCambios = borrador !== null

  return (
    <section className="revelar-corto rounded-2xl border border-noche-800 bg-noche-900 p-4 lg:col-span-2">
      <h2 className="mb-1 flex items-center gap-2 text-sm font-semibold text-crema-100">
        <Clock className="h-4 w-4" aria-hidden />
        Horario y contacto del sitio
      </h2>
      <p className="mb-4 text-xs leading-relaxed text-noche-400">
        Es lo que el cliente lee en la página de inicio y en el pie del sitio. Los cambios se ven
        de inmediato, sin desplegar nada.
      </p>

      {/* ---------- Horario ---------- */}
      <div className="mb-5">
        <div className="mb-2 flex items-center justify-between gap-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-noche-400">
            Cuándo abrimos
          </h3>
          <Boton
            variante="secundario"
            tamano="compacto"
            onClick={() => cambiar({ horario: [...ficha.horario, FRANJA_VACIA] })}
            icono={<Plus className="h-4 w-4" aria-hidden />}
          >
            Agregar franja
          </Boton>
        </div>

        {ficha.horario.length === 0 ? (
          <p className="rounded-xl border border-noche-700 bg-noche-850 px-3 py-2.5 text-sm text-noche-400">
            Sin horario publicado. La sección «Cuándo abrimos» no se pinta en el sitio.
          </p>
        ) : (
          <ul className="space-y-2">
            {ficha.horario.map((franja, i) => (
              <li key={i} className="flex items-end gap-2">
                <div className="flex-1">
                  <Campo
                    etiqueta={i === 0 ? 'Días' : undefined}
                    value={franja.dias}
                    onChange={(e) => cambiarFranja(i, { dias: e.target.value })}
                    placeholder="Martes a Jueves"
                  />
                </div>
                <div className="flex-1">
                  <Campo
                    etiqueta={i === 0 ? 'Horas' : undefined}
                    value={franja.horas}
                    onChange={(e) => cambiarFranja(i, { horas: e.target.value })}
                    placeholder="12:00 m. – 10:00 p. m."
                  />
                </div>
                <button
                  type="button"
                  onClick={() => cambiar({ horario: ficha.horario.filter((_, j) => j !== i) })}
                  aria-label={`Quitar la franja de ${franja.dias || 'sin días'}`}
                  className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl border border-noche-700 text-noche-500 transition hover:border-estado-demorado/40 hover:text-estado-demorado"
                >
                  <Trash2 className="h-4 w-4" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        )}

        <p className="mt-2 text-xs text-noche-500">
          Se escribe como se diría por teléfono. Una franja que diga «Cerrado» sale en gris.
        </p>
      </div>

      {/* ---------- Contacto ---------- */}
      <div className="mb-4">
        <h3 className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-noche-400">
          <MapPin className="h-3.5 w-3.5" aria-hidden />
          Cómo nos encuentran
        </h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo
            etiqueta="Dirección"
            value={ficha.direccion}
            onChange={(e) => cambiar({ direccion: e.target.value })}
            placeholder="Calle 26 #31-2"
          />
          <Campo
            etiqueta="Ciudad"
            value={ficha.ciudad}
            onChange={(e) => cambiar({ ciudad: e.target.value })}
            placeholder="Turbaco, Bolívar"
          />
          <Campo
            etiqueta="Teléfono"
            value={ficha.telefono}
            onChange={(e) => cambiar({ telefono: e.target.value })}
            placeholder="+57 304 403 2936"
            ayuda="Como se lee en la página."
          />
          <Campo
            etiqueta="WhatsApp"
            value={ficha.whatsapp}
            onChange={(e) => cambiar({ whatsapp: e.target.value })}
            inputMode="numeric"
            placeholder="573044032936"
            ayuda="Con indicativo y sin espacios: es el número al que abre el chat."
          />
          <Campo
            etiqueta="Instagram"
            value={ficha.instagram}
            onChange={(e) => cambiar({ instagram: e.target.value })}
            placeholder="elpatiorestaurante_turbaco"
            ayuda="Solo el usuario, sin arroba."
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Boton
          variante="principal"
          cargando={guardando}
          disabled={!hayCambios}
          onClick={guardar}
          icono={<Save className="h-4 w-4" aria-hidden />}
        >
          Guardar y publicar
        </Boton>
        {hayCambios && (
          <Boton variante="fantasma" onClick={() => setBorrador(null)}>
            Descartar
          </Boton>
        )}
      </div>
    </section>
  )
}

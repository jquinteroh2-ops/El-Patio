import { useEffect, useState } from 'react'
import * as api from '@/compartido/mockApi'
import type { ContenidoInstitucional } from '@/compartido/mockApi'
import { formatoFechaLarga } from '@/compartido/formato'
import { useFichaSitio } from '@/compartido/sitio'
import { Cargando } from '@/componentes/ui/Cargando'

/**
 * La política de tratamiento de datos personales.
 *
 * Existe porque los formularios del sitio tienen que enlazarla: la Ley 1581 de
 * 2012 exige que quien autoriza pueda leer a qué está autorizando.
 *
 * El texto no está aquí: es el bloque `politica-datos` del contenido
 * institucional y el restaurante lo edita en Panel → Institucional, sin
 * desplegar nada. Es texto plano: una línea que empieza por «# » es un
 * subtítulo y una línea en blanco separa párrafos. Nada de HTML, por la misma
 * razón que en «Quiénes somos»: lo escribe una persona desde un formulario.
 */
export default function PoliticaDatos() {
  const ficha = useFichaSitio()
  const [politica, setPolitica] = useState<ContenidoInstitucional | null | undefined>(undefined)

  useEffect(() => {
    void api
      .contenidoInstitucional()
      .then((bloques) => setPolitica(bloques.find((b) => b.clave === api.CLAVE_POLITICA_DATOS) ?? null))
      .catch(() => setPolitica(null))
  }, [])

  if (politica === undefined) return <Cargando mensaje="Cargando la política" />

  // Oculta desde el panel, o el servidor no contesta. Una página en blanco
  // parecería un error; se dice qué pasa y por dónde preguntar.
  if (politica === null) {
    return (
      <div className="mx-auto max-w-2xl px-5 py-16 text-center">
        <h1 className="font-titulo text-3xl text-crema-100 sm:text-4xl">
          Política de tratamiento de datos personales
        </h1>
        <p className="mt-6 text-sm leading-relaxed text-crema-100/70">
          En este momento no podemos mostrar la política. Si necesita consultarla, comuníquese al{' '}
          <span className="text-oro-300">{ficha.telefono}</span>.
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-5 py-12 sm:py-16">
      <h1 className="font-titulo text-3xl text-crema-100 sm:text-4xl">{politica.titulo}</h1>
      <p className="mt-2 text-sm text-crema-100/50">
        Actualizada el {formatoFechaLarga(politica.actualizadoEn)}
      </p>

      <div className="mt-8 space-y-4 text-sm leading-relaxed text-crema-100/75">
        {bloquesDe(politica.cuerpo).map((bloque, i) =>
          bloque.tipo === 'titulo' ? (
            <h2 key={i} className="pt-4 font-titulo text-xl text-crema-100">
              {bloque.texto}
            </h2>
          ) : (
            <p key={i} className="whitespace-pre-line">
              {bloque.texto}
            </p>
          ),
        )}
      </div>
    </div>
  )
}

type Bloque = { tipo: 'titulo' | 'parrafo'; texto: string }

/**
 * Parte el texto en subtítulos y párrafos.
 *
 * «# » al empezar una línea la vuelve subtítulo; las líneas seguidas forman un
 * párrafo, y una línea en blanco lo cierra. Es todo el formato que se admite.
 */
function bloquesDe(cuerpo: string): Bloque[] {
  const bloques: Bloque[] = []
  let parrafo: string[] = []
  const cerrar = () => {
    if (parrafo.length > 0) bloques.push({ tipo: 'parrafo', texto: parrafo.join('\n') })
    parrafo = []
  }
  for (const linea of cuerpo.split(/\r?\n/)) {
    const limpia = linea.trim()
    if (limpia.startsWith('# ')) {
      cerrar()
      bloques.push({ tipo: 'titulo', texto: limpia.slice(2).trim() })
    } else if (limpia === '') {
      cerrar()
    } else {
      parrafo.push(limpia)
    }
  }
  cerrar()
  return bloques
}

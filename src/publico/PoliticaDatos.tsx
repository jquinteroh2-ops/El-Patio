import { AlertTriangle } from 'lucide-react'
import { DATOS_FISCALES, RESTAURANTE } from '@/compartido/config'

/**
 * La política de tratamiento de datos personales.
 *
 * Existe porque el formulario de «Trabaja con nosotros» tiene que enlazarla:
 * la Ley 1581 de 2012 exige que quien autoriza pueda leer a qué está
 * autorizando, y una casilla que enlaza a una página que no existe es una
 * autorización que no se sostiene.
 *
 * EL TEXTO ES UN BORRADOR y lo dice en la propia página. No lo escribió un
 * abogado y el restaurante tiene que reemplazarlo por su política real antes de
 * salir a producción. Se deja visible el aviso a propósito: una política
 * inventada que parezca definitiva es peor que no tener ninguna, porque nadie
 * la revisaría.
 */
export default function PoliticaDatos() {
  return (
    <div className="mx-auto max-w-2xl px-5 py-12 sm:py-16">
      <h1 className="font-titulo text-3xl text-crema-100 sm:text-4xl">
        Política de tratamiento de datos personales
      </h1>
      <p className="mt-2 text-sm text-crema-100/50">
        {RESTAURANTE.nombreCompleto} · NIT {DATOS_FISCALES.nitCompleto}
      </p>

      <div className="mt-6 flex items-start gap-3 rounded-sm border border-oro-500/35 bg-oro-500/10 p-4 text-sm text-oro-200">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <p>
          <strong className="font-semibold">Este texto es un borrador.</strong> El restaurante
          debe reemplazarlo por su política revisada antes de recibir hojas de vida
          reales. No lo redactó un abogado.
        </p>
      </div>

      <div className="mt-8 space-y-6 text-sm leading-relaxed text-crema-100/75">
        <section>
          <h2 className="mb-2 font-titulo text-xl text-crema-100">Quién responde por sus datos</h2>
          <p>
            {RESTAURANTE.nombreCompleto}, con NIT {DATOS_FISCALES.nitCompleto} y domicilio en{' '}
            {RESTAURANTE.direccion}, {RESTAURANTE.ciudad}, es el responsable del tratamiento de los
            datos personales que usted entregue a través de este sitio.
          </p>
        </section>

        <section>
          <h2 className="mb-2 font-titulo text-xl text-crema-100">Para qué los usamos</h2>
          <p>
            Los datos que se envían por el formulario de «Trabaja con nosotros» —nombre,
            documento de identidad, teléfono, correo electrónico y hoja de vida— se usan
            únicamente para evaluar su candidatura a una vacante y para comunicarnos con
            usted durante ese proceso. No se venden, no se comparten con terceros con fines
            comerciales y no se usan para enviarle publicidad.
          </p>
        </section>

        <section>
          <h2 className="mb-2 font-titulo text-xl text-crema-100">Cuánto tiempo los guardamos</h2>
          <p>
            Conservamos su hoja de vida mientras el proceso de selección esté abierto y por
            un periodo razonable después, por si surge una vacante que encaje con su perfil.
            Usted puede pedir que la eliminemos en cualquier momento.
          </p>
        </section>

        <section>
          <h2 className="mb-2 font-titulo text-xl text-crema-100">Sus derechos</h2>
          <p>
            La Ley 1581 de 2012 le da derecho a conocer, actualizar y rectificar sus datos,
            a solicitar prueba de la autorización que otorgó, a ser informado sobre el uso
            que se les ha dado, a presentar quejas ante la Superintendencia de Industria y
            Comercio y a solicitar la supresión de sus datos.
          </p>
          <p className="mt-3">
            Para ejercer cualquiera de estos derechos escríbanos a{' '}
            <span className="text-oro-300">{DATOS_FISCALES.correo}</span> o comuníquese al{' '}
            <span className="text-oro-300">{RESTAURANTE.telefono}</span>. Atendemos su
            solicitud en los términos que fija la ley.
          </p>
        </section>

        <section>
          <h2 className="mb-2 font-titulo text-xl text-crema-100">Seguridad</h2>
          <p>
            Su hoja de vida se guarda en un almacenamiento al que solo accede la
            administración del restaurante, mediante una sesión autenticada. No queda
            publicada en ninguna dirección accesible desde internet.
          </p>
        </section>
      </div>
    </div>
  )
}

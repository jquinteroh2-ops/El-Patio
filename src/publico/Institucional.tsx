import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as api from '@/compartido/mockApi'
import type { ContenidoInstitucional } from '@/compartido/mockApi'
import { Filete } from './Ornamento'

/**
 * La sección institucional del sitio: quiénes somos, misión, visión, valores.
 *
 * El texto no está aquí: viene de la base y lo edita el restaurante desde el
 * panel. Este componente solo sabe pintarlo.
 *
 * Si no hay nada visible —porque el restaurante todavía no escribió su texto, o
 * porque el servidor no contesta— NO se pinta la sección. Es deliberado: una
 * sección con títulos y párrafos vacíos se ve como un error del sitio, y es
 * peor que no tenerla.
 */
export default function Institucional() {
  const [bloques, setBloques] = useState<ContenidoInstitucional[]>([])

  useEffect(() => {
    void api.contenidoInstitucional().then(setBloques).catch(() => setBloques([]))
  }, [])

  if (bloques.length === 0) return null

  const [principal, ...resto] = bloques

  return (
    <section id="quienes-somos" className="border-t border-oro-500/15 bg-onix-900/40">
      <div className="mx-auto max-w-3xl px-5 py-20">
        {/* El primer bloque manda: es «quiénes somos» y lleva el peso visual.
            Los demás van debajo, más discretos, para que la sección tenga una
            entrada clara en vez de cuatro títulos del mismo tamaño compitiendo. */}
        <header className="text-center">
          <p className="text-[0.7rem] uppercase tracking-[0.35em] text-oro-400">El Patio</p>
          <h2 className="mt-4 font-titulo text-4xl font-light leading-tight text-crema-100 sm:text-5xl">
            {principal.titulo}
          </h2>
          <Filete className="mx-auto mt-6" />
        </header>

        {/* `whitespace-pre-line` respeta los saltos de línea que escribió el
            dueño. Es todo el formato que hace falta: el cuerpo es texto plano y
            no HTML, justamente para que nadie pueda meter un script por aquí. */}
        <p className="mt-8 whitespace-pre-line text-base leading-relaxed text-crema-100/75">
          {principal.cuerpo}
        </p>

        {resto.length > 0 && (
          <div className="mt-16 grid gap-10 sm:grid-cols-2">
            {resto.map((bloque) => (
              <article key={bloque.clave}>
                <h3 className="font-titulo text-2xl font-light text-oro-300">{bloque.titulo}</h3>
                <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-crema-100/70">
                  {bloque.cuerpo}
                </p>
              </article>
            ))}
          </div>
        )}

        {/* ---------- El CTA de reclutamiento ----------
            Cierra la sección institucional y no el sitio entero: quien acaba de
            leer quiénes son y qué valores tienen es exactamente quien puede
            querer trabajar ahí. En el encabezado compite con reservar; aquí no
            compite con nada. */}
        <div className="mt-16 border-t border-oro-500/15 pt-10 text-center">
          <p className="text-sm leading-relaxed text-crema-100/60">
            ¿Le gustaría hacer parte del equipo?
          </p>
          <Link
            to="/trabaja-con-nosotros"
            className="mt-5 inline-flex min-h-toque items-center rounded-sm border border-oro-400 px-6 text-sm uppercase tracking-[0.16em] text-oro-300 transition hover:bg-oro-500/10"
          >
            Trabaja con nosotros
          </Link>
        </div>
      </div>
    </section>
  )
}

import { useRef, useState, type FormEvent, type PointerEvent, type ReactNode } from 'react'
import {
  motion,
  useMotionValue,
  useScroll,
  useSpring,
  useTransform,
} from 'motion/react'
import { ArrowDown, Cake, CalendarCheck, Gift, MessageCircle, Sparkles, Star } from 'lucide-react'
import { enlaceWhatsApp } from '@/compartido/whatsapp'

/**
 * Las secciones animadas de la portada: la portada con fotos que se mueven,
 * la cinta, los platos en movimiento, el collage del local y la tarjeta de
 * fidelidad.
 *
 * Las fotos viven en `public/img/inicio/`. Para cambiarlas basta con poner otra
 * foto con el MISMO nombre de archivo en esa carpeta: no hay que tocar código.
 */

const F = '/img/inicio/'
const SUAVE = [0.16, 1, 0.3, 1] as const

// ---------------------------------------------------------------------------
// Portada
// ---------------------------------------------------------------------------

/**
 * Videos del restaurante real, en `public/video/`. Son verticales, como se
 * graban con el celular: en el teléfono se ve uno a pantalla completa y en el
 * computador los tres lado a lado, como un tríptico.
 */
const V = '/video/'
const VIDEOS = [
  { nombre: 'portada-patio', pie: 'El patio' },
  { nombre: 'portada-salon', pie: 'El salón' },
  { nombre: 'portada-cocina', pie: 'Cocina y barra' },
]

function VideoDeFondo({ nombre, className = '' }: { nombre: string; className?: string }) {
  return (
    <video
      className={`h-full w-full object-cover ${className}`}
      src={`${V}${nombre}.mp4`}
      poster={`${V}${nombre}.jpg`}
      autoPlay
      muted
      loop
      playsInline
      preload="auto"
      aria-hidden
    />
  )
}

export function PortadaViva({
  ciudad,
  lema,
  texto,
  acciones,
  franja,
}: {
  ciudad: string
  lema: string
  texto: string
  acciones: ReactNode
  franja: ReactNode
}) {
  const ref = useRef<HTMLElement>(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start start', 'end start'] })
  const yTexto = useTransform(scrollYProgress, [0, 1], [0, 160])
  const opacidad = useTransform(scrollYProgress, [0, 0.75], [1, 0])
  const escalaVideo = useTransform(scrollYProgress, [0, 1], [1, 1.12])

  return (
    <section ref={ref} className="relative overflow-hidden bg-onix-950">
      {/* Celular: un video a pantalla completa. */}
      <motion.div style={{ scale: escalaVideo }} className="absolute inset-0 lg:hidden">
        <VideoDeFondo nombre={VIDEOS[0].nombre} />
      </motion.div>

      {/* Computador: tres videos lado a lado que se destapan de abajo arriba,
          uno tras otro. */}
      <motion.div style={{ scale: escalaVideo }} className="absolute inset-0 hidden grid-cols-3 gap-1 lg:grid">
        {VIDEOS.map((v, k) => (
          <motion.div
            key={v.nombre}
            className="relative overflow-hidden"
            initial={{ clipPath: 'inset(100% 0 0 0)' }}
            animate={{ clipPath: 'inset(0% 0 0 0)' }}
            transition={{ duration: 1.4, delay: 0.15 + k * 0.18, ease: [0.76, 0, 0.24, 1] }}
          >
            <VideoDeFondo nombre={v.nombre} />
            <span className="absolute bottom-28 right-5 text-[0.6rem] font-semibold uppercase tracking-[0.3em] text-crema-100/70">
              {v.pie}
            </span>
          </motion.div>
        ))}
      </motion.div>

      <div className="absolute inset-0 bg-gradient-to-t from-onix-950 via-onix-950/35 to-onix-950/25" />
      <div className="absolute inset-0 bg-gradient-to-r from-onix-950/85 via-onix-950/30 to-transparent" />

      <motion.div
        style={{ y: yTexto, opacity: opacidad }}
        className="relative mx-auto grid min-h-[calc(100svh-4rem-5rem)] max-w-6xl content-center gap-12 px-5 pb-16 pt-14 sm:px-6 lg:grid-cols-[1.15fr_1fr] lg:items-center lg:gap-16"
      >
        <div className="flex flex-col items-center text-center lg:items-start lg:text-left">
          <motion.p
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2, duration: 0.8 }}
            className="inline-flex items-center gap-3 text-[0.7rem] uppercase tracking-[0.4em] text-oro-300"
          >
            <span className="h-px w-8 bg-oro-400/60" aria-hidden />
            {ciudad}
          </motion.p>

          {/* Cada letra sube desde detrás de una máscara. */}
          <h1
            aria-label="El Patio"
            className="mt-5 font-marca text-6xl font-normal tracking-[0.12em] text-crema-50 sm:text-8xl"
          >
            {[...'EL PATIO'].map((letra, k) => (
              <span key={k} aria-hidden className="inline-block overflow-hidden pb-[0.06em] align-bottom">
                <motion.span
                  className={letra === ' ' ? 'inline-block w-[0.35em]' : 'inline-block'}
                  initial={{ y: '110%' }}
                  animate={{ y: 0 }}
                  transition={{ delay: 0.35 + k * 0.06, duration: 1.1, ease: SUAVE }}
                >
                  {letra === ' ' ? ' ' : letra}
                </motion.span>
              </span>
            ))}
          </h1>

          <p className="mt-5 font-titulo text-2xl font-light italic leading-snug text-oro-200 sm:text-3xl">
            {lema.split(' ').map((palabra, k) => (
              <span key={k} className="inline-block overflow-hidden align-bottom">
                <motion.span
                  className="mr-[0.25em] inline-block"
                  initial={{ y: '110%' }}
                  animate={{ y: 0 }}
                  transition={{ delay: 0.9 + k * 0.07, duration: 1, ease: SUAVE }}
                >
                  {palabra}
                </motion.span>
              </span>
            ))}
          </p>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 1.3, duration: 0.8 }}
            className="mt-7 max-w-md text-base leading-relaxed text-crema-100/75"
          >
            {texto}
          </motion.p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.8, duration: 1, ease: SUAVE }}
          className="grid gap-3"
        >
          {acciones}
        </motion.div>
      </motion.div>

      <motion.a
        href="#club"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 2.2 }}
        className="absolute bottom-24 left-1/2 hidden -translate-x-1/2 flex-col items-center gap-2 text-[0.6rem] font-semibold uppercase tracking-[0.35em] text-crema-200 xl:flex"
      >
        Descubra
        <ArrowDown className="h-4 w-4 animate-bounce" aria-hidden />
      </motion.a>

      <div className="relative">{franja}</div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Cinta que corre sola
// ---------------------------------------------------------------------------

const CINTA = [
  'Cocina de fusión',
  'Coctelería de autor',
  'Viernes de cocina en vivo',
  'Terraza al aire libre',
  'Comedores privados',
  'Domicilios y para llevar',
  'Club El Patio',
]

export function Cinta() {
  return (
    <div className="relative overflow-hidden border-y border-oro-500/15 bg-onix-900 py-5">
      <motion.div
        className="flex w-max gap-12 whitespace-nowrap"
        animate={{ x: ['0%', '-50%'] }}
        transition={{ duration: 40, ease: 'linear', repeat: Infinity }}
      >
        {[...CINTA, ...CINTA].map((t, k) => (
          <span
            key={k}
            className="flex items-center gap-12 font-titulo text-2xl font-light italic text-crema-100 sm:text-3xl"
          >
            {t}
            <Sparkles className="h-4 w-4 text-oro-400/70" aria-hidden />
          </span>
        ))}
      </motion.div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Encabezado de sección
// ---------------------------------------------------------------------------

function Titulo({ ante, children, centro = false }: { ante: string; children: ReactNode; centro?: boolean }) {
  return (
    <div className={centro ? 'text-center' : ''}>
      <p
        className={`inline-flex items-center gap-3 text-[0.7rem] font-semibold uppercase tracking-[0.35em] text-oro-400 ${
          centro ? 'justify-center' : ''
        }`}
      >
        <span className="h-px w-8 bg-oro-500/50" aria-hidden />
        {ante}
      </p>
      <h2 className="mt-4 font-titulo text-[clamp(2.4rem,5vw,4rem)] font-light leading-[1.05] text-crema-100">
        {children}
      </h2>
    </div>
  )
}

const It = ({ children }: { children: ReactNode }) => <span className="italic text-oro-300">{children}</span>

/** Aparece subiendo cuando entra en pantalla. */
function Revelar({ children, retraso = 0, className = '' }: { children: ReactNode; retraso?: number; className?: string }) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 36 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-10%' }}
      transition={{ duration: 0.9, delay: retraso, ease: SUAVE }}
    >
      {children}
    </motion.div>
  )
}

/** Foto que se destapa de abajo hacia arriba mientras se aleja. */
function ImagenRevelada({ src, alt = '', className = '', retraso = 0 }: { src: string; alt?: string; className?: string; retraso?: number }) {
  return (
    <motion.div
      className={`relative overflow-hidden rounded-[1.75rem] bg-onix-800 ${className}`}
      initial={{ clipPath: 'inset(100% 0 0 0)' }}
      whileInView={{ clipPath: 'inset(0% 0 0 0)' }}
      viewport={{ once: true, margin: '-10%' }}
      transition={{ duration: 1.2, delay: retraso, ease: [0.76, 0, 0.24, 1] }}
    >
      <motion.img
        src={src}
        alt={alt}
        loading="lazy"
        className="h-full w-full object-cover"
        initial={{ scale: 1.3 }}
        whileInView={{ scale: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 1.8, delay: retraso, ease: SUAVE }}
      />
    </motion.div>
  )
}

// ---------------------------------------------------------------------------
// El lugar, en collage
// ---------------------------------------------------------------------------

export function ElLugar() {
  return (
    <section className="overflow-hidden py-24 sm:py-32">
      <div className="mx-auto grid max-w-6xl gap-14 px-5 sm:px-6 lg:grid-cols-2 lg:items-center">
        <div className="grid grid-cols-5 grid-rows-6 gap-4" style={{ height: 'min(110vw, 36rem)' }}>
          <ImagenRevelada src={`${F}salon-arcos.jpg`} alt="El salón de los arcos" className="col-span-3 row-span-6" />
          <ImagenRevelada src={`${F}fuente.jpg`} alt="La fuente del patio" className="col-span-2 row-span-3" retraso={0.15} />
          <ImagenRevelada src={`${F}patio-mesas.jpg`} alt="Mesas en el patio" className="col-span-2 row-span-3" retraso={0.3} />
        </div>
        <div>
          <Revelar>
            <Titulo ante="El lugar">
              Mantel largo, aire fresco <It>y tiempo para conversar</It>
            </Titulo>
          </Revelar>
          <Revelar retraso={0.1}>
            <p className="mt-7 text-base leading-relaxed text-crema-100/70 sm:text-lg">
              El Patio nació para las celebraciones. Salón climatizado, terraza para las noches templadas de
              Turbaco y comedores privados cuando la ocasión pide intimidad. Porciones generosas, servicio
              atento y una carta pensada para compartir.
            </p>
          </Revelar>
        </div>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Platos en movimiento: dos filas que corren en sentidos opuestos
// ---------------------------------------------------------------------------

const FILA_1 = [
  { src: `${F}lomo-fuego.jpg`, t: 'Carnes al fuego' },
  { src: `${F}langostinos.jpg`, t: 'Mariscos de la casa' },
  { src: `${F}croquetas.jpg`, t: 'Entradas para compartir' },
  { src: `${F}emplatado.jpg`, t: 'Emplatado al momento' },
  { src: `${F}gratinado.jpg`, t: 'Gratinado de la casa' },
  { src: `${F}lomo-risotto.jpg`, t: 'Fusión gourmet' },
]
const FILA_2 = [
  { src: `${F}coctel-barro.jpg`, t: 'Coctelería de autor' },
  { src: `${F}comensales.jpg`, t: 'Mesas que se alargan' },
  { src: `${F}coctel-rosa.jpg`, t: 'Clásicos reinventados' },
  { src: `${F}barra.jpg`, t: 'La barra' },
  { src: `${F}coctel-maracuya.jpg`, t: 'Frutas del Caribe' },
  { src: `${F}mesa-romantica.jpg`, t: 'Noches especiales' },
  { src: `${F}mesero.jpg`, t: 'Servicio atento' },
  { src: `${F}coctel-rosas.jpg`, t: 'Ediciones de temporada' },
]

function FilaMovil({ fotos, haciaLaDerecha = false, duracion = 45 }: { fotos: typeof FILA_1; haciaLaDerecha?: boolean; duracion?: number }) {
  return (
    <div className="group relative overflow-hidden">
      <motion.div
        className="flex w-max gap-4"
        animate={{ x: haciaLaDerecha ? ['-50%', '0%'] : ['0%', '-50%'] }}
        transition={{ duration: duracion, ease: 'linear', repeat: Infinity }}
      >
        {[...fotos, ...fotos].map((f, k) => (
          <figure
            key={k}
            className="relative h-80 w-56 shrink-0 overflow-hidden rounded-3xl border border-oro-500/15 sm:h-[26rem] sm:w-72"
          >
            <img src={f.src} alt={f.t} loading="lazy" className="h-full w-full object-cover transition-transform duration-700 hover:scale-110" />
            <figcaption className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-onix-950/90 to-transparent px-5 pb-4 pt-12 font-titulo text-xl italic text-crema-50">
              {f.t}
            </figcaption>
          </figure>
        ))}
      </motion.div>
    </div>
  )
}

export function PlatosEnMovimiento() {
  return (
    <section className="overflow-hidden border-t border-oro-500/15 bg-onix-900/50 py-24 sm:py-28">
      <div className="mx-auto max-w-6xl px-5 sm:px-6">
        <Revelar className="mb-12 flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
          <Titulo ante="De la cocina a su mesa">
            Sabores que <It>se recuerdan</It>
          </Titulo>
          <p className="max-w-sm text-crema-100/65">
            Producto del Caribe, técnica de fusión y una barra que se toma en serio cada cóctel.
          </p>
        </Revelar>
      </div>
      <div className="grid gap-4">
        <FilaMovil fotos={FILA_1} />
        <FilaMovil fotos={FILA_2} haciaLaDerecha duracion={55} />
      </div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Franja con profundidad
// ---------------------------------------------------------------------------

export function FranjaParallax() {
  const ref = useRef<HTMLElement>(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'end start'] })
  // Tres fotos verticales que se desplazan a velocidades distintas: la del
  // medio va en contra, y eso es lo que da la sensación de profundidad.
  const y1 = useTransform(scrollYProgress, [0, 1], ['-10%', '10%'])
  const y2 = useTransform(scrollYProgress, [0, 1], ['12%', '-12%'])
  const y3 = useTransform(scrollYProgress, [0, 1], ['-6%', '16%'])
  const columnas = [
    { src: `${F}chef-emplatando.jpg`, y: y1 },
    { src: `${F}chef.jpg`, y: y2 },
    { src: `${F}salon-barra.jpg`, y: y3 },
  ]
  return (
    <section ref={ref} className="relative h-[90svh] min-h-[32rem] overflow-hidden bg-onix-950">
      <div className="absolute inset-0 grid grid-cols-2 gap-3 px-3 sm:grid-cols-3">
        {columnas.map((c, k) => (
          <div key={c.src} className={`relative overflow-hidden rounded-3xl ${k === 2 ? 'hidden sm:block' : ''}`}>
            <motion.img
              src={c.src}
              alt=""
              aria-hidden
              loading="lazy"
              style={{ y: c.y }}
              className="absolute inset-x-0 -top-[15%] h-[130%] w-full object-cover"
            />
          </div>
        ))}
      </div>
      <div className="absolute inset-0 bg-gradient-to-t from-onix-950 via-onix-950/50 to-onix-950/30" />
      <div className="relative mx-auto flex h-full max-w-6xl flex-col justify-end px-5 pb-16 sm:px-6 sm:pb-24">
        <Revelar>
          <Titulo ante="Cada viernes">
            Cocina en vivo, <It>un destino distinto</It>
          </Titulo>
          <p className="mt-5 max-w-lg text-lg font-light text-crema-100/80">
            Nuestro equipo prepara frente a usted la cocina de un país diferente cada semana.
          </p>
        </Revelar>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Tarjeta de fidelidad
// ---------------------------------------------------------------------------

/** Cuántas visitas hacen falta para el premio. Ajustable. */
const SELLOS = 10
/** Cuántos sellos se ven marcados en la tarjeta de muestra. */
const SELLOS_MUESTRA = 7

const BENEFICIOS = [
  { icono: CalendarCheck, t: 'Cada visita suma un sello', d: 'Muestre su tarjeta al pagar y la caja le marca el sello.' },
  { icono: Gift, t: `Al sello ${SELLOS}, la casa invita`, d: 'Un plato fuerte por cuenta de El Patio.' },
  { icono: Cake, t: 'Postre de cumpleaños', d: 'En su mes, el postre corre por nuestra cuenta.' },
  { icono: Star, t: 'Prioridad en reservas', d: 'Las fechas especiales se llenan; los socios eligen primero.' },
]

function Tarjeta({ nombre }: { nombre: string }) {
  // La tarjeta se inclina siguiendo el cursor, con un resorte para que no tiemble.
  const mx = useMotionValue(0)
  const my = useMotionValue(0)
  const rotX = useSpring(useTransform(my, [-0.5, 0.5], [12, -12]), { stiffness: 150, damping: 15 })
  const rotY = useSpring(useTransform(mx, [-0.5, 0.5], [-14, 14]), { stiffness: 150, damping: 15 })
  const brilloX = useTransform(mx, [-0.5, 0.5], ['0%', '100%'])
  const reflejo = useTransform(
    brilloX,
    (x) => `radial-gradient(circle at ${x} 30%, rgba(227,202,132,0.25), transparent 55%)`,
  )

  function mover(e: PointerEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect()
    mx.set((e.clientX - r.left) / r.width - 0.5)
    my.set((e.clientY - r.top) / r.height - 0.5)
  }
  function soltar() {
    mx.set(0)
    my.set(0)
  }

  return (
    <div className="[perspective:1200px]" onPointerMove={mover} onPointerLeave={soltar}>
      <motion.div
        style={{ rotateX: rotX, rotateY: rotY, transformStyle: 'preserve-3d' }}
        animate={{ y: [0, -10, 0] }}
        transition={{ y: { duration: 6, repeat: Infinity, ease: 'easeInOut' } }}
        className="relative mx-auto aspect-[1.586] w-full max-w-md overflow-hidden rounded-3xl border border-oro-400/40 bg-gradient-to-br from-onix-700 via-onix-900 to-onix-950 p-6 shadow-[0_40px_80px_-20px_rgba(0,0,0,0.8)] sm:p-8"
      >
        {/* Reflejo que sigue al cursor y un destello que cruza de vez en cuando. */}
        <motion.div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{ background: reflejo }}
        />
        <motion.div
          aria-hidden
          className="pointer-events-none absolute inset-y-0 w-1/3 -skew-x-12 bg-gradient-to-r from-transparent via-crema-50/15 to-transparent"
          initial={{ left: '-40%' }}
          animate={{ left: '140%' }}
          transition={{ duration: 1.6, repeat: Infinity, repeatDelay: 4, ease: 'easeInOut' }}
        />
        <img src="/emblema.png" alt="" aria-hidden className="pointer-events-none absolute -right-10 -top-10 w-48 opacity-[0.07]" />

        <div className="relative flex h-full flex-col justify-between">
          <div className="flex items-start justify-between">
            <div>
              <p className="font-marca text-lg tracking-[0.3em] text-crema-50 sm:text-xl">EL PATIO</p>
              <p className="mt-0.5 text-[0.55rem] uppercase tracking-[0.4em] text-oro-400">Club de la casa</p>
            </div>
            <div className="h-8 w-11 rounded-md bg-gradient-to-br from-oro-300 via-oro-500 to-oro-700 opacity-90" aria-hidden />
          </div>

          {/* Los sellos se marcan uno tras otro cuando la tarjeta entra en pantalla. */}
          <div className="grid grid-cols-5 gap-2 sm:gap-2.5">
            {Array.from({ length: SELLOS }).map((_, k) => {
              const marcado = k < SELLOS_MUESTRA
              const premio = k === SELLOS - 1
              return (
                <motion.span
                  key={k}
                  className={`grid aspect-square place-items-center rounded-full border ${
                    marcado
                      ? 'border-oro-400 bg-oro-500/90 text-onix-950'
                      : premio
                        ? 'border-dashed border-oro-400/70 text-oro-300'
                        : 'border-crema-100/20 text-crema-100/30'
                  }`}
                  initial={marcado ? { scale: 0, rotate: -90 } : { opacity: 0 }}
                  whileInView={marcado ? { scale: 1, rotate: 0 } : { opacity: 1 }}
                  viewport={{ once: true }}
                  transition={{ delay: 0.4 + k * 0.12, type: 'spring', stiffness: 260, damping: 14 }}
                >
                  {premio ? (
                    <Gift className="h-3.5 w-3.5 sm:h-4 sm:w-4" aria-hidden />
                  ) : marcado ? (
                    <Star className="h-3.5 w-3.5 fill-current sm:h-4 sm:w-4" aria-hidden />
                  ) : (
                    <span className="text-[0.6rem]">{k + 1}</span>
                  )}
                </motion.span>
              )
            })}
          </div>

          <div className="flex items-end justify-between gap-4">
            <div className="min-w-0">
              <p className="text-[0.55rem] uppercase tracking-[0.3em] text-crema-100/50">Socio</p>
              <p className="truncate font-titulo text-xl text-crema-50 sm:text-2xl">{nombre.trim() || 'Su nombre aquí'}</p>
            </div>
            <p className="shrink-0 font-mono text-xs tracking-widest text-oro-300/80">N.º 2026·0001</p>
          </div>
        </div>
      </motion.div>
    </div>
  )
}

export function TarjetaFidelidad({ whatsapp }: { whatsapp: string }) {
  const [nombre, setNombre] = useState('')
  const [celular, setCelular] = useState('')

  function pedir(e: FormEvent) {
    e.preventDefault()
    const texto = `Hola, quiero mi tarjeta del Club El Patio.\nNombre: ${nombre.trim()}\nCelular: ${celular.trim()}`
    window.open(enlaceWhatsApp(whatsapp, texto), '_blank', 'noopener,noreferrer')
  }

  return (
    <section id="club" className="relative scroll-mt-16 overflow-hidden border-t border-oro-500/15 py-24 sm:py-32">
      <div aria-hidden className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_80%_30%,rgba(192,154,51,0.16),transparent_55%)]" />
      <div className="relative mx-auto grid max-w-6xl gap-16 px-5 sm:px-6 lg:grid-cols-2 lg:items-center">
        <div>
          <Revelar>
            <Titulo ante="Club El Patio · Tarjeta de fidelidad">
              Cada visita <It>suma</It>
            </Titulo>
          </Revelar>
          <Revelar retraso={0.1}>
            <p className="mt-6 max-w-lg text-base leading-relaxed text-crema-100/70 sm:text-lg">
              Quien vuelve a El Patio se nota. Pida su tarjeta gratis, junte sellos con cada cena y deje que la casa
              le devuelva el gusto.
            </p>
          </Revelar>
          <ul className="mt-9 grid gap-4 sm:grid-cols-2">
            {BENEFICIOS.map(({ icono: Icono, t, d }, k) => (
              <Revelar key={t} retraso={0.15 + k * 0.07}>
                <li className="tarjeta-viva h-full rounded-2xl border border-oro-500/15 bg-onix-900/60 p-5">
                  <Icono className="h-5 w-5 text-oro-400" strokeWidth={1.5} aria-hidden />
                  <p className="mt-3 font-titulo text-xl text-crema-100">{t}</p>
                  <p className="mt-1 text-sm text-crema-100/60">{d}</p>
                </li>
              </Revelar>
            ))}
          </ul>
        </div>

        <div>
          <Revelar retraso={0.1}>
            <Tarjeta nombre={nombre} />
          </Revelar>
          <Revelar retraso={0.2}>
            <form onSubmit={pedir} className="mx-auto mt-10 grid max-w-md gap-3">
              <input
                required
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Nombre completo"
                autoComplete="name"
                className="min-h-toque rounded-xl border border-crema-100/15 bg-onix-900/70 px-4 text-crema-100 outline-none transition placeholder:text-crema-100/35 focus:border-oro-400"
              />
              <input
                required
                type="tel"
                value={celular}
                onChange={(e) => setCelular(e.target.value)}
                placeholder="Celular / WhatsApp"
                autoComplete="tel"
                className="min-h-toque rounded-xl border border-crema-100/15 bg-onix-900/70 px-4 text-crema-100 outline-none transition placeholder:text-crema-100/35 focus:border-oro-400"
              />
              <button
                type="submit"
                className="boton-brillo mt-1 inline-flex min-h-[52px] items-center justify-center gap-2 rounded-xl bg-oro-500 px-6 text-sm font-semibold uppercase tracking-[0.16em] text-onix-950 hover:bg-oro-400"
              >
                <MessageCircle className="h-4 w-4" aria-hidden />
                Quiero mi tarjeta
              </button>
              <p className="text-center text-xs text-crema-100/40">Le confirmamos por WhatsApp. Es gratis.</p>
            </form>
          </Revelar>
        </div>
      </div>
    </section>
  )
}

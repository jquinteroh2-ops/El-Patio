/**
 * Avisa si en `src/` quedó una clase de color que la paleta no define.
 *
 * ── Por qué existe ───────────────────────────────────────────────────────────
 * Tailwind descarta en silencio la clase que no reconoce. `bg-cobre-500` en un
 * proyecto cuya paleta se llama `oro` no da error, no sale en consola y no
 * rompe la compilación: simplemente no genera regla, y el elemento se queda sin
 * fondo. Si además el texto es oscuro —como en el botón principal, que es
 * `text-onix-950` sobre dorado— el resultado es un botón INVISIBLE.
 *
 * Ya pasó, y de la peor manera: este mismo software corre en dos restaurantes
 * del mismo dueño, El Patio en dorado (`oro`) y La Carreta en cobre (`cobre`),
 * y los archivos se van pasando de un repositorio al otro. Un `Boton.tsx`
 * copiado sin cambiar el acento dejó el botón de guardar sin fondo en todo el
 * panel, y se descubrió mirando una pantalla, no compilando.
 *
 * Por eso corre antes de `build`: el despliegue se hace desde `main`, y esto
 * tiene que fallar aquí y no en el celular de un mesero.
 * ─────────────────────────────────────────────────────────────────────────────
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const RAIZ = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

/** Los prefijos de Tailwind que aceptan un color. */
const PROPIEDADES = [
  'bg', 'text', 'border', 'ring', 'fill', 'stroke', 'from', 'via', 'to',
  'divide', 'outline', 'decoration', 'accent', 'caret', 'shadow', 'placeholder',
]

function paletaDelProyecto() {
  const config = readFileSync(join(RAIZ, 'tailwind.config.js'), 'utf8')

  // Se lee del archivo y no importando la config para no arrastrar aquí todo el
  // arranque de Tailwind por una lista de nombres.
  const desde = config.indexOf('colors:')
  if (desde < 0) return new Set()

  // Se recorre contando llaves hasta cerrar el bloque `colors`. Cortar «desde
  // colors hasta el final» haría que también entraran `keyframes` y
  // `fontFamily`, y entonces una familia mal escrita que coincidiera con una de
  // ellas pasaría la revisión sin que nadie se enterara.
  const abre = config.indexOf('{', desde)
  let nivel = 0
  let hasta = abre
  for (; hasta < config.length; hasta++) {
    if (config[hasta] === '{') nivel++
    else if (config[hasta] === '}' && --nivel === 0) break
  }

  const bloque = config.slice(abre + 1, hasta)
  // Solo las claves del primer nivel: `oro: {` sí, el `500:` de dentro no.
  const familias = new Set()
  let profundidad = 0
  for (const linea of bloque.split('\n')) {
    const clave = linea.match(/^\s*([a-zA-Z][\w-]*)\s*:\s*(\{)?/)
    if (profundidad === 0 && clave && clave[2]) familias.add(clave[1])
    for (const c of linea) {
      if (c === '{') profundidad++
      else if (c === '}') profundidad--
    }
  }
  return familias
}

function archivos(dir) {
  return readdirSync(dir).flatMap((nombre) => {
    const ruta = join(dir, nombre)
    if (statSync(ruta).isDirectory()) return archivos(ruta)
    return /\.(tsx?|css|html)$/.test(nombre) ? [ruta] : []
  })
}

const paleta = paletaDelProyecto()
if (paleta.size === 0) {
  console.error('revisar-paleta: no pude leer los colores de tailwind.config.js')
  process.exit(1)
}

const patron = new RegExp(
  `\\b(?:[a-z-]+:)*(?:${PROPIEDADES.join('|')})-([a-z][a-z-]*)-(\\d{2,3})\\b`,
  'g',
)

/* Nombres que Tailwind trae de fábrica y que aquí son legítimos aunque no
   estén en la paleta de la casa. */
const DE_FABRICA = new Set(['slate', 'gray', 'zinc', 'neutral', 'stone', 'red', 'orange',
  'amber', 'yellow', 'lime', 'green', 'emerald', 'teal', 'cyan', 'sky', 'blue', 'indigo',
  'violet', 'purple', 'fuchsia', 'pink', 'rose', 'black', 'white'])

const hallazgos = []
for (const ruta of archivos(join(RAIZ, 'src'))) {
  const texto = readFileSync(ruta, 'utf8')
  for (const [clase, familia] of texto.matchAll(patron)) {
    if (paleta.has(familia) || DE_FABRICA.has(familia)) continue
    const linea = texto.slice(0, texto.indexOf(clase)).split('\n').length
    hallazgos.push(`  ${ruta.replace(RAIZ, '')}:${linea}  ${clase}   (no existe la familia «${familia}»)`)
  }
}

if (hallazgos.length > 0) {
  console.error('\nColores que la paleta no define. Tailwind los descarta en silencio,')
  console.error('así que el elemento se queda SIN fondo o SIN color:\n')
  console.error([...new Set(hallazgos)].join('\n'))
  console.error(`\nLa paleta de este proyecto es: ${[...paleta].join(', ')}\n`)
  process.exit(1)
}

console.log(`revisar-paleta: bien (${[...paleta].join(', ')})`)

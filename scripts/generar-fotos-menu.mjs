// Genera una foto por cada plato de carta-2026.json usando la API de imagenes
// de OpenAI (gpt-image-1) y las guarda en fotos-menu/<id>.png.
//
// Uso:
//   $env:OPENAI_API_KEY = "sk-..."
//   node scripts/generar-fotos-menu.mjs
//
// Es resumible: si una foto ya existe en fotos-menu/, se salta. Si el script
// se corta a mitad de camino (rate limit, internet), correrlo de nuevo
// retoma donde se quedo en vez de gastar de nuevo lo ya generado.

import { mkdir, readFile, writeFile, access } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(__dirname, '..')
const archivoCarta = path.join(raiz, 'carta-2026.json')
const carpetaSalida = path.join(raiz, 'fotos-menu')

const apiKey = process.env.OPENAI_API_KEY
if (!apiKey) {
  console.error('Falta OPENAI_API_KEY en el entorno. Ejemplo:')
  console.error('  $env:OPENAI_API_KEY = "sk-..."')
  process.exit(1)
}

const promptPara = (nombre, descripcion) =>
  `Fotografia profesional de comida de restaurante gourmet: "${nombre}". ` +
  `${descripcion ? `Detalles del plato: ${descripcion}. ` : ''}` +
  'Servido en plato de ceramica blanca sobre mesa de madera oscura, luz natural lateral suave, ' +
  'poca profundidad de campo, alta resolucion, estilo editorial de restaurante. ' +
  'Sin texto, sin logo, sin marca de agua, sin manos ni personas en la imagen.'

async function existeArchivo(ruta) {
  try {
    await access(ruta)
    return true
  } catch {
    return false
  }
}

async function generarImagen(prompt) {
  const respuesta = await fetch('https://api.openai.com/v1/images/generations', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: 'gpt-image-1',
      prompt,
      size: '1024x1024',
      quality: 'medium',
    }),
  })

  if (!respuesta.ok) {
    const texto = await respuesta.text()
    throw new Error(`API respondio ${respuesta.status}: ${texto}`)
  }

  const datos = await respuesta.json()
  return Buffer.from(datos.data[0].b64_json, 'base64')
}

async function main() {
  const carta = JSON.parse(await readFile(archivoCarta, 'utf8'))
  await mkdir(carpetaSalida, { recursive: true })

  console.log(`${carta.items.length} platos por procesar.\n`)

  let generadas = 0
  let saltadas = 0

  for (const [indice, item] of carta.items.entries()) {
    const rutaSalida = path.join(carpetaSalida, `${item.id}.png`)
    const progreso = `[${indice + 1}/${carta.items.length}]`

    if (await existeArchivo(rutaSalida)) {
      console.log(`${progreso} ya existe: ${item.nombre}`)
      saltadas++
      continue
    }

    console.log(`${progreso} generando: ${item.nombre}...`)
    try {
      const imagen = await generarImagen(promptPara(item.nombre, item.descripcion))
      await writeFile(rutaSalida, imagen)
      generadas++
    } catch (error) {
      console.error(`${progreso} fallo «${item.nombre}»: ${error.message}`)
      console.error('   Volve a correr el script para reintentar solo lo que falta.')
    }
  }

  console.log(`\nListo. ${generadas} generadas, ${saltadas} ya existian.`)
  console.log(`Fotos en: ${carpetaSalida}`)
  console.log('Siguiente paso: subirlas una por una desde /admin/carta, editando cada producto.')
}

main()

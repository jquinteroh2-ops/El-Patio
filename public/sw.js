/* eslint-env serviceworker */

/**
 * Service worker de El Patio.
 *
 * Sin esto, la tablet que pierde WiFi sigue funcionando solo mientras nadie
 * recargue la pagina: al recargar, el navegador no puede traer el codigo de la
 * aplicacion y el mesero se queda mirando un error. Aqui se guarda el
 * "esqueleto" (HTML, JS y CSS) para que la comandera abra sin senal.
 *
 * Los datos no pasan por aqui: los sirve el backend, y lo unico que se guarda
 * es el codigo de la aplicacion.
 */

/**
 * El nombre de la cache lleva el sello de la compilacion, que llega en la URL
 * con la que main.tsx registra este archivo: `/sw.js?v=xxxx`.
 *
 * Antes era una constante fija. Como este archivo tampoco cambia entre
 * despliegues y el navegador decide si hay version nueva comparando el archivo,
 * un aparato que ya lo tenia registrado no volvia a mirarlo: seguia abriendo el
 * index.html guardado, y el mismo sistema se veia distinto en el computador y
 * en el celular. Con el sello, cada despliegue estrena cache y el `activate` de
 * abajo borra las anteriores.
 */
const VERSION = new URL(self.location.href).searchParams.get('v') || 'dev'
const CACHE = `elpatio-${VERSION}`
const RAIZ = '/index.html'

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(['/', RAIZ])),
  )
  // La version nueva entra sin esperar a que se cierren las pestanas viejas.
  self.skipWaiting()
})

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((nombres) =>
        Promise.all(nombres.filter((n) => n !== CACHE).map((n) => caches.delete(n))),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (evento) => {
  const peticion = evento.request
  if (peticion.method !== 'GET') return

  const url = new URL(peticion.url)
  if (url.origin !== self.location.origin) return

  // Navegacion: primero la red, y si no hay senal, el index guardado.
  // Asi el mesero siempre entra, aunque el salon se quede sin WiFi.
  if (peticion.mode === 'navigate') {
    evento.respondWith(
      fetch(peticion)
        .then((respuesta) => {
          const copia = respuesta.clone()
          void caches.open(CACHE).then((cache) => cache.put(RAIZ, copia))
          return respuesta
        })
        .catch(() => caches.match(RAIZ).then((guardada) => guardada ?? Response.error())),
    )
    return
  }

  // Recursos de la aplicacion: primero lo guardado, que es instantaneo, y de
  // fondo se refresca para la proxima vez.
  evento.respondWith(
    caches.match(peticion).then((guardada) => {
      const desdeRed = fetch(peticion)
        .then((respuesta) => {
          if (respuesta.ok) {
            const copia = respuesta.clone()
            void caches.open(CACHE).then((cache) => cache.put(peticion, copia))
          }
          return respuesta
        })
        .catch(() => guardada ?? Response.error())

      return guardada ?? desdeRed
    }),
  )
})

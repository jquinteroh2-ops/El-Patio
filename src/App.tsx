import { Suspense, lazy, useEffect, useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ProveedorSesion } from '@/compartido/auth'
import * as api from '@/compartido/mockApi'
import { MetaDeRuta } from '@/compartido/seo'
import { RevelarAlDesplazar } from '@/compartido/revelar'
import { precargarFichaSitio } from '@/compartido/sitio'
import { Cargando } from '@/componentes/ui/Cargando'
import { GuardaRuta } from '@/componentes/GuardaRuta'

import { ProveedorAvisos } from '@/componentes/ui/Avisos'

import LayoutPublico from '@/publico/LayoutPublico'
import Inicio from '@/publico/Inicio'
import Carta from '@/publico/Carta'
import Reservar from '@/publico/Reservar'
import Pedir from '@/publico/Pedir'
import Acceso from '@/publico/Acceso'
import Trabaja from '@/publico/Trabaja'
import PqrPublico from '@/publico/Pqr'
import PoliticaDatos from '@/publico/PoliticaDatos'
import NoEncontrada from '@/publico/NoEncontrada'

/*
 * Las pantallas del personal se descargan aparte, solo al entrar a una de
 * ellas: ver `pantallasPersonal.ts`. Todas salen del mismo modulo, asi que
 * el navegador las trae en un solo paquete.
 */
const personal = () =>
  import('@/pantallasPersonal')
    .then((modulo) => {
      // Cargo bien: el proximo despliegue tiene derecho a su propia recarga.
      try {
        sessionStorage.removeItem('recargado-por-paquete')
      } catch {
        // Sin almacenamiento no hay marca que borrar.
      }
      return modulo
    })
    .catch((error: unknown) => {
      // Una pestana abierta desde antes de un despliegue pide el paquete de la
      // compilacion anterior, que ya no existe en el servidor. Se recarga una
      // vez para traer el index.html nuevo; si vuelve a fallar, es otra cosa y
      // se deja ver el error.
      try {
        if (!sessionStorage.getItem('recargado-por-paquete')) {
          sessionStorage.setItem('recargado-por-paquete', '1')
          window.location.reload()
        }
      } catch {
        // Sin almacenamiento no hay como evitar un ciclo de recargas: no se recarga.
      }
      throw error
    })
type Personal = Awaited<ReturnType<typeof personal>>
function pantalla<K extends keyof Personal>(nombre: K) {
  return lazy(() => personal().then((m) => ({ default: m[nombre] })))
}

const MapaMesas = pantalla('MapaMesas')
const OrdenMesa = pantalla('OrdenMesa')
const SelectorProductos = pantalla('SelectorProductos')
const CuentaMesa = pantalla('CuentaMesa')
const PantallaCocina = pantalla('PantallaCocina')
const PantallaPedidos = pantalla('PantallaPedidos')
const PantallaReservas = pantalla('PantallaReservas')
const PantallaAjustes = pantalla('PantallaAjustes')
const PantallaRepartidor = pantalla('PantallaRepartidor')
const LayoutAdmin = pantalla('LayoutAdmin')
const InicioAdmin = pantalla('InicioAdmin')
const Reservas = pantalla('Reservas')
const CartaAdmin = pantalla('CartaAdmin')
const Publicaciones = pantalla('Publicaciones')
const Ventas = pantalla('Ventas')
const Cierre = pantalla('Cierre')
const Caja = pantalla('Caja')
const Papelera = pantalla('Papelera')
const Postulaciones = pantalla('Postulaciones')
const PqrAdmin = pantalla('PqrAdmin')
const InstitucionalAdmin = pantalla('InstitucionalAdmin')
const Reportes = pantalla('Reportes')
const Configuracion = pantalla('Configuracion')


export default function App() {
  const [listo, setListo] = useState(false)

  // Comprueba que el servidor conteste antes de pintar nada, para no abrir
  // pantallas vacias sin explicacion. No bloquea si falla.
  //
  // De paso trae la ficha del sitio, para que los modulos que la leen sin React
  // -los mensajes de WhatsApp, los comprobantes que se imprimen- no salgan con
  // la direccion de reserva la primera vez que alguien los usa.
  useEffect(() => {
    void Promise.all([api.inicializar(), precargarFichaSitio()]).finally(() => setListo(true))
  }, [])

  if (!listo) return <Cargando pantallaCompleta />

  return (
    // `v7_startTransition` hace que, al navegar a una pantalla del personal que
    // todavia no se pinto, se quede la anterior en vez de parpadear el cargador.
    <BrowserRouter future={{ v7_startTransition: true }}>
      {/* Ajusta titulo, descripcion y canonica segun la ruta. No pinta nada. */}
      <MetaDeRuta />
      {/* Hace subir flotando lo que lleve la clase `revelar`. Tampoco pinta
          nada: solo observa y va marcando lo que entra en pantalla. */}
      <RevelarAlDesplazar />
      <ProveedorSesion>
        <ProveedorAvisos>
          <Suspense fallback={<Cargando pantallaCompleta />}>
            <Routes>
              {/* ---------- Sitio publico ---------- */}
              <Route element={<LayoutPublico />}>
                <Route path="/" element={<Inicio />} />
                <Route path="/carta" element={<Carta />} />
                <Route path="/reservar" element={<Reservar />} />
                <Route path="/pedir" element={<Pedir />} />
                <Route path="/trabaja-con-nosotros" element={<Trabaja />} />
                <Route path="/pqr" element={<PqrPublico />} />
                <Route path="/politica-de-datos" element={<PoliticaDatos />} />
                {/* Cualquier direccion que no exista: dice que no existe, no manda al inicio. */}
                <Route path="*" element={<NoEncontrada />} />
              </Route>
              <Route path="/acceso" element={<Acceso />} />

              {/* ---------- Comandera ---------- */}
              <Route
                path="/comandera"
                element={
                  <GuardaRuta roles={['mesero', 'administrador']}>
                    <MapaMesas />
                  </GuardaRuta>
                }
              />
              <Route
                path="/comandera/mesa/:mesaId"
                element={
                  <GuardaRuta roles={['mesero', 'administrador']}>
                    <OrdenMesa />
                  </GuardaRuta>
                }
              />
              <Route
                path="/comandera/mesa/:mesaId/agregar"
                element={
                  <GuardaRuta roles={['mesero', 'administrador']}>
                    <SelectorProductos />
                  </GuardaRuta>
                }
              />
              <Route
                path="/comandera/mesa/:mesaId/cuenta"
                element={
                  <GuardaRuta roles={['mesero', 'cajero', 'administrador']}>
                    <CuentaMesa />
                  </GuardaRuta>
                }
              />

              {/* ---------- Cocina y barra ---------- */}
              <Route
                path="/cocina"
                element={
                  <GuardaRuta roles={['cocina', 'administrador']}>
                    <PantallaCocina destino="cocina" />
                  </GuardaRuta>
                }
              />
              <Route
                path="/cocina/bar"
                element={
                  <GuardaRuta roles={['cocina', 'administrador']}>
                    <PantallaCocina destino="bar" />
                  </GuardaRuta>
                }
              />

              {/* ---------- Recepcion: domicilios, para llevar y reservas ---------- */}
              <Route
                path="/recepcion"
                element={
                  <GuardaRuta roles={['recepcion', 'cajero', 'administrador']}>
                    <PantallaPedidos />
                  </GuardaRuta>
                }
              />
              <Route
                path="/recepcion/reservas"
                element={
                  <GuardaRuta roles={['recepcion', 'cajero', 'administrador']}>
                    <PantallaReservas />
                  </GuardaRuta>
                }
              />
              {/*
                El horario y el contacto del sitio, y el canal de pedidos. Es lo
                unico de configuracion que llega al mostrador: quien contesta el
                telefono es quien primero sabe que el horario publicado esta viejo.
              */}
              <Route
                path="/recepcion/ajustes"
                element={
                  <GuardaRuta roles={['recepcion', 'cajero', 'administrador']}>
                    <PantallaAjustes />
                  </GuardaRuta>
                }
              />

              {/* ---------- La calle ---------- */}
              <Route
                path="/reparto"
                element={
                  <GuardaRuta roles={['repartidor', 'administrador']}>
                    <PantallaRepartidor />
                  </GuardaRuta>
                }
              />

              {/* ---------- Panel administrativo ---------- */}
              <Route
                path="/admin"
                element={
                  <GuardaRuta roles={['cajero', 'administrador']}>
                    <LayoutAdmin />
                  </GuardaRuta>
                }
              >
                <Route index element={<InicioAdmin />} />
                <Route path="reservas" element={<Reservas />} />
                <Route path="carta" element={<CartaAdmin />} />
                <Route path="publicaciones" element={<Publicaciones />} />
                <Route path="ventas" element={<Ventas />} />
                <Route path="caja" element={<Caja />} />
                <Route path="cierre" element={<Cierre />} />
                <Route
                  path="institucional"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <InstitucionalAdmin />
                    </GuardaRuta>
                  }
                />
                <Route
                  path="pqr"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <PqrAdmin />
                    </GuardaRuta>
                  }
                />
                <Route
                  path="postulaciones"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <Postulaciones />
                    </GuardaRuta>
                  }
                />
                <Route
                  path="papelera"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <Papelera />
                    </GuardaRuta>
                  }
                />
                {/* Conciliación con Globalsoft: retirada del panel por ahora a
                    pedido del restaurante. La pantalla y el backend siguen en
                    el proyecto para volver a ponerla cuando se conecte. */}
                <Route
                  path="reportes"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <Reportes />
                    </GuardaRuta>
                  }
                />
                <Route
                  path="configuracion"
                  element={
                    <GuardaRuta roles={['administrador']}>
                      <Configuracion />
                    </GuardaRuta>
                  }
                />
              </Route>

            </Routes>
          </Suspense>
        </ProveedorAvisos>
      </ProveedorSesion>
    </BrowserRouter>
  )
}

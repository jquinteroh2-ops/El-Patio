import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProveedorSesion } from '@/compartido/auth'
import * as api from '@/compartido/mockApi'
import { MetaDeRuta } from '@/compartido/seo'
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
import MapaMesas from '@/comandera/MapaMesas'
import OrdenMesa from '@/comandera/OrdenMesa'
import SelectorProductos from '@/comandera/SelectorProductos'
import CuentaMesa from '@/comandera/CuentaMesa'
import PantallaCocina from '@/cocina/PantallaCocina'
import PantallaPedidos from '@/recepcion/PantallaPedidos'
import PantallaReservas from '@/recepcion/PantallaReservas'
import PantallaAjustes from '@/recepcion/PantallaAjustes'
import PantallaRepartidor from '@/repartidor/PantallaRepartidor'
import LayoutAdmin from '@/admin/LayoutAdmin'
import InicioAdmin from '@/admin/InicioAdmin'
import Reservas from '@/admin/Reservas'
import CartaAdmin from '@/admin/CartaAdmin'
import Publicaciones from '@/admin/Publicaciones'
import Ventas from '@/admin/Ventas'
import Cierre from '@/admin/Cierre'
import Conciliacion from '@/admin/Conciliacion'
import Postulaciones from '@/admin/Postulaciones'
import PqrAdmin from '@/admin/Pqr'
import InstitucionalAdmin from '@/admin/Institucional'
import Reportes from '@/admin/Reportes'
import Configuracion from '@/admin/Configuracion'

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
    <BrowserRouter>
      {/* Ajusta titulo, descripcion y canonica segun la ruta. No pinta nada. */}
      <MetaDeRuta />
      <ProveedorSesion>
        <ProveedorAvisos>
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
                path="conciliacion"
                element={
                  <GuardaRuta roles={['administrador']}>
                    <Conciliacion />
                  </GuardaRuta>
                }
              />
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

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ProveedorAvisos>
      </ProveedorSesion>
    </BrowserRouter>
  )
}

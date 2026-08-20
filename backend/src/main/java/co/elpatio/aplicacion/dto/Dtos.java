package co.elpatio.aplicacion.dto;

import co.elpatio.dominio.caja.Turno;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.DivisionPago;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.ModificadorSeleccionado;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.salon.EstadoMesa;
import co.elpatio.dominio.salon.Mesa;
import co.elpatio.dominio.salon.Zona;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que viaja entre el backend y el frontend.
 *
 * Cada record de aqui tiene exactamente los mismos campos, con los mismos
 * nombres, que la interfaz correspondiente de src/compartido/tipos.ts o de
 * mockApi.ts. Es a proposito: el frontend no debe mapear nada, solo cambiar de
 * donde vienen los datos.
 */
public final class Dtos {

  private Dtos() {}

  // -------------------------------------------------------------------------
  // Acceso
  // -------------------------------------------------------------------------

  /** Lo que el frontend guarda en sessionStorage. Nunca incluye la clave. */
  public record Sesion(String usuarioId, String nombre, Rol rol, String usuario, Instant iniciadaEn) {}

  public record PeticionIngreso(String usuario, String clave) {}

  public record PeticionRefresco(String refresco) {}

  /**
   * Respuesta del ingreso.
   *
   * `expiraEnSegundos` viaja para que el frontend programe la renovacion
   * silenciosa sin tener que abrir el token y leerle la fecha por dentro.
   */
  public record RespuestaAcceso(Sesion sesion, String acceso, String refresco, long expiraEnSegundos) {}

  /**
   * Usuario tal como lo espera la pantalla de administracion.
   *
   * `clave` siempre sale vacia: la pantalla solo la escribe para cambiarla y
   * jamas necesita leerla. El hash no sale del backend en ningun caso.
   */
  public record UsuarioDto(String id, String nombre, Rol rol, String usuario, String clave, boolean activo) {

    public static UsuarioDto de(Usuario usuario) {
      return new UsuarioDto(
          usuario.getId(), usuario.getNombre(), usuario.getRol(), usuario.getUsuario(), "", usuario.isActivo());
    }
  }

  // -------------------------------------------------------------------------
  // Ajustes
  // -------------------------------------------------------------------------

  public record AjustesDto(
      int porcentajeInc, boolean simularSinConexion, int consecutivoOrden, LocalDate fechaConsecutivo) {}

  /** Cambios parciales de ajustes: lo que venga en null se deja como esta. */
  public record CambiosAjustes(Integer porcentajeInc, Boolean simularSinConexion) {}

  // -------------------------------------------------------------------------
  // Salon
  // -------------------------------------------------------------------------

  /** Mesa con lo que la comandera necesita pintar en el mapa. */
  public record MesaEnMapa(
      String id,
      int numero,
      String nombre,
      Zona zona,
      int capacidad,
      EstadoMesa estado,
      String meseroId,
      String ordenActivaId,
      Integer ordenNumero,
      Instant abiertaEn,
      long total,
      int comensales,
      int itemsPendientes,
      int itemsListos,
      String meseroNombre) {}

  public record MesaDto(
      String id,
      int numero,
      String nombre,
      Zona zona,
      int capacidad,
      EstadoMesa estado,
      String meseroId,
      String ordenActivaId) {

    public static MesaDto de(Mesa mesa) {
      return new MesaDto(
          mesa.getId(),
          mesa.getNumero(),
          mesa.getNombre(),
          mesa.getZona(),
          mesa.getCapacidad(),
          mesa.getEstado(),
          mesa.getMeseroId(),
          mesa.getOrdenActivaId());
    }
  }

  // -------------------------------------------------------------------------
  // Carta
  // -------------------------------------------------------------------------

  /** Carta agrupada por categoria, tal como la lee el cliente y el mesero. */
  public record CategoriaConItems(String id, String nombre, int orden, List<ItemCarta> items) {

    public static CategoriaConItems de(CategoriaCarta categoria, List<ItemCarta> items) {
      return new CategoriaConItems(categoria.getId(), categoria.getNombre(), categoria.getOrden(), items);
    }
  }

  public record CambioDisponibilidad(boolean disponible) {}

  // -------------------------------------------------------------------------
  // Comandas
  // -------------------------------------------------------------------------

  public record OrdenDetallada(Orden orden, MesaDto mesa, String meseroNombre, int porcentajeInc) {}

  public record PeticionAbrirMesa(String meseroId, int comensales) {}

  public record NuevoItem(
      String itemCartaId,
      int cantidad,
      List<ModificadorSeleccionado> modificadoresSeleccionados,
      String notaCocina) {}

  public record PeticionAgregarItems(List<NuevoItem> items) {}

  public record PeticionCantidad(int cantidad) {}

  public record PeticionMotivo(String motivo) {}

  public record PeticionComensales(int comensales) {}

  public record PeticionNota(String notas) {}

  public record PeticionTraslado(String mesaDestinoId) {}

  public record PeticionCargo(String nombre, long valor) {}

  /**
   * Resultado de mandar la comanda a produccion.
   *
   * `encolado` se conserva del prototipo pero ahora siempre llega en false: la
   * cola de reintentos vive en el dispositivo, que es donde se pierde el WiFi.
   * El backend, si respondio, es porque recibio.
   */
  public record ResultadoEnvio(boolean encolado, int turno, int cantidadItems) {}

  /**
   * Cuerpo opcional del envio a cocina.
   *
   * Solo lo manda la comandera cuando esta vaciando su cola de envios
   * pendientes, para reponer un turno exactamente como se dicto. En el uso
   * normal no viaja y el backend envia todo lo que este sin salir.
   */
  public record PeticionEnvio(List<String> itemIds, Integer turno) {}

  // -------------------------------------------------------------------------
  // Cocina
  // -------------------------------------------------------------------------

  /** Un bloque de la pantalla de cocina: un turno de envio de una mesa. */
  public record TurnoEnCocina(
      String ordenId,
      int numeroOrden,
      String mesaId,
      String mesaEtiqueta,
      Zona zona,
      String meseroNombre,
      int turno,
      Instant enviadoEn,
      String estado,
      List<ItemOrden> items,
      String notas) {}

  public record PeticionEstadoItem(EstadoItem estado) {}

  public record PeticionEstadoTurno(EstadoItem estado) {}

  // -------------------------------------------------------------------------
  // Cobro
  // -------------------------------------------------------------------------

  public record DatosPago(
      String ordenId,
      int porcentajePropina,
      long propina,
      MetodoPago metodo,
      List<DivisionPago> divisiones,
      String recibidoPor) {}

  public record ComprobanteDetallado(Pago pago, Orden orden, String mesaEtiqueta, String meseroNombre) {}

  // -------------------------------------------------------------------------
  // Reservas
  // -------------------------------------------------------------------------

  public record NuevaReserva(
      String nombreCliente,
      String telefono,
      Instant fechaHora,
      int personas,
      co.elpatio.dominio.reserva.Ocasion ocasion,
      String notas) {}

  public record CambioEstadoReserva(
      co.elpatio.dominio.reserva.EstadoReserva estado, String mesaAsignadaId) {}

  public record PeticionReprogramar(Instant fechaHora) {}

  // -------------------------------------------------------------------------
  // Panel administrativo
  // -------------------------------------------------------------------------

  public record IndicadoresDia(
      long ventaTotal,
      int ordenes,
      long ticketPromedio,
      int mesasOcupadas,
      int mesasTotales,
      long propinas,
      long inc,
      int minutosPromedioPreparacion,
      int comensales) {}

  public record VentaHistorica(Orden orden, Pago pago, String mesaEtiqueta, String meseroNombre) {}

  public record ResumenTurno(
      LocalDate fecha,
      Turno turno,
      long ventaTotal,
      long totalEfectivo,
      long totalTarjeta,
      long totalTransferencia,
      long propinasTotales,
      long incTotal,
      int ordenesAtendidas,
      long ticketPromedio,
      /** Mismo turno del dia anterior, para comparar. */
      long ventaDiaAnterior,
      int ordenesDiaAnterior) {}

  public record PeticionCierre(String cerradoPor) {}

  public record Reportes(
      List<ProductoVendido> masVendidos,
      List<VentaPorFranja> porFranja,
      List<VentaPorMesero> porMesero,
      List<TiempoProducto> tiemposPorProducto,
      List<VentaPorDia> ventasPorDia) {}

  public record ProductoVendido(String nombre, int unidades, long ingreso) {}

  public record VentaPorFranja(String franja, int hora, long ventas, int ordenes) {}

  public record VentaPorMesero(
      String nombre, int ordenes, long ventas, long ticketPromedio, long propinas) {}

  public record TiempoProducto(String nombre, int minutos, int muestras) {}

  public record VentaPorDia(String dia, long total) {}

  public record Alerta(String id, String tipo, String mensaje, int minutos, String mesaId) {}

  // -------------------------------------------------------------------------
  // Errores
  // -------------------------------------------------------------------------

  /** Forma unica de todos los errores del API. El frontend lee `mensaje`. */
  public record ErrorApi(String mensaje) {}
}

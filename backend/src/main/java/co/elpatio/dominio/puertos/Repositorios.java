package co.elpatio.dominio.puertos;

import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.pago.PagoOnline;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.pqr.FiltroPqr;
import co.elpatio.dominio.pqr.Radicado;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.reclutamiento.FiltroPostulaciones;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.dominio.reclutamiento.Postulacion;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.Mesa;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puertos de salida del dominio.
 *
 * Estan juntos en un solo archivo porque son interfaces cortas que se leen
 * mejor de corrido: es el indice de todo lo que el dominio necesita del mundo
 * exterior. Las implementaciones viven en infraestructura/persistencia.
 */
public final class Repositorios {

  private Repositorios() {}

  public interface DeUsuarios {
    List<Usuario> listar();

    Optional<Usuario> porId(String id);

    /** Busca por nombre de acceso sin distinguir mayusculas. */
    Optional<Usuario> porNombreDeUsuario(String usuario);

    Usuario guardar(Usuario usuario);

    boolean hayAlguno();
  }

  public interface DeMesas {
    List<Mesa> listar();

    Optional<Mesa> porId(String id);

    Mesa guardar(Mesa mesa);

    void eliminar(String id);
  }

  public interface DeCarta {
    List<CategoriaCarta> listarCategorias();

    CategoriaCarta guardarCategoria(CategoriaCarta categoria);

    List<ItemCarta> listarItems();

    Optional<ItemCarta> porId(String id);

    ItemCarta guardarItem(ItemCarta item);

    void eliminarItem(String id);
  }

  public interface DeOrdenes {
    List<Orden> listar();

    Optional<Orden> porId(String id);

    /** Comandas todavia abiertas: es lo que miran cocina, el mapa y las alertas. */
    List<Orden> activas();

    List<Orden> abiertasDesde(Instant desde);

    Orden guardar(Orden orden);
  }

  public interface DePagos {
    List<Pago> listar();

    Optional<Pago> porId(String id);

    List<Pago> entre(Instant desde, Instant hasta);

    Pago guardar(Pago pago);
  }

  public interface DeReservas {
    List<Reserva> listar();

    Optional<Reserva> porId(String id);

    Reserva guardar(Reserva reserva);
  }

  public interface DeZonasDomicilio {
    List<ZonaDomicilio> listar();

    Optional<ZonaDomicilio> porId(String id);

    ZonaDomicilio guardar(ZonaDomicilio zona);

    void eliminar(String id);
  }

  public interface DePublicaciones {
    /** Todas, publicadas o no. Es lo que ve el dueno en su pantalla. */
    List<Publicacion> listar();

    Optional<Publicacion> porId(String id);

    Publicacion guardar(Publicacion publicacion);

    void eliminar(String id);
  }

  public interface DeCierres {
    List<CierreCaja> listar();

    CierreCaja guardar(CierreCaja cierre);
  }

  public interface DeConversaciones {
    Optional<Conversacion> porId(String id);

    /**
     * La conversacion abierta con ese identificador en ese canal, si hay una.
     *
     * Un mismo telefono puede tener charlas viejas ya finalizadas; esto solo
     * busca la que sigue activa, que es con la que un mensaje nuevo continua.
     */
    Optional<Conversacion> abiertaPara(Canal canal, String identificadorExterno);

    Conversacion guardar(Conversacion conversacion);
  }

  public interface DePagosOnline {
    Optional<PagoOnline> porId(String id);

    /** Con lo que llega el webhook: es como Wompi identifica de que pedido habla. */
    Optional<PagoOnline> porReferencia(String referencia);

    /** Los que el job de expiracion tiene que revisar. */
    List<PagoOnline> pendientesVencidosAntesDe(Instant instante);

    /** Los cobrados en un periodo, para el reporte de anticipos. */
    List<PagoOnline> creadosEntre(Instant desde, Instant hasta);

    PagoOnline guardar(PagoOnline pago);
  }

  /** La bandeja de salida de ventas hacia el ERP externo. */
  public interface DeEnviosErp {
    Optional<EnvioErp> porId(String id);

    /** Un pago se reporta una sola vez; esto es lo que lo comprueba. */
    Optional<EnvioErp> porPago(String pagoId);

    /**
     * Los que ya les toca salir, de a tandas.
     *
     * El limite no es una optimizacion: tras una caida larga del ERP hay
     * cientos esperando, y mandarlos todos de golpe contra un servidor que
     * acaba de levantarse lo vuelve a tumbar.
     */
    List<EnvioErp> pendientesListos(Instant ahora, int limite);

    /** Lo del periodo, para conciliar contra la contabilidad. */
    List<EnvioErp> entre(Instant desde, Instant hasta);

    EnvioErp guardar(EnvioErp envio);
  }

  /** Las hojas de vida que llegan por el sitio publico. */
  public interface DePostulaciones {
    Optional<Postulacion> porId(String id);

    /** La bandeja, paginada y con sus filtros. */
    Pagina<Postulacion> buscar(FiltroPostulaciones filtro);

    /** Cuantas hay sin revisar. Es el contador del menu. */
    long sinRevisar();

    /**
     * Envios recientes del mismo documento.
     *
     * Sirve para no llenar la bandeja con la misma hoja de vida cinco veces:
     * pasa sin mala intencion, cuando la pagina tarda y la gente vuelve a
     * pulsar el boton.
     */
    List<Postulacion> delDocumentoDesde(String numeroDocumento, Instant desde);

    /** Las del periodo, para el reporte. */
    List<Postulacion> entre(Instant desde, Instant hasta);

    Postulacion guardar(Postulacion postulacion);

    /**
     * La borra de la base.
     *
     * Es el derecho de supresion de la Ley 1581. El archivo de la hoja de vida
     * lo borra el servicio, aparte: borrar solo la fila dejaria el PDF con los
     * datos de la persona tirado en el volumen.
     */
    void eliminar(String id);
  }

  /** Las PQR de los clientes. */
  public interface DeSolicitudesPqr {
    Optional<SolicitudPqr> porId(String id);

    /**
     * La consulta publica: exige radicado Y correo.
     *
     * Las dos cosas, y no solo el numero: con el numero suelto cualquiera
     * podria recorrer los radicados en orden y leer las quejas de todo el
     * mundo, con nombre y telefono incluidos.
     */
    Optional<SolicitudPqr> porRadicadoYCorreo(String radicado, String email);

    /** La bandeja, ordenada por lo que primero vence. */
    Pagina<SolicitudPqr> buscar(FiltroPqr filtro);

    /** Cuantas siguen abiertas. Es el contador del menu. */
    long abiertas();

    /** Las que vencen dentro del plazo de aviso y siguen sin responder. */
    List<SolicitudPqr> porVencerHasta(LocalDate limite);

    /** Las del periodo, para el reporte. */
    List<SolicitudPqr> entre(Instant desde, Instant hasta);

    SolicitudPqr guardar(SolicitudPqr solicitud);

    /**
     * Entrega el siguiente radicado del año tomando el bloqueo del contador.
     *
     * Se llama DENTRO de la transaccion que inserta la solicitud. Una secuencia
     * de PostgreSQL seria mas simple y dejaria huecos al revertirse una
     * transaccion, porque las secuencias no participan del rollback; y un
     * radicado con huecos no sirve para lo unico que tiene que servir, que es
     * demostrar cuantas solicitudes entraron.
     */
    Radicado siguienteRadicado(int ano);
  }

  public interface DeAjustes {
    Ajustes leer();

    Ajustes guardar(Ajustes ajustes);

    /**
     * Entrega el siguiente consecutivo del dia tomando el bloqueo de la fila.
     *
     * El consecutivo de comprobante es diario y no admite saltos ni repetidos:
     * por eso no se usa una secuencia de PostgreSQL, que dejaria huecos cuando
     * una transaccion se revierte, sino un contador bloqueado dentro de la
     * misma transaccion que crea la comanda.
     */
    int siguienteConsecutivo();
  }
}

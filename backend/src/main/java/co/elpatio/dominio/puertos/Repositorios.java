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
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.Mesa;
import java.time.Instant;
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

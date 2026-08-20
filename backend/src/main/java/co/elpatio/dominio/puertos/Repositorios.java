package co.elpatio.dominio.puertos;

import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.personal.Usuario;
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

  public interface DeCierres {
    List<CierreCaja> listar();

    CierreCaja guardar(CierreCaja cierre);
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

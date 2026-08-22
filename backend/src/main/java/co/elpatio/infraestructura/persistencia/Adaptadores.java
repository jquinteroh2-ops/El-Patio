package co.elpatio.infraestructura.persistencia;

import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reserva.Reserva;
import co.elpatio.dominio.salon.Mesa;
import co.elpatio.infraestructura.persistencia.dao.DaoAjustes;
import co.elpatio.infraestructura.persistencia.dao.DaoCategorias;
import co.elpatio.infraestructura.persistencia.dao.DaoCierres;
import co.elpatio.infraestructura.persistencia.dao.DaoItemsCarta;
import co.elpatio.infraestructura.persistencia.dao.DaoMesas;
import co.elpatio.infraestructura.persistencia.dao.DaoOrdenes;
import co.elpatio.infraestructura.persistencia.dao.DaoPagos;
import co.elpatio.infraestructura.persistencia.dao.DaoPublicaciones;
import co.elpatio.infraestructura.persistencia.dao.DaoReservas;
import co.elpatio.infraestructura.persistencia.dao.DaoUsuarios;
import co.elpatio.infraestructura.persistencia.dao.DaoZonasDomicilio;
import co.elpatio.infraestructura.persistencia.filas.FilaAjustes;
import co.elpatio.infraestructura.persistencia.filas.FilaCategoria;
import co.elpatio.infraestructura.persistencia.filas.FilaCierreCaja;
import co.elpatio.infraestructura.persistencia.filas.FilaItemCarta;
import co.elpatio.infraestructura.persistencia.filas.FilaMesa;
import co.elpatio.infraestructura.persistencia.filas.FilaOrden;
import co.elpatio.infraestructura.persistencia.filas.FilaPago;
import co.elpatio.infraestructura.persistencia.filas.FilaPublicacion;
import co.elpatio.infraestructura.persistencia.filas.FilaReserva;
import co.elpatio.infraestructura.persistencia.filas.FilaUsuario;
import co.elpatio.infraestructura.persistencia.filas.FilaZonaDomicilio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Implementaciones de los puertos del dominio contra PostgreSQL.
 *
 * Cada adaptador se limita a traducir: no toma decisiones de negocio. Si algun
 * dia el motor cambia, este archivo es lo unico que hay que reescribir.
 */
public final class Adaptadores {

  private Adaptadores() {}

  // -------------------------------------------------------------------------

  @Repository
  public static class Usuarios implements Repositorios.DeUsuarios {
    private final DaoUsuarios dao;

    public Usuarios(DaoUsuarios dao) {
      this.dao = dao;
    }

    @Override
    public List<Usuario> listar() {
      return dao.findAll().stream()
          .map(FilaUsuario::aDominio)
          .sorted(Comparator.comparing(Usuario::getNombre))
          .toList();
    }

    @Override
    public Optional<Usuario> porId(String id) {
      return dao.findById(id).map(FilaUsuario::aDominio);
    }

    @Override
    public Optional<Usuario> porNombreDeUsuario(String usuario) {
      return dao.porNombreDeUsuario(usuario).map(FilaUsuario::aDominio);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
      return dao.save(FilaUsuario.deDominio(usuario)).aDominio();
    }

    @Override
    public boolean hayAlguno() {
      return dao.count() > 0;
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Mesas implements Repositorios.DeMesas {
    private final DaoMesas dao;

    public Mesas(DaoMesas dao) {
      this.dao = dao;
    }

    @Override
    public List<Mesa> listar() {
      return dao.findAllByOrderByNumeroAsc().stream().map(FilaMesa::aDominio).toList();
    }

    @Override
    public Optional<Mesa> porId(String id) {
      return dao.findById(id).map(FilaMesa::aDominio);
    }

    @Override
    public Mesa guardar(Mesa mesa) {
      return dao.save(FilaMesa.deDominio(mesa)).aDominio();
    }

    @Override
    public void eliminar(String id) {
      dao.deleteById(id);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Carta implements Repositorios.DeCarta {
    private final DaoCategorias daoCategorias;
    private final DaoItemsCarta daoItems;

    public Carta(DaoCategorias daoCategorias, DaoItemsCarta daoItems) {
      this.daoCategorias = daoCategorias;
      this.daoItems = daoItems;
    }

    @Override
    public List<CategoriaCarta> listarCategorias() {
      return daoCategorias.findAllByOrderByOrdenAsc().stream().map(FilaCategoria::aDominio).toList();
    }

    @Override
    public CategoriaCarta guardarCategoria(CategoriaCarta categoria) {
      return daoCategorias.save(FilaCategoria.deDominio(categoria)).aDominio();
    }

    @Override
    public List<ItemCarta> listarItems() {
      return daoItems.findAllByOrderByNombreAsc().stream().map(FilaItemCarta::aDominio).toList();
    }

    @Override
    public Optional<ItemCarta> porId(String id) {
      return daoItems.findById(id).map(FilaItemCarta::aDominio);
    }

    @Override
    public ItemCarta guardarItem(ItemCarta item) {
      return daoItems.save(FilaItemCarta.deDominio(item)).aDominio();
    }

    @Override
    public void eliminarItem(String id) {
      daoItems.deleteById(id);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Publicaciones implements Repositorios.DePublicaciones {
    private final DaoPublicaciones dao;

    public Publicaciones(DaoPublicaciones dao) {
      this.dao = dao;
    }

    @Override
    public List<Publicacion> listar() {
      return dao.findAllByOrderByOrdenAscCreadaEnDesc().stream()
          .map(FilaPublicacion::aDominio)
          .toList();
    }

    @Override
    public Optional<Publicacion> porId(String id) {
      return dao.findById(id).map(FilaPublicacion::aDominio);
    }

    @Override
    public Publicacion guardar(Publicacion publicacion) {
      return dao.save(FilaPublicacion.deDominio(publicacion)).aDominio();
    }

    @Override
    public void eliminar(String id) {
      dao.deleteById(id);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Ordenes implements Repositorios.DeOrdenes {
    private final DaoOrdenes dao;
    private final Reloj reloj;

    public Ordenes(DaoOrdenes dao, Reloj reloj) {
      this.dao = dao;
      this.reloj = reloj;
    }

    @Override
    public List<Orden> listar() {
      return dao.findAll().stream().map(FilaOrden::aDominio).toList();
    }

    @Override
    public Optional<Orden> porId(String id) {
      return dao.findById(id).map(FilaOrden::aDominio);
    }

    @Override
    public List<Orden> activas() {
      return dao.activas().stream().map(FilaOrden::aDominio).toList();
    }

    @Override
    public List<Orden> abiertasDesde(Instant desde) {
      return dao.findByAbiertaEnGreaterThanEqualOrderByAbiertaEnAsc(desde).stream()
          .map(FilaOrden::aDominio)
          .toList();
    }

    @Override
    public Orden guardar(Orden orden) {
      // El dia operativo se fija al abrir y no se recalcula: si la comanda se
      // cobra pasada la medianoche debe seguir contando para el cierre de la
      // noche en que se abrio.
      FilaOrden fila = dao.findById(orden.getId()).orElseGet(FilaOrden::new);
      LocalDate dia =
          fila.getDiaOperativo() != null ? fila.getDiaOperativo() : reloj.diaDe(orden.getAbiertaEn());
      return dao.save(fila.volcar(orden, dia)).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Pagos implements Repositorios.DePagos {
    private final DaoPagos dao;

    public Pagos(DaoPagos dao) {
      this.dao = dao;
    }

    @Override
    public List<Pago> listar() {
      return dao.findAll().stream().map(FilaPago::aDominio).toList();
    }

    @Override
    public Optional<Pago> porId(String id) {
      return dao.findById(id).map(FilaPago::aDominio);
    }

    @Override
    public List<Pago> entre(Instant desde, Instant hasta) {
      return dao.findByFechaHoraBetweenOrderByFechaHoraDesc(desde, hasta).stream()
          .map(FilaPago::aDominio)
          .toList();
    }

    @Override
    public Pago guardar(Pago pago) {
      return dao.save(FilaPago.deDominio(pago)).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Reservas implements Repositorios.DeReservas {
    private final DaoReservas dao;

    public Reservas(DaoReservas dao) {
      this.dao = dao;
    }

    @Override
    public List<Reserva> listar() {
      return dao.findAllByOrderByFechaHoraAsc().stream().map(FilaReserva::aDominio).toList();
    }

    @Override
    public Optional<Reserva> porId(String id) {
      return dao.findById(id).map(FilaReserva::aDominio);
    }

    @Override
    public Reserva guardar(Reserva reserva) {
      return dao.save(FilaReserva.deDominio(reserva)).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class ZonasDomicilio implements Repositorios.DeZonasDomicilio {
    private final DaoZonasDomicilio dao;

    public ZonasDomicilio(DaoZonasDomicilio dao) {
      this.dao = dao;
    }

    @Override
    public List<ZonaDomicilio> listar() {
      return dao.findAllByOrderByOrdenAsc().stream().map(FilaZonaDomicilio::aDominio).toList();
    }

    @Override
    public Optional<ZonaDomicilio> porId(String id) {
      return dao.findById(id).map(FilaZonaDomicilio::aDominio);
    }

    @Override
    public ZonaDomicilio guardar(ZonaDomicilio zona) {
      return dao.save(FilaZonaDomicilio.deDominio(zona)).aDominio();
    }

    @Override
    public void eliminar(String id) {
      dao.deleteById(id);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Cierres implements Repositorios.DeCierres {
    private final DaoCierres dao;

    public Cierres(DaoCierres dao) {
      this.dao = dao;
    }

    @Override
    public List<CierreCaja> listar() {
      return dao.findAllByOrderByFechaHoraDesc().stream().map(FilaCierreCaja::aDominio).toList();
    }

    @Override
    public CierreCaja guardar(CierreCaja cierre) {
      return dao.save(FilaCierreCaja.deDominio(cierre)).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class AjustesRepo implements Repositorios.DeAjustes {
    private final DaoAjustes dao;
    private final Reloj reloj;

    public AjustesRepo(DaoAjustes dao, Reloj reloj) {
      this.dao = dao;
      this.reloj = reloj;
    }

    private FilaAjustes fila() {
      return dao.findById(FilaAjustes.UNICA)
          .orElseThrow(() -> new IllegalStateException("La fila de ajustes no existe: revise las migraciones"));
    }

    @Override
    public Ajustes leer() {
      return fila().aDominio();
    }

    @Override
    public Ajustes guardar(Ajustes ajustes) {
      FilaAjustes fila = fila();
      fila.volcar(ajustes);
      return dao.save(fila).aDominio();
    }

    /**
     * El consecutivo se entrega con la fila bloqueada y dentro de la misma
     * transaccion que crea la comanda. Una secuencia de PostgreSQL seria mas
     * simple pero dejaria huecos cuando una transaccion se revierte, y el
     * consecutivo del comprobante no puede tener saltos.
     */
    @Override
    public int siguienteConsecutivo() {
      FilaAjustes fila = dao.bloquearParaConsecutivo(FilaAjustes.UNICA);
      LocalDate hoy = reloj.hoy();
      int siguiente = hoy.equals(fila.getFechaConsecutivo()) ? fila.getConsecutivoOrden() + 1 : 1;
      fila.setFechaConsecutivo(hoy);
      fila.setConsecutivoOrden(siguiente);
      dao.saveAndFlush(fila);
      return siguiente;
    }
  }
}

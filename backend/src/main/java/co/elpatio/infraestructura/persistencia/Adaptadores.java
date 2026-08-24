package co.elpatio.infraestructura.persistencia;

import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.caja.CierreCaja;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.cobro.Pago;
import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.institucional.ContenidoInstitucional;
import co.elpatio.dominio.pago.EstadoPagoOnline;
import co.elpatio.dominio.pago.PagoOnline;
import co.elpatio.dominio.pedido.ZonaDomicilio;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.pqr.FiltroPqr;
import co.elpatio.dominio.pqr.Radicado;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.reclutamiento.EstadoPostulacion;
import co.elpatio.dominio.reclutamiento.FiltroPostulaciones;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.dominio.reclutamiento.Postulacion;
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
import co.elpatio.infraestructura.persistencia.dao.DaoContenidoInstitucional;
import co.elpatio.infraestructura.persistencia.dao.DaoConversaciones;
import co.elpatio.infraestructura.persistencia.dao.DaoEnviosErp;
import co.elpatio.infraestructura.persistencia.dao.DaoPagos;
import co.elpatio.infraestructura.persistencia.dao.DaoPagosOnline;
import co.elpatio.infraestructura.persistencia.dao.DaoConsecutivosPqr;
import co.elpatio.infraestructura.persistencia.dao.DaoPostulaciones;
import co.elpatio.infraestructura.persistencia.dao.DaoSolicitudesPqr;
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
import co.elpatio.infraestructura.persistencia.filas.FilaContenidoInstitucional;
import co.elpatio.infraestructura.persistencia.filas.FilaConversacion;
import co.elpatio.infraestructura.persistencia.filas.FilaEnvioErp;
import co.elpatio.infraestructura.persistencia.filas.FilaPago;
import co.elpatio.infraestructura.persistencia.filas.FilaPagoOnline;
import co.elpatio.infraestructura.persistencia.filas.FilaConsecutivoPqr;
import co.elpatio.infraestructura.persistencia.filas.FilaPostulacion;
import co.elpatio.infraestructura.persistencia.filas.FilaSolicitudPqr;
import co.elpatio.infraestructura.persistencia.filas.FilaPublicacion;
import co.elpatio.infraestructura.persistencia.filas.FilaReserva;
import co.elpatio.infraestructura.persistencia.filas.FilaUsuario;
import co.elpatio.infraestructura.persistencia.filas.FilaZonaDomicilio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Implementaciones de los puertos del dominio contra PostgreSQL.
 *
 * Cada adaptador se limita a traducir: no toma decisiones de negocio. Si algun
 * dia el motor cambia, este archivo es lo unico que hay que reescribir.
 */
public final class Adaptadores {

  private Adaptadores() {}

  /**
   * Un filtro exacto como patron LIKE. Sin filtro, el comodin que lo acepta todo.
   *
   * Las bandejas de PQR y postulaciones combinan filtros opcionales, y el
   * `(:x is null or columna = :x)` de manual no funciona contra PostgreSQL: un
   * parametro que no toca ninguna columna no tiene tipo deducible y la consulta
   * falla entera antes de ejecutarse. Con LIKE, el tipo lo da la columna.
   */
  private static String comodinSi(String valor) {
    return valor == null || valor.isBlank() ? "%" : valor;
  }

  /**
   * El texto que escribio el usuario, como patron de busqueda.
   *
   * Se pasa a minusculas aqui y no en la consulta porque la consulta ya aplica
   * `lower()` a la columna: hacerlo tambien al parametro lo dejaria sin tipo.
   *
   * Los comodines que venga escribiendo el usuario se escapan con `!`, que es
   * el caracter declarado en el `escape` de la consulta. Sin eso, buscar «100%»
   * devuelve la tabla entera y un `_` suelto casa con cualquier letra.
   */
  /**
   * El inicio del rango, o el principio de los tiempos si no hay filtro.
   *
   * Por la misma razon que `comodinSi`: un parametro null no tiene tipo
   * deducible para PostgreSQL y tumba la consulta entera. Un instante concreto
   * comparado contra la columna si lo tiene.
   */
  private static Instant desdeOSiempre(LocalDate desde, ZoneId zona) {
    return desde == null ? Instant.EPOCH : desde.atStartOfDay(zona).toInstant();
  }

  /** El fin del rango, o una fecha que ninguna fila va a alcanzar. */
  private static Instant hastaOSiempre(LocalDate hasta, ZoneId zona) {
    return hasta == null
        ? LocalDate.of(3000, 1, 1).atStartOfDay(zona).toInstant()
        : hasta.plusDays(1).atStartOfDay(zona).toInstant();
  }

  private static String patronDeBusqueda(String busqueda) {
    if (busqueda == null || busqueda.isBlank()) return "%";
    String limpio =
        busqueda.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + limpio + "%";
  }

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
  public static class PagosOnline implements Repositorios.DePagosOnline {
    private final DaoPagosOnline dao;

    public PagosOnline(DaoPagosOnline dao) {
      this.dao = dao;
    }

    @Override
    public Optional<PagoOnline> porId(String id) {
      return dao.findById(id).map(FilaPagoOnline::aDominio);
    }

    @Override
    public Optional<PagoOnline> porReferencia(String referencia) {
      return dao.findByReferencia(referencia).map(FilaPagoOnline::aDominio);
    }

    @Override
    public List<PagoOnline> pendientesVencidosAntesDe(Instant instante) {
      return dao.findByEstadoAndExpiraEnBefore(EstadoPagoOnline.PENDIENTE.codigo(), instante).stream()
          .map(FilaPagoOnline::aDominio)
          .toList();
    }

    @Override
    public List<PagoOnline> creadosEntre(Instant desde, Instant hasta) {
      return dao.findByCreadaEnBetweenOrderByCreadaEnDesc(desde, hasta).stream()
          .map(FilaPagoOnline::aDominio)
          .toList();
    }

    @Override
    public PagoOnline guardar(PagoOnline pago) {
      return dao.save(FilaPagoOnline.deDominio(pago)).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class EnviosErp implements Repositorios.DeEnviosErp {
    private final DaoEnviosErp dao;

    public EnviosErp(DaoEnviosErp dao) {
      this.dao = dao;
    }

    @Override
    public Optional<EnvioErp> porId(String id) {
      return dao.findById(id).map(FilaEnvioErp::aDominio);
    }

    @Override
    public Optional<EnvioErp> porPago(String pagoId) {
      return dao.findByPagoId(pagoId).map(FilaEnvioErp::aDominio);
    }

    @Override
    public List<EnvioErp> pendientesListos(Instant ahora, int limite) {
      return dao.pendientesListos(ahora, Limit.of(limite)).stream()
          .map(FilaEnvioErp::aDominio)
          .toList();
    }

    @Override
    public List<EnvioErp> entre(Instant desde, Instant hasta) {
      return dao.entre(desde, hasta).stream().map(FilaEnvioErp::aDominio).toList();
    }

    /**
     * Guarda sobre la fila existente si la hay.
     *
     * Un `save` con la fila reconstruida desde cero funcionaria, pero perderia
     * las columnas que el dominio no expone el dia que se agregue alguna. Leer
     * y volcar deja esa puerta cerrada.
     */
    @Override
    public EnvioErp guardar(EnvioErp envio) {
      FilaEnvioErp fila = dao.findById(envio.getId()).orElseGet(FilaEnvioErp::new);
      fila.volcar(envio);
      return dao.save(fila).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Postulaciones implements Repositorios.DePostulaciones {
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final DaoPostulaciones dao;

    public Postulaciones(DaoPostulaciones dao) {
      this.dao = dao;
    }

    @Override
    public Optional<Postulacion> porId(String id) {
      return dao.findById(id).map(FilaPostulacion::aDominio);
    }

    /**
     * Traduce el filtro del dominio a lo que entiende Spring Data.
     *
     * Los enum viajan a la consulta como su `name()` porque asi se guardan en
     * la columna. Las fechas locales se convierten a instantes en la zona del
     * restaurante: quien filtra por «el 24 de agosto» quiere el dia del local,
     * no una ventana UTC que le corte la noche.
     */
    @Override
    public Pagina<Postulacion> buscar(FiltroPostulaciones filtro) {
      Page<FilaPostulacion> pagina =
          dao.buscar(
              // Patrones LIKE, no valores sueltos: es lo que permite a
              // PostgreSQL deducir el tipo del parámetro. Ver la nota del DAO.
              comodinSi(filtro.estado() == null ? null : filtro.estado().name()),
              comodinSi(filtro.cargo() == null ? null : filtro.cargo().name()),
              desdeOSiempre(filtro.desde(), ZONA),
              // Exclusivo por arriba: el dia `hasta` entra completo.
              hastaOSiempre(filtro.hasta(), ZONA),
              patronDeBusqueda(filtro.busqueda()),
              PageRequest.of(filtro.pagina(), filtro.tamano()));

      return new Pagina<>(
          pagina.getContent().stream().map(FilaPostulacion::aDominio).toList(),
          filtro.pagina(),
          filtro.tamano(),
          pagina.getTotalElements());
    }

    @Override
    public long sinRevisar() {
      return dao.countByEstado(EstadoPostulacion.RECIBIDA.name());
    }

    @Override
    public List<Postulacion> delDocumentoDesde(String numeroDocumento, Instant desde) {
      return dao.findByNumeroDocumentoAndFechaPostulacionAfter(numeroDocumento, desde).stream()
          .map(FilaPostulacion::aDominio)
          .toList();
    }

    @Override
    public List<Postulacion> entre(Instant desde, Instant hasta) {
      return dao.findByFechaPostulacionBetweenOrderByFechaPostulacionDesc(desde, hasta).stream()
          .map(FilaPostulacion::aDominio)
          .toList();
    }

    @Override
    public Postulacion guardar(Postulacion postulacion) {
      FilaPostulacion fila = dao.findById(postulacion.getId()).orElseGet(FilaPostulacion::new);
      fila.volcar(postulacion);
      return dao.save(fila).aDominio();
    }

    @Override
    public void eliminar(String id) {
      dao.deleteById(id);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class SolicitudesPqr implements Repositorios.DeSolicitudesPqr {
    private static final ZoneId ZONA_PQR = ZoneId.of("America/Bogota");

    private final DaoSolicitudesPqr dao;
    private final DaoConsecutivosPqr consecutivos;

    public SolicitudesPqr(DaoSolicitudesPqr dao, DaoConsecutivosPqr consecutivos) {
      this.dao = dao;
      this.consecutivos = consecutivos;
    }

    @Override
    public Optional<SolicitudPqr> porId(String id) {
      return dao.findById(id).map(FilaSolicitudPqr::aDominio);
    }

    @Override
    public Optional<SolicitudPqr> porRadicadoYCorreo(String radicado, String email) {
      return dao.porRadicadoYCorreo(radicado, email).map(FilaSolicitudPqr::aDominio);
    }

    @Override
    public Pagina<SolicitudPqr> buscar(FiltroPqr filtro) {
      Page<FilaSolicitudPqr> pagina =
          dao.buscar(
              // Ver la nota del DAO: patrones LIKE, nunca valores sueltos.
              comodinSi(filtro.tipo() == null ? null : filtro.tipo().name()),
              comodinSi(filtro.estado() == null ? null : filtro.estado().name()),
              desdeOSiempre(filtro.desde(), ZONA_PQR),
              hastaOSiempre(filtro.hasta(), ZONA_PQR),
              patronDeBusqueda(filtro.busqueda()),
              PageRequest.of(filtro.pagina(), filtro.tamano()));

      return new Pagina<>(
          pagina.getContent().stream().map(FilaSolicitudPqr::aDominio).toList(),
          filtro.pagina(),
          filtro.tamano(),
          pagina.getTotalElements());
    }

    @Override
    public long abiertas() {
      return dao.abiertas();
    }

    @Override
    public List<SolicitudPqr> porVencerHasta(LocalDate limite) {
      return dao.porVencerHasta(limite).stream().map(FilaSolicitudPqr::aDominio).toList();
    }

    @Override
    public List<SolicitudPqr> entre(Instant desde, Instant hasta) {
      return dao.findByFechaRadicacionBetweenOrderByFechaRadicacionDesc(desde, hasta).stream()
          .map(FilaSolicitudPqr::aDominio)
          .toList();
    }

    @Override
    public SolicitudPqr guardar(SolicitudPqr solicitud) {
      FilaSolicitudPqr fila = dao.findById(solicitud.getId()).orElseGet(FilaSolicitudPqr::new);
      fila.volcar(solicitud);
      return dao.save(fila).aDominio();
    }

    /**
     * El siguiente radicado del año, bajo bloqueo del contador.
     *
     * El primer dia del año la fila no existe todavia y hay que crearla. Ese
     * insert puede perder una carrera contra otra peticion simultanea —las dos
     * ven que no hay fila y las dos insertan—, y la que pierde choca contra la
     * clave primaria. Se atrapa y se reintenta una vez: para entonces la fila
     * ya existe y el bloqueo funciona normalmente.
     */
    @Override
    public Radicado siguienteRadicado(int ano) {
      FilaConsecutivoPqr contador = consecutivos.bloquear(ano);
      if (contador == null) {
        try {
          contador = consecutivos.saveAndFlush(new FilaConsecutivoPqr(ano, 0));
        } catch (DataIntegrityViolationException carrera) {
          contador = consecutivos.bloquear(ano);
          if (contador == null) throw carrera;
        }
      }
      int siguiente = contador.getUltimo() + 1;
      contador.setUltimo(siguiente);
      consecutivos.saveAndFlush(contador);
      return new Radicado(ano, siguiente);
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class ContenidoInstitucionalRepo
      implements Repositorios.DeContenidoInstitucional {
    private final DaoContenidoInstitucional dao;

    public ContenidoInstitucionalRepo(DaoContenidoInstitucional dao) {
      this.dao = dao;
    }

    @Override
    public List<ContenidoInstitucional> listar() {
      return dao.findAllByOrderByOrdenAsc().stream()
          .map(FilaContenidoInstitucional::aDominio)
          .toList();
    }

    @Override
    public List<ContenidoInstitucional> visibles() {
      return dao.findByVisibleTrueOrderByOrdenAsc().stream()
          .map(FilaContenidoInstitucional::aDominio)
          .toList();
    }

    @Override
    public Optional<ContenidoInstitucional> porClave(String clave) {
      return dao.findById(clave).map(FilaContenidoInstitucional::aDominio);
    }

    /**
     * Guarda sobre la fila existente.
     *
     * Nunca crea claves nuevas por este camino: las secciones las define una
     * migracion, no el formulario. Si alguien manda una clave desconocida, el
     * servicio ya lo rechazo antes de llegar aqui.
     */
    @Override
    public ContenidoInstitucional guardar(ContenidoInstitucional contenido) {
      FilaContenidoInstitucional fila =
          dao.findById(contenido.getClave()).orElseGet(FilaContenidoInstitucional::new);
      fila.volcar(contenido);
      return dao.save(fila).aDominio();
    }
  }

  // -------------------------------------------------------------------------

  @Repository
  public static class Conversaciones implements Repositorios.DeConversaciones {
    private final DaoConversaciones dao;

    public Conversaciones(DaoConversaciones dao) {
      this.dao = dao;
    }

    @Override
    public Optional<Conversacion> porId(String id) {
      return dao.findById(id).map(FilaConversacion::aDominio);
    }

    @Override
    public Optional<Conversacion> abiertaPara(Canal canal, String identificadorExterno) {
      return dao
          .findByCanalAndIdentificadorExternoOrderByIniciadaEnDesc(canal.codigo(), identificadorExterno)
          .stream()
          .map(FilaConversacion::aDominio)
          .filter(c -> !c.getEstado().esFinal())
          .findFirst();
    }

    @Override
    public Conversacion guardar(Conversacion conversacion) {
      return dao.save(FilaConversacion.deDominio(conversacion)).aDominio();
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
    private final DaoOrdenes ordenes;
    private final Reloj reloj;

    public AjustesRepo(DaoAjustes dao, DaoOrdenes ordenes, Reloj reloj) {
      this.dao = dao;
      this.ordenes = ordenes;
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
     *
     * El numero sale del mayor entre lo que dice el contador y lo que de verdad
     * hay en la base. Fiarse solo del contador costaba caro: cuando el dia
     * cambiaba volvia a 1, y si ese dia ya tenia comandas —una real que
     * sobrevivio al retiro de los datos de demostracion, un respaldo
     * restaurado— la llave (dia_operativo, numero) rechazaba el insert. En el
     * arranque eso tumbaba el sistema entero; en plena venta habria dejado al
     * restaurante sin poder abrir una comanda.
     */
    @Override
    public int siguienteConsecutivo() {
      FilaAjustes fila = dao.bloquearParaConsecutivo(FilaAjustes.UNICA);
      LocalDate hoy = reloj.hoy();
      int contador = hoy.equals(fila.getFechaConsecutivo()) ? fila.getConsecutivoOrden() : 0;
      int siguiente = Math.max(contador, ordenes.maximoNumeroDe(hoy)) + 1;
      fila.setFechaConsecutivo(hoy);
      fila.setConsecutivoOrden(siguiente);
      dao.saveAndFlush(fila);
      return siguiente;
    }
  }
}

package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.comanda.CargoAdicional;
import co.elpatio.dominio.comanda.EstadoOrden;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.cobro.MetodoPago;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.pedido.ClienteExterno;
import co.elpatio.dominio.pedido.EstadoPedido;
import co.elpatio.dominio.pedido.TipoPedido;
import co.elpatio.dominio.pedido.UbicacionEntrega;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fila de la tabla `ordenes`, con sus productos y sus cargos.
 *
 * La comanda es un agregado: sus productos no tienen vida propia y siempre se
 * leen y se guardan con ella, por eso van en cascada con `orphanRemoval`. Es la
 * misma forma que tenia en localStorage, donde `Orden` traia su arreglo `items`
 * adentro, y conservarla evita que el frontend tenga que armar nada.
 */
@Entity
@Table(name = "ordenes")
public class FilaOrden {

  @Id private String id;

  @Column(name = "mesa_id")
  private String mesaId;

  @Column(name = "mesero_id")
  private String meseroId;

  private int numero;

  /**
   * Dia al que pertenece el consecutivo. Se guarda calculado en la zona del
   * restaurante: una comanda de las 11 de la noche es del cierre de ese dia, y
   * derivarlo de `abierta_en` en UTC la mandaria al dia siguiente.
   */
  @Column(name = "dia_operativo")
  private LocalDate diaOperativo;

  private String estado;
  private int comensales;

  @Column(name = "abierta_en")
  private Instant abiertaEn;

  @Column(name = "cerrada_en")
  private Instant cerradaEn;

  private String notas;

  @Column(name = "motivo_anulacion")
  private String motivoAnulacion;

  @Column(name = "orden_reemplazo_id")
  private String ordenReemplazoId;

  // --- Canal del pedido -----------------------------------------------------

  private String tipo;

  @Column(name = "estado_pedido")
  private String estadoPedido;

  @Column(name = "cliente_nombre")
  private String clienteNombre;

  @Column(name = "cliente_telefono")
  private String clienteTelefono;

  @Column(name = "cliente_direccion")
  private String clienteDireccion;

  @Column(name = "cliente_barrio")
  private String clienteBarrio;

  @Column(name = "cliente_latitud")
  private Double clienteLatitud;

  @Column(name = "cliente_longitud")
  private Double clienteLongitud;

  @Column(name = "ubicacion_precision_metros")
  private Integer ubicacionPrecisionMetros;

  @Column(name = "zona_domicilio_id")
  private String zonaDomicilioId;

  @Column(name = "costo_envio")
  private long costoEnvio;

  @Column(name = "metodo_pago_previsto")
  private String metodoPagoPrevisto;

  @Column(name = "minutos_estimados")
  private Integer minutosEstimados;

  private String repartidor;

  @Column(name = "repartidor_id")
  private String repartidorId;

  @Column(name = "motivo_rechazo")
  private String motivoRechazo;

  @Column(name = "recibido_en")
  private Instant recibidoEn;

  /**
   * `nullable = false` no es decorativo: sin el, Hibernate inserta el hijo con
   * la clave foranea vacia y la rellena con un UPDATE posterior, que choca
   * contra el NOT NULL de la tabla. Declarandola obligatoria, la columna entra
   * en el INSERT y ademas se ahorra una escritura por producto.
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "orden_id", nullable = false)
  @OrderBy("posicion ASC")
  private List<FilaItemOrden> items = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "orden_id", nullable = false)
  @OrderBy("agregadoEn ASC")
  private List<FilaCargoAdicional> cargos = new ArrayList<>();

  public Orden aDominio() {
    Orden orden = new Orden();
    orden.setId(id);
    orden.setMesaId(mesaId);
    orden.setMeseroId(meseroId);
    orden.setNumero(numero);
    orden.setEstado(EstadoOrden.de(estado));
    orden.setComensales(comensales);
    orden.setAbiertaEn(abiertaEn);
    orden.setCerradaEn(cerradaEn);
    orden.setNotas(notas);
    orden.setMotivoAnulacion(motivoAnulacion);
    orden.setOrdenReemplazoId(ordenReemplazoId);
    orden.setTipo(tipo == null ? TipoPedido.MESA : TipoPedido.de(tipo));
    orden.setEstadoPedido(estadoPedido == null ? null : EstadoPedido.de(estadoPedido));
    // El cliente solo existe si hay algo que guardar: una orden de mesa no
    // lleva datos de contacto y no tiene por que cargar un objeto vacio.
    orden.setCliente(
        clienteNombre == null && clienteTelefono == null
            ? null
            : new ClienteExterno(clienteNombre, clienteTelefono, clienteDireccion, clienteBarrio));
    // O estan las dos coordenadas o no hay ubicacion: media coordenada no ubica.
    orden.setUbicacion(
        clienteLatitud == null || clienteLongitud == null
            ? null
            : new UbicacionEntrega(clienteLatitud, clienteLongitud, ubicacionPrecisionMetros));
    orden.setZonaDomicilioId(zonaDomicilioId);
    orden.setCostoEnvio(costoEnvio);
    orden.setMetodoPagoPrevisto(
        metodoPagoPrevisto == null ? null : MetodoPago.de(metodoPagoPrevisto));
    orden.setMinutosEstimados(minutosEstimados);
    orden.setRepartidor(repartidor);
    orden.setRepartidorId(repartidorId);
    orden.setMotivoRechazo(motivoRechazo);
    orden.setRecibidoEn(recibidoEn);
    orden.setItems(new ArrayList<>(items.stream().map(FilaItemOrden::aDominio).toList()));
    orden.setCargosAdicionales(
        new ArrayList<>(cargos.stream().map(FilaCargoAdicional::aDominio).toList()));
    return orden;
  }

  /**
   * Vuelca el estado del dominio sobre la fila.
   *
   * Se reutiliza la instancia recibida en lugar de crear una nueva para que
   * Hibernate siga viendo la misma entidad gestionada y calcule un UPDATE en
   * vez de intentar un INSERT sobre una clave que ya existe.
   */
  public FilaOrden volcar(Orden orden, LocalDate diaOperativo) {
    this.id = orden.getId();
    this.mesaId = orden.getMesaId();
    this.meseroId = orden.getMeseroId();
    this.numero = orden.getNumero();
    this.diaOperativo = diaOperativo;
    this.estado = orden.getEstado().codigo();
    this.comensales = orden.getComensales();
    this.abiertaEn = orden.getAbiertaEn();
    this.cerradaEn = orden.getCerradaEn();
    this.notas = orden.getNotas();
    this.motivoAnulacion = orden.getMotivoAnulacion();
    this.ordenReemplazoId = orden.getOrdenReemplazoId();
    this.tipo = orden.getTipo().codigo();
    this.estadoPedido = orden.getEstadoPedido() == null ? null : orden.getEstadoPedido().codigo();
    ClienteExterno cliente = orden.getCliente();
    this.clienteNombre = cliente == null ? null : cliente.nombre();
    this.clienteTelefono = cliente == null ? null : cliente.telefono();
    this.clienteDireccion = cliente == null ? null : cliente.direccion();
    this.clienteBarrio = cliente == null ? null : cliente.barrio();
    UbicacionEntrega ubicacion = orden.getUbicacion();
    this.clienteLatitud = ubicacion == null ? null : ubicacion.latitud();
    this.clienteLongitud = ubicacion == null ? null : ubicacion.longitud();
    this.ubicacionPrecisionMetros = ubicacion == null ? null : ubicacion.precisionMetros();
    this.zonaDomicilioId = orden.getZonaDomicilioId();
    this.costoEnvio = orden.getCostoEnvio();
    this.metodoPagoPrevisto =
        orden.getMetodoPagoPrevisto() == null ? null : orden.getMetodoPagoPrevisto().codigo();
    this.minutosEstimados = orden.getMinutosEstimados();
    this.repartidor = orden.getRepartidor();
    this.repartidorId = orden.getRepartidorId();
    this.motivoRechazo = orden.getMotivoRechazo();
    this.recibidoEn = orden.getRecibidoEn();

    // Se reconstruye la coleccion en su sitio: reasignar la lista rompe el
    // seguimiento de huerfanos y deja filas viejas colgando en la tabla.
    this.items.clear();
    List<ItemOrden> deDominio = orden.getItems();
    for (int i = 0; i < deDominio.size(); i++) {
      this.items.add(FilaItemOrden.deDominio(deDominio.get(i), i));
    }

    this.cargos.clear();
    for (CargoAdicional cargo : orden.getCargosAdicionales()) {
      this.cargos.add(FilaCargoAdicional.deDominio(cargo));
    }
    return this;
  }

  public String getId() { return id; }

  public LocalDate getDiaOperativo() { return diaOperativo; }
}

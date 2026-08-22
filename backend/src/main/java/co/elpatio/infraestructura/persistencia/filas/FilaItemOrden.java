package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.ModificadorSeleccionado;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;

/** Fila de la tabla `items_orden`. */
@Entity
@Table(name = "items_orden")
public class FilaItemOrden {

  @Id private String id;

  @Column(name = "item_carta_id")
  private String itemCartaId;

  private String nombre;

  @Column(name = "precio_unitario")
  private long precioUnitario;

  @Column(name = "precio_lista")
  private Long precioLista;

  private int cantidad;

  @Type(JsonType.class)
  @Column(name = "modificadores_seleccionados", columnDefinition = "jsonb")
  private List<ModificadorSeleccionado> modificadoresSeleccionados = new ArrayList<>();

  @Column(name = "nota_cocina")
  private String notaCocina;

  private String estado;
  private String destino;

  @Column(name = "enviado_en")
  private Instant enviadoEn;

  @Column(name = "listo_en")
  private Instant listoEn;

  @Column(name = "turno_envio")
  private int turnoEnvio;

  /** Orden en que el mesero fue dictando los platos. */
  private int posicion;

  public ItemOrden aDominio() {
    ItemOrden item = new ItemOrden();
    item.setId(id);
    item.setItemCartaId(itemCartaId);
    item.setNombre(nombre);
    item.setPrecioUnitario(precioUnitario);
    item.setPrecioLista(precioLista);
    item.setCantidad(cantidad);
    item.setModificadoresSeleccionados(
        modificadoresSeleccionados == null ? new ArrayList<>() : new ArrayList<>(modificadoresSeleccionados));
    item.setNotaCocina(notaCocina);
    item.setEstado(EstadoItem.de(estado));
    item.setDestino(Destino.de(destino));
    item.setEnviadoEn(enviadoEn);
    item.setListoEn(listoEn);
    item.setTurnoEnvio(turnoEnvio);
    return item;
  }

  public static FilaItemOrden deDominio(ItemOrden item, int posicion) {
    FilaItemOrden fila = new FilaItemOrden();
    fila.id = item.getId();
    fila.itemCartaId = item.getItemCartaId();
    fila.nombre = item.getNombre();
    fila.precioUnitario = item.getPrecioUnitario();
    fila.precioLista = item.getPrecioLista();
    fila.cantidad = item.getCantidad();
    fila.modificadoresSeleccionados = new ArrayList<>(item.getModificadoresSeleccionados());
    fila.notaCocina = item.getNotaCocina();
    fila.estado = item.getEstado().codigo();
    fila.destino = item.getDestino().codigo();
    fila.enviadoEn = item.getEnviadoEn();
    fila.listoEn = item.getListoEn();
    fila.turnoEnvio = item.getTurnoEnvio();
    fila.posicion = posicion;
    return fila;
  }
}

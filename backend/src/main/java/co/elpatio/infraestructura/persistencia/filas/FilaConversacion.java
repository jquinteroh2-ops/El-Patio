package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.conversacion.Conversacion;
import co.elpatio.dominio.conversacion.EstadoConversacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `conversaciones`. */
@Entity
@Table(name = "conversaciones")
public class FilaConversacion {

  @Id private String id;

  private String canal;

  @Column(name = "identificador_externo")
  private String identificadorExterno;

  private String estado;

  @Column(name = "pedido_id")
  private String pedidoId;

  @Column(name = "reserva_id")
  private String reservaId;

  @Column(name = "iniciada_en")
  private Instant iniciadaEn;

  @Column(name = "actualizada_en")
  private Instant actualizadaEn;

  @Column(name = "datos_contexto")
  private String datosContexto;

  public Conversacion aDominio() {
    Conversacion conversacion = new Conversacion();
    conversacion.setId(id);
    conversacion.setCanal(Canal.de(canal));
    conversacion.setIdentificadorExterno(identificadorExterno);
    conversacion.setEstado(EstadoConversacion.de(estado));
    conversacion.setPedidoId(pedidoId);
    conversacion.setReservaId(reservaId);
    conversacion.setIniciadaEn(iniciadaEn);
    conversacion.setActualizadaEn(actualizadaEn);
    conversacion.setDatosContexto(datosContexto);
    return conversacion;
  }

  public static FilaConversacion deDominio(Conversacion conversacion) {
    FilaConversacion fila = new FilaConversacion();
    fila.id = conversacion.getId();
    fila.canal = conversacion.getCanal().codigo();
    fila.identificadorExterno = conversacion.getIdentificadorExterno();
    fila.estado = conversacion.getEstado().codigo();
    fila.pedidoId = conversacion.getPedidoId();
    fila.reservaId = conversacion.getReservaId();
    fila.iniciadaEn = conversacion.getIniciadaEn();
    fila.actualizadaEn = conversacion.getActualizadaEn();
    fila.datosContexto = conversacion.getDatosContexto();
    return fila;
  }
}

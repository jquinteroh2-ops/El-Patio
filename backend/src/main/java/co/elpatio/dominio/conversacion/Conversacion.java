package co.elpatio.dominio.conversacion;

import co.elpatio.dominio.canal.Canal;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;

/**
 * El hilo de una charla con un cliente en un canal automatizado.
 *
 * No es el pedido ni la reserva: es el contexto de la charla que eventualmente
 * los produce. Por eso es un agregado propio, con su propio estado, y solo
 * referencia al pedido o la reserva por id una vez el cliente los confirma. Un
 * canal nuevo (el agente de voz, por ejemplo) reutiliza esta misma forma
 * cambiando solo el `canal` y el `identificadorExterno`.
 */
public class Conversacion {
  private String id;
  private Canal canal;

  /** El numero de telefono en WhatsApp, o lo que identifique la sesion en otro canal. */
  private String identificadorExterno;

  private EstadoConversacion estado = EstadoConversacion.INICIADA;

  /** Se llenan solo cuando el cliente confirma; antes de eso la charla no tiene pedido todavia. */
  private String pedidoId;

  private String reservaId;
  private Instant iniciadaEn;
  private Instant actualizadaEn;

  /**
   * Bolsillo libre para lo que el adaptador de cada canal necesite recordar
   * entre un mensaje y el siguiente (que producto va armando, en que paso del
   * formulario esta). El dominio no lo interpreta: es opaco a proposito, para
   * que cada canal guarde ahi la forma que le sirva sin que este agregado
   * tenga que conocer la de todos.
   */
  private String datosContexto;

  public Conversacion() {}

  /**
   * Avanza la charla a un nuevo paso.
   *
   * Una conversacion finalizada o expirada es un capitulo cerrado: si el
   * cliente vuelve a escribir, se abre una conversacion nueva, no se reanima
   * esta, para que el historial de cada hilo siga siendo consistente.
   */
  public void cambiarEstado(EstadoConversacion siguiente, Instant ahora) {
    if (estado.esFinal()) {
      throw new ReglaDeNegocioError("Esta conversación ya terminó");
    }
    estado = siguiente;
    actualizadaEn = ahora;
  }

  public void asociarPedido(String pedidoId, Instant ahora) {
    this.pedidoId = pedidoId;
    this.actualizadaEn = ahora;
  }

  public void asociarReserva(String reservaId, Instant ahora) {
    this.reservaId = reservaId;
    this.actualizadaEn = ahora;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Canal getCanal() { return canal; }
  public void setCanal(Canal canal) { this.canal = canal; }
  public String getIdentificadorExterno() { return identificadorExterno; }
  public void setIdentificadorExterno(String identificadorExterno) {
    this.identificadorExterno = identificadorExterno;
  }
  public EstadoConversacion getEstado() { return estado; }
  public void setEstado(EstadoConversacion estado) { this.estado = estado; }
  public String getPedidoId() { return pedidoId; }
  public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
  public String getReservaId() { return reservaId; }
  public void setReservaId(String reservaId) { this.reservaId = reservaId; }
  public Instant getIniciadaEn() { return iniciadaEn; }
  public void setIniciadaEn(Instant iniciadaEn) { this.iniciadaEn = iniciadaEn; }
  public Instant getActualizadaEn() { return actualizadaEn; }
  public void setActualizadaEn(Instant actualizadaEn) { this.actualizadaEn = actualizadaEn; }
  public String getDatosContexto() { return datosContexto; }
  public void setDatosContexto(String datosContexto) { this.datosContexto = datosContexto; }
}

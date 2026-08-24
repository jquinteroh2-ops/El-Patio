package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.erp.EnvioErp;
import co.elpatio.dominio.erp.EstadoEnvioErp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `erp_outbox`: una venta en camino al ERP externo. */
@Entity
@Table(name = "erp_outbox")
public class FilaEnvioErp {

  @Id private String id;

  @Column(name = "pago_id")
  private String pagoId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  private String estado;

  private int intentos;

  @Column(name = "proximo_intento")
  private Instant proximoIntento;

  private String payload;

  @Column(name = "respuesta_cruda")
  private String respuestaCruda;

  @Column(name = "documento_externo")
  private String documentoExterno;

  private String error;

  private String adaptador;

  @Column(name = "creado_en")
  private Instant creadoEn;

  @Column(name = "actualizado_en")
  private Instant actualizadoEn;

  public EnvioErp aDominio() {
    EnvioErp envio = new EnvioErp();
    envio.setId(id);
    envio.setPagoId(pagoId);
    envio.setIdempotencyKey(idempotencyKey);
    envio.setEstado(EstadoEnvioErp.de(estado));
    envio.setIntentos(intentos);
    envio.setProximoIntento(proximoIntento);
    envio.setPayload(payload);
    envio.setRespuestaCruda(respuestaCruda);
    envio.setDocumentoExterno(documentoExterno);
    envio.setError(error);
    envio.setAdaptador(adaptador);
    envio.setCreadoEn(creadoEn);
    envio.setActualizadoEn(actualizadoEn);
    return envio;
  }

  public static FilaEnvioErp deDominio(EnvioErp envio) {
    FilaEnvioErp fila = new FilaEnvioErp();
    fila.volcar(envio);
    return fila;
  }

  /** Vuelca el estado del dominio sobre esta fila, sin tocar la identidad. */
  public void volcar(EnvioErp envio) {
    this.id = envio.getId();
    this.pagoId = envio.getPagoId();
    this.idempotencyKey = envio.getIdempotencyKey();
    this.estado = envio.getEstado().name();
    this.intentos = envio.getIntentos();
    this.proximoIntento = envio.getProximoIntento();
    this.payload = envio.getPayload();
    this.respuestaCruda = envio.getRespuestaCruda();
    this.documentoExterno = envio.getDocumentoExterno();
    this.error = envio.getError();
    this.adaptador = envio.getAdaptador();
    this.creadoEn = envio.getCreadoEn();
    this.actualizadoEn = envio.getActualizadoEn();
  }

  public String getId() { return id; }
  public String getPagoId() { return pagoId; }
  public String getEstado() { return estado; }
  public Instant getCreadoEn() { return creadoEn; }
}

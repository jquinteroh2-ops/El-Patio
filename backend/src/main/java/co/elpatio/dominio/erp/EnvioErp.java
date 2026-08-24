package co.elpatio.dominio.erp;

import java.time.Instant;

/**
 * Una venta esperando su turno para irse al ERP.
 *
 * Es el registro de la bandeja de salida. Nace en la MISMA transaccion que
 * registra el pago, y esa simultaneidad es todo el punto: si la venta se
 * guardo, su envio existe; si la transaccion se revierte, no queda un envio
 * huerfano apuntando a un pago que nunca ocurrio. Escribir en la cola despues
 * de confirmar la transaccion abriria justo esa ventana, y las ventas que se
 * pierden por ahi son las que nadie encuentra hasta el cierre del mes.
 *
 * Lo que este objeto NO hace es hablar con nadie. No sabe si el ERP es
 * Globalsoft, ni si se le llega por HTTP, por archivo o digitando a mano. Solo
 * sabe en que va, cuantas veces se intento y cuando toca la proxima.
 */
public class EnvioErp {

  private String id;
  private String pagoId;
  private String idempotencyKey;
  private EstadoEnvioErp estado;
  private int intentos;
  private Instant proximoIntento;
  /** El cuerpo que se le mando al ERP, para poder auditar que se envio. */
  private String payload;
  /** Lo que contesto, sin interpretar. */
  private String respuestaCruda;
  /** El numero del documento del ERP, cuando confirma. */
  private String documentoExterno;
  private String error;
  /** Que adaptador lo atendio: rest, archivo o manual. */
  private String adaptador;
  private Instant creadoEn;
  private Instant actualizadoEn;

  public EnvioErp() {}

  /** Un envio recien encolado: pendiente, sin intentos y listo para salir ya. */
  public static EnvioErp encolar(
      String id, String pagoId, String idempotencyKey, String payload, Instant ahora) {
    EnvioErp envio = new EnvioErp();
    envio.id = id;
    envio.pagoId = pagoId;
    envio.idempotencyKey = idempotencyKey;
    envio.payload = payload;
    envio.estado = EstadoEnvioErp.PENDIENTE_ENVIO_ERP;
    envio.intentos = 0;
    envio.proximoIntento = ahora;
    envio.creadoEn = ahora;
    envio.actualizadoEn = ahora;
    return envio;
  }

  /** Sale hacia el adaptador. Se cuenta el intento antes de saber como termina. */
  public void marcarEnviado(String adaptador, Instant ahora) {
    this.estado = EstadoEnvioErp.ENVIADA_ERP;
    this.adaptador = adaptador;
    this.intentos += 1;
    this.actualizadoEn = ahora;
  }

  /** El ERP acepto. Es el unico final bueno y no se vuelve a tocar. */
  public void confirmar(ResultadoFacturacion resultado, Instant ahora) {
    this.estado = EstadoEnvioErp.FACTURADA_ERP;
    this.documentoExterno = resultado.numeroDocumento();
    this.respuestaCruda = resultado.respuestaCruda();
    this.error = null;
    this.proximoIntento = null;
    this.actualizadoEn = ahora;
  }

  /**
   * No salio. Vuelve a la cola si queda margen; si no, queda para que lo mire
   * una persona.
   *
   * El mensaje se guarda siempre, incluso cuando va a reintentar: cuando el
   * octavo intento falla, saber que los siete anteriores fallaron por lo mismo
   * es la diferencia entre arreglarlo y adivinar.
   */
  public void fallar(
      String motivo, String respuestaCruda, PoliticaReintentos politica, Instant ahora) {
    this.error = motivo;
    this.respuestaCruda = respuestaCruda;
    this.actualizadoEn = ahora;
    if (politica.quedanIntentos(intentos)) {
      this.estado = EstadoEnvioErp.PENDIENTE_ENVIO_ERP;
      this.proximoIntento = politica.proximoIntento(ahora, intentos);
    } else {
      this.estado = EstadoEnvioErp.ERROR_ERP;
      this.proximoIntento = null;
    }
  }

  /**
   * Lo devuelve a la cola por orden de un administrador.
   *
   * Reinicia el contador de intentos a proposito. Quien pulsa reintentar en la
   * pantalla de conciliacion normalmente acaba de arreglar la causa —levanto el
   * servidor, corrigio el codigo del producto— y merece la tanda completa, no
   * el unico intento que le quedaba a la anterior.
   */
  public void reencolar(Instant ahora) {
    this.estado = EstadoEnvioErp.PENDIENTE_ENVIO_ERP;
    this.intentos = 0;
    this.proximoIntento = ahora;
    this.error = null;
    this.actualizadoEn = ahora;
  }

  /** Si ya le toca salir. */
  public boolean debeIntentarse(Instant ahora) {
    return estado == EstadoEnvioErp.PENDIENTE_ENVIO_ERP
        && proximoIntento != null
        && !proximoIntento.isAfter(ahora);
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getPagoId() { return pagoId; }
  public void setPagoId(String pagoId) { this.pagoId = pagoId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String valor) { this.idempotencyKey = valor; }
  public EstadoEnvioErp getEstado() { return estado; }
  public void setEstado(EstadoEnvioErp estado) { this.estado = estado; }
  public int getIntentos() { return intentos; }
  public void setIntentos(int intentos) { this.intentos = intentos; }
  public Instant getProximoIntento() { return proximoIntento; }
  public void setProximoIntento(Instant valor) { this.proximoIntento = valor; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getRespuestaCruda() { return respuestaCruda; }
  public void setRespuestaCruda(String valor) { this.respuestaCruda = valor; }
  public String getDocumentoExterno() { return documentoExterno; }
  public void setDocumentoExterno(String valor) { this.documentoExterno = valor; }
  public String getError() { return error; }
  public void setError(String error) { this.error = error; }
  public String getAdaptador() { return adaptador; }
  public void setAdaptador(String adaptador) { this.adaptador = adaptador; }
  public Instant getCreadoEn() { return creadoEn; }
  public void setCreadoEn(Instant valor) { this.creadoEn = valor; }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public void setActualizadoEn(Instant valor) { this.actualizadoEn = valor; }
}

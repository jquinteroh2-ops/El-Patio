package co.elpatio.dominio.pqr;

import co.elpatio.dominio.calendario.FestivosColombia;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Una peticion, queja, reclamo, sugerencia o felicitacion de un cliente.
 *
 * <p><b>Aqui hay un termino que corre.</b> El Estatuto del Consumidor —Ley 1480
 * de 2011— obliga a atender los reclamos de consumidores dentro de un plazo, y
 * el plazo se cuenta en dias habiles. Por eso la fecha limite se calcula al
 * radicar y se guarda: si se calculara al consultar, cambiar el numero de dias
 * en la configuracion moveria hacia atras el vencimiento de solicitudes ya
 * radicadas, y el restaurante se encontraria de un dia para otro con quejas
 * vencidas que ayer estaban al dia.
 *
 * <p>Contexto propio. No comparte nada con reclutamiento ni con ventas: quien
 * se queja de un plato no es un aspirante ni un empleado.
 */
public class SolicitudPqr {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private String id;
  /** `PQR-AAAA-NNNNN`. Unico, sin saltos, y la referencia para consultar. */
  private String radicado;
  private TipoSolicitud tipo;

  private String nombreCompleto;
  /** El canal de respuesta. Sin correo no hay a donde contestar. */
  private String email;
  private String telefono;
  /** Cuando estuvo en el restaurante. Ayuda a ubicar el turno y la mesa. */
  private LocalDate fechaVisita;

  private String asunto;
  private String descripcion;
  /** Referencia del adjunto en el almacen, si mando alguno. */
  private String adjuntoRef;
  private String adjuntoNombreOriginal;

  private EstadoPqr estado = EstadoPqr.RADICADA;
  private Instant fechaRadicacion;
  /**
   * Hasta cuando hay para responder.
   *
   * Se congela al radicar. Nula en las felicitaciones, que no tienen termino.
   */
  private LocalDate fechaLimiteRespuesta;
  private Instant fechaRespuesta;
  private String respuesta;
  private String respondidoPor;

  private boolean autorizacionDatos;
  private Instant autorizacionFecha;
  private String autorizacionIp;

  private String notasInternas;
  private Instant actualizadoEn;

  public SolicitudPqr() {}

  /**
   * Radica una solicitud nueva.
   *
   * @param diasHabilesDePlazo cuantos dias habiles hay para responder. Viene de
   *     la configuracion del restaurante y no de una constante: el termino
   *     depende del tipo de solicitud y la norma puede cambiar.
   */
  public static SolicitudPqr radicar(
      String id,
      Radicado radicado,
      TipoSolicitud tipo,
      String nombreCompleto,
      String email,
      String telefono,
      LocalDate fechaVisita,
      String asunto,
      String descripcion,
      String adjuntoRef,
      String adjuntoNombreOriginal,
      boolean autorizacionDatos,
      String autorizacionIp,
      int diasHabilesDePlazo,
      Instant ahora) {

    if (!autorizacionDatos) {
      throw new ReglaDeNegocioError(
          "Sin autorización para el tratamiento de datos no se puede radicar la solicitud");
    }
    if (tipo == null) throw new ReglaDeNegocioError("Falta el tipo de solicitud");

    SolicitudPqr solicitud = new SolicitudPqr();
    solicitud.id = id;
    solicitud.radicado = radicado.toString();
    solicitud.tipo = tipo;
    solicitud.nombreCompleto = exigirTexto(nombreCompleto, "el nombre completo");
    solicitud.email = exigirCorreo(email);
    solicitud.telefono = recortar(telefono, 40);
    solicitud.fechaVisita = fechaVisita;
    solicitud.asunto = exigirTexto(recortarObligatorio(asunto, 120), "el asunto");
    solicitud.descripcion = exigirTexto(recortarObligatorio(descripcion, 2000), "la descripción");
    solicitud.adjuntoRef = adjuntoRef;
    solicitud.adjuntoNombreOriginal = adjuntoNombreOriginal;
    solicitud.estado = EstadoPqr.RADICADA;
    solicitud.fechaRadicacion = ahora;
    solicitud.fechaLimiteRespuesta =
        tipo.exigeRespuestaEnTermino()
            ? FestivosColombia.sumarDiasHabiles(ahora.atZone(ZONA).toLocalDate(), diasHabilesDePlazo)
            : null;
    solicitud.autorizacionDatos = true;
    solicitud.autorizacionFecha = ahora;
    solicitud.autorizacionIp = autorizacionIp;
    solicitud.actualizadoEn = ahora;
    return solicitud;
  }

  /**
   * Registra la respuesta que se le da al cliente.
   *
   * Pasa la solicitud a RESUELTA, que es lo que detiene el reloj del termino.
   * Se puede responder una solicitud ya resuelta —para corregir o ampliar— pero
   * la fecha de la primera respuesta NO se pisa: es la que demuestra que se
   * contesto dentro del plazo.
   */
  public void responder(String texto, String quienResponde, Instant ahora) {
    String contenido = exigirTexto(texto, "la respuesta");
    this.respuesta = contenido;
    this.respondidoPor = quienResponde;
    if (this.fechaRespuesta == null) this.fechaRespuesta = ahora;
    this.estado = EstadoPqr.RESUELTA;
    this.actualizadoEn = ahora;
  }

  public void cambiarEstado(EstadoPqr nuevo, Instant ahora) {
    if (nuevo == null) throw new ReglaDeNegocioError("Falta el estado");
    if (nuevo == EstadoPqr.RESUELTA && (respuesta == null || respuesta.isBlank())) {
      // Marcar como resuelta sin haber respondido es como se pierde el rastro
      // de una queja: el tablero queda en verde y el cliente sin contestacion.
      throw new ReglaDeNegocioError("Para darla por resuelta hay que registrar la respuesta");
    }
    this.estado = nuevo;
    this.actualizadoEn = ahora;
  }

  public void anotar(String notas, Instant ahora) {
    this.notasInternas = recortar(notas, 2000);
    this.actualizadoEn = ahora;
  }

  // -------------------------------------------------------------------------
  // El termino
  // -------------------------------------------------------------------------

  /** Si el plazo ya paso y todavia no se ha respondido. */
  public boolean estaVencida(LocalDate hoy) {
    return fechaLimiteRespuesta != null
        && estado.sigueCorriendo()
        && hoy.isAfter(fechaLimiteRespuesta);
  }

  /**
   * Dias habiles que quedan. Negativo si ya se paso.
   *
   * Se cuenta en habiles y no en corridos porque es como corre el termino: un
   * «faltan 3 dias» que en realidad son un viernes y un fin de semana engaña a
   * quien tiene que responder.
   */
  public int diasHabilesRestantes(LocalDate hoy) {
    if (fechaLimiteRespuesta == null) return 0;
    if (hoy.isAfter(fechaLimiteRespuesta)) {
      return -FestivosColombia.diasHabilesEntre(fechaLimiteRespuesta, hoy);
    }
    return FestivosColombia.diasHabilesEntre(hoy, fechaLimiteRespuesta);
  }

  /** Si conviene avisar de que se acerca el vencimiento. */
  public boolean estaPorVencer(LocalDate hoy, int diasDeAviso) {
    if (fechaLimiteRespuesta == null || !estado.sigueCorriendo()) return false;
    int restantes = diasHabilesRestantes(hoy);
    return restantes >= 0 && restantes <= diasDeAviso;
  }

  /**
   * Si se respondio dentro del plazo. Para el reporte de cumplimiento.
   *
   * Sin respuesta todavia, no se afirma que se incumplio: puede que aun quede
   * plazo. Solo cuenta como incumplida cuando la fecha limite ya paso.
   */
  public Boolean cumplioElPlazo(LocalDate hoy) {
    if (fechaLimiteRespuesta == null) return null;
    if (fechaRespuesta == null) {
      return hoy.isAfter(fechaLimiteRespuesta) ? Boolean.FALSE : null;
    }
    LocalDate diaDeRespuesta = fechaRespuesta.atZone(ZONA).toLocalDate();
    return !diaDeRespuesta.isAfter(fechaLimiteRespuesta);
  }

  // -------------------------------------------------------------------------

  private static String exigirTexto(String valor, String queEs) {
    if (valor == null || valor.isBlank()) throw new ReglaDeNegocioError("Falta " + queEs);
    return valor.trim();
  }

  /** Comprobacion minima: es el canal de respuesta, no un documento de identidad. */
  private static String exigirCorreo(String valor) {
    String correo = exigirTexto(valor, "el correo electrónico");
    int arroba = correo.indexOf('@');
    if (arroba <= 0 || correo.indexOf('.', arroba) < 0 || correo.endsWith(".")) {
      throw new ReglaDeNegocioError("El correo electrónico no parece válido");
    }
    return correo.toLowerCase();
  }

  private static String recortar(String texto, int maximo) {
    if (texto == null || texto.isBlank()) return null;
    String limpio = texto.trim();
    return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
  }

  /** Como `recortar`, pero deja pasar el nulo para que lo atrape `exigirTexto`. */
  private static String recortarObligatorio(String texto, int maximo) {
    if (texto == null) return null;
    String limpio = texto.trim();
    return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRadicado() { return radicado; }
  public void setRadicado(String radicado) { this.radicado = radicado; }
  public TipoSolicitud getTipo() { return tipo; }
  public void setTipo(TipoSolicitud tipo) { this.tipo = tipo; }
  public String getNombreCompleto() { return nombreCompleto; }
  public void setNombreCompleto(String valor) { this.nombreCompleto = valor; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getTelefono() { return telefono; }
  public void setTelefono(String telefono) { this.telefono = telefono; }
  public LocalDate getFechaVisita() { return fechaVisita; }
  public void setFechaVisita(LocalDate valor) { this.fechaVisita = valor; }
  public String getAsunto() { return asunto; }
  public void setAsunto(String asunto) { this.asunto = asunto; }
  public String getDescripcion() { return descripcion; }
  public void setDescripcion(String valor) { this.descripcion = valor; }
  public String getAdjuntoRef() { return adjuntoRef; }
  public void setAdjuntoRef(String valor) { this.adjuntoRef = valor; }
  public String getAdjuntoNombreOriginal() { return adjuntoNombreOriginal; }
  public void setAdjuntoNombreOriginal(String valor) { this.adjuntoNombreOriginal = valor; }
  public EstadoPqr getEstado() { return estado; }
  public void setEstado(EstadoPqr estado) { this.estado = estado; }
  public Instant getFechaRadicacion() { return fechaRadicacion; }
  public void setFechaRadicacion(Instant valor) { this.fechaRadicacion = valor; }
  public LocalDate getFechaLimiteRespuesta() { return fechaLimiteRespuesta; }
  public void setFechaLimiteRespuesta(LocalDate valor) { this.fechaLimiteRespuesta = valor; }
  public Instant getFechaRespuesta() { return fechaRespuesta; }
  public void setFechaRespuesta(Instant valor) { this.fechaRespuesta = valor; }
  public String getRespuesta() { return respuesta; }
  public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
  public String getRespondidoPor() { return respondidoPor; }
  public void setRespondidoPor(String valor) { this.respondidoPor = valor; }
  public boolean isAutorizacionDatos() { return autorizacionDatos; }
  public void setAutorizacionDatos(boolean valor) { this.autorizacionDatos = valor; }
  public Instant getAutorizacionFecha() { return autorizacionFecha; }
  public void setAutorizacionFecha(Instant valor) { this.autorizacionFecha = valor; }
  public String getAutorizacionIp() { return autorizacionIp; }
  public void setAutorizacionIp(String valor) { this.autorizacionIp = valor; }
  public String getNotasInternas() { return notasInternas; }
  public void setNotasInternas(String valor) { this.notasInternas = valor; }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public void setActualizadoEn(Instant valor) { this.actualizadoEn = valor; }
}

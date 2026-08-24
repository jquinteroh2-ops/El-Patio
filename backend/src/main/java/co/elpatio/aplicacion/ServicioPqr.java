package co.elpatio.aplicacion;

import co.elpatio.dominio.archivo.TipoDeArchivo;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.pqr.EstadoPqr;
import co.elpatio.dominio.pqr.FiltroPqr;
import co.elpatio.dominio.pqr.Radicado;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.pqr.TipoSolicitud;
import co.elpatio.dominio.puertos.AlmacenDeDocumentos;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reclutamiento.Pagina;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Peticiones, quejas, reclamos, sugerencias y felicitaciones de los clientes. */
@Service
public class ServicioPqr {

  private static final Logger registro = LoggerFactory.getLogger(ServicioPqr.class);

  /** Con cuántos días hábiles de antelación se avisa de un vencimiento. */
  private static final int DIAS_DE_AVISO = 3;

  private final Repositorios.DeSolicitudesPqr solicitudes;
  private final Repositorios.DeAjustes ajustes;
  private final AlmacenDeDocumentos almacen;
  private final NotificadorPorCorreo correo;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final String correoDelAdministrador;

  public ServicioPqr(
      Repositorios.DeSolicitudesPqr solicitudes,
      Repositorios.DeAjustes ajustes,
      AlmacenDeDocumentos almacen,
      NotificadorPorCorreo correo,
      GeneradorIds ids,
      Reloj reloj,
      @Value("${elpatio.reclutamiento.correo-administrador:}") String correoDelAdministrador) {
    this.solicitudes = solicitudes;
    this.ajustes = ajustes;
    this.almacen = almacen;
    this.correo = correo;
    this.ids = ids;
    this.reloj = reloj;
    this.correoDelAdministrador = correoDelAdministrador;
  }

  /** Lo que llega del formulario público. El adjunto es opcional. */
  public record DatosPqr(
      TipoSolicitud tipo,
      String nombreCompleto,
      String email,
      String telefono,
      LocalDate fechaVisita,
      String asunto,
      String descripcion,
      boolean autorizacionDatos,
      String ip,
      String adjuntoNombre,
      byte[] adjunto) {}

  // -------------------------------------------------------------------------
  // Radicar
  // -------------------------------------------------------------------------

  /**
   * Radica una solicitud y le entrega su número.
   *
   * Todo ocurre en UNA transacción, y eso es lo que hace que el radicado no
   * tenga saltos: el consecutivo se pide con la fila del contador bloqueada, y
   * si el insert de la solicitud falla, el número vuelve a quedar libre. Con una
   * secuencia de PostgreSQL —que no participa del rollback— cada fallo dejaría
   * un hueco, y un radicado con huecos no demuestra nada.
   */
  @Transactional
  public SolicitudPqr radicar(DatosPqr datos) {
    Instant ahora = reloj.ahora();

    // El adjunto es opcional, pero si viene se valida por sus bytes ANTES de
    // tocar el disco: al revés, cada rechazo dejaría un archivo huérfano.
    String referencia = null;
    if (datos.adjunto() != null && datos.adjunto().length > 0) {
      TipoDeArchivo.exigirPdfOImagen(datos.adjunto());
      referencia = almacen.guardar(datos.adjuntoNombre(), datos.adjunto());
    }

    int plazo = ajustes.leer().getDiasHabilesPqr();
    Radicado radicado = solicitudes.siguienteRadicado(reloj.hoy().getYear());

    SolicitudPqr solicitud;
    try {
      solicitud =
          SolicitudPqr.radicar(
              ids.nuevo("pqr"),
              radicado,
              datos.tipo(),
              datos.nombreCompleto(),
              datos.email(),
              datos.telefono(),
              datos.fechaVisita(),
              datos.asunto(),
              datos.descripcion(),
              referencia,
              datos.adjuntoNombre(),
              datos.autorizacionDatos(),
              datos.ip(),
              plazo,
              ahora);
    } catch (RuntimeException e) {
      // El archivo ya está en el disco y la solicitud no se pudo crear.
      if (referencia != null) almacen.borrar(referencia);
      throw e;
    }

    SolicitudPqr guardada = solicitudes.guardar(solicitud);
    avisarQueLlego(guardada);
    acusarRecibo(guardada);
    return guardada;
  }

  /**
   * El acuse de recibo para el cliente.
   *
   * Lleva el radicado porque es su comprobante: sin él no puede volver a
   * consultar. Nunca tumba la radicación —si el correo no sale, el número ya se
   * le mostró en pantalla—, pero sí queda registrado, porque un cliente que
   * cierra la pestaña sin apuntar el número y tampoco recibe el correo se queda
   * sin forma de hacer seguimiento.
   */
  private void acusarRecibo(SolicitudPqr solicitud) {
    try {
      String limite =
          solicitud.getFechaLimiteRespuesta() == null
              ? ""
              : "\n\nTenemos plazo para responderle hasta el "
                  + solicitud.getFechaLimiteRespuesta()
                  + ".";
      correo.enviar(
          solicitud.getEmail(),
          "Recibimos su solicitud " + solicitud.getRadicado(),
          "Hola, "
              + solicitud.getNombreCompleto()
              + ".\n\nRecibimos su "
              + solicitud.getTipo().etiqueta().toLowerCase()
              + " con el número de radicado "
              + solicitud.getRadicado()
              + ".\n\nGuarde este número: con él y con este correo puede consultar el estado de su"
              + " solicitud en nuestro sitio web."
              + limite);
    } catch (RuntimeException e) {
      registro.warn("No se pudo acusar recibo de la PQR {}", solicitud.getRadicado(), e);
    }
  }

  private void avisarQueLlego(SolicitudPqr solicitud) {
    if (correoDelAdministrador == null || correoDelAdministrador.isBlank()) return;
    try {
      correo.enviar(
          correoDelAdministrador,
          solicitud.getTipo().etiqueta() + " nueva: " + solicitud.getRadicado(),
          solicitud.getAsunto()
              + "\n\nDe "
              + solicitud.getNombreCompleto()
              + ".\nEntre al panel para atenderla.");
    } catch (RuntimeException e) {
      registro.warn("No se pudo avisar de la PQR {}", solicitud.getRadicado(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Consulta pública
  // -------------------------------------------------------------------------

  /**
   * Lo que ve el cliente al consultar su radicado.
   *
   * Deliberadamente escueto: el estado, las fechas y la respuesta. NO devuelve
   * el teléfono, ni la IP, ni las notas internas, ni el adjunto. Quien consulta
   * solo demostró conocer un radicado y un correo, y con eso no se le entrega
   * todo el expediente de vuelta.
   */
  public record ConsultaPublica(
      String radicado,
      TipoSolicitud tipo,
      String asunto,
      EstadoPqr estado,
      Instant fechaRadicacion,
      LocalDate fechaLimiteRespuesta,
      Instant fechaRespuesta,
      String respuesta) {}

  @Transactional(readOnly = true)
  public ConsultaPublica consultar(String radicado, String email) {
    // Se normaliza el radicado antes de buscar: el cliente lo copia de un correo
    // o lo escribe de memoria, y negarle la consulta por la caja de las letras
    // sería negársela por nada.
    String normalizado = Radicado.de(radicado).toString();

    SolicitudPqr solicitud =
        solicitudes
            .porRadicadoYCorreo(normalizado, email == null ? "" : email.trim())
            .orElseThrow(
                () ->
                    // El mismo mensaje para «no existe» y para «el correo no
                    // coincide». Distinguirlos le confirmaría a quien prueba
                    // radicados al azar cuáles existen.
                    new NoEncontradoError(
                        "No encontramos una solicitud con ese radicado y ese correo"));

    return new ConsultaPublica(
        solicitud.getRadicado(),
        solicitud.getTipo(),
        solicitud.getAsunto(),
        solicitud.getEstado(),
        solicitud.getFechaRadicacion(),
        solicitud.getFechaLimiteRespuesta(),
        solicitud.getFechaRespuesta(),
        solicitud.getRespuesta());
  }

  // -------------------------------------------------------------------------
  // Bandeja
  // -------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Pagina<SolicitudPqr> buscar(FiltroPqr filtro) {
    return solicitudes.buscar(filtro);
  }

  @Transactional(readOnly = true)
  public long abiertas() {
    return solicitudes.abiertas();
  }

  @Transactional(readOnly = true)
  public SolicitudPqr porId(String id) {
    return solicitudes.porId(id).orElseThrow(() -> new NoEncontradoError("Esa solicitud no existe"));
  }

  @Transactional
  public SolicitudPqr responder(String id, String texto, String quienResponde) {
    SolicitudPqr solicitud = porId(id);
    solicitud.responder(texto, quienResponde, reloj.ahora());
    SolicitudPqr guardada = solicitudes.guardar(solicitud);
    notificarRespuesta(guardada);
    return guardada;
  }

  /**
   * Le manda la respuesta al cliente.
   *
   * Va después de guardar, no antes: si el correo fallara y se hubiera enviado
   * primero, quedaría un cliente con una respuesta que el sistema no registró.
   */
  private void notificarRespuesta(SolicitudPqr solicitud) {
    try {
      correo.enviar(
          solicitud.getEmail(),
          "Respuesta a su solicitud " + solicitud.getRadicado(),
          "Hola, "
              + solicitud.getNombreCompleto()
              + ".\n\nSobre su solicitud "
              + solicitud.getRadicado()
              + " ("
              + solicitud.getAsunto()
              + "):\n\n"
              + solicitud.getRespuesta());
    } catch (RuntimeException e) {
      registro.warn("No se pudo notificar la respuesta de {}", solicitud.getRadicado(), e);
    }
  }

  @Transactional
  public SolicitudPqr cambiarEstado(String id, EstadoPqr estado) {
    SolicitudPqr solicitud = porId(id);
    solicitud.cambiarEstado(estado, reloj.ahora());
    return solicitudes.guardar(solicitud);
  }

  @Transactional
  public SolicitudPqr anotar(String id, String notas) {
    SolicitudPqr solicitud = porId(id);
    solicitud.anotar(notas, reloj.ahora());
    return solicitudes.guardar(solicitud);
  }

  /** El adjunto que mandó el cliente, para servirlo por el endpoint autenticado. */
  @Transactional(readOnly = true)
  public byte[] adjunto(String id) {
    SolicitudPqr solicitud = porId(id);
    if (solicitud.getAdjuntoRef() == null) {
      throw new NoEncontradoError("Esa solicitud no trae adjunto");
    }
    byte[] contenido = almacen.leer(solicitud.getAdjuntoRef());
    if (contenido.length == 0) {
      throw new NoEncontradoError("El archivo de esa solicitud ya no está disponible");
    }
    return contenido;
  }

  /** Las que están por vencer o ya vencieron, para el aviso del panel. */
  @Transactional(readOnly = true)
  public List<SolicitudPqr> porVencer() {
    LocalDate limite = LocalDate.from(reloj.hoy()).plusDays(DIAS_DE_AVISO * 2L);
    return solicitudes.porVencerHasta(limite);
  }

  @Transactional(readOnly = true)
  public List<SolicitudPqr> entre(Instant desde, Instant hasta) {
    return solicitudes.entre(desde, hasta);
  }

  /** El plazo configurado, para mostrarlo en el formulario público. */
  @Transactional(readOnly = true)
  public int plazoEnDiasHabiles() {
    return ajustes.leer().getDiasHabilesPqr();
  }

  /**
   * Falla si el tipo pedido no existe.
   *
   * Se usa desde el controlador público para dar un mensaje entendible en vez
   * del error de conversión de Spring, que menciona nombres de clases.
   */
  public static TipoSolicitud tipoDe(String valor) {
    try {
      return TipoSolicitud.de(valor);
    } catch (IllegalArgumentException e) {
      throw new ReglaDeNegocioError("Ese tipo de solicitud no existe");
    }
  }
}

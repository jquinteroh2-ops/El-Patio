package co.elpatio.aplicacion;

import co.elpatio.dominio.archivo.TipoDeArchivo;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeDocumentos;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.reclutamiento.CargoDeInteres;
import co.elpatio.dominio.reclutamiento.EstadoPostulacion;
import co.elpatio.dominio.reclutamiento.FiltroPostulaciones;
import co.elpatio.dominio.reclutamiento.Pagina;
import co.elpatio.dominio.reclutamiento.Postulacion;
import co.elpatio.dominio.reclutamiento.TipoDocumento;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Las hojas de vida que deja la gente por el sitio publico. */
@Service
public class ServicioPostulaciones {

  private static final Logger registro = LoggerFactory.getLogger(ServicioPostulaciones.class);

  private final Repositorios.DePostulaciones postulaciones;
  private final AlmacenDeDocumentos almacen;
  private final NotificadorPorCorreo correo;
  private final GeneradorIds ids;
  private final Reloj reloj;
  private final String correoDelAdministrador;
  private final int diasSinRepetir;

  public ServicioPostulaciones(
      Repositorios.DePostulaciones postulaciones,
      AlmacenDeDocumentos almacen,
      NotificadorPorCorreo correo,
      GeneradorIds ids,
      Reloj reloj,
      @Value("${elpatio.reclutamiento.correo-administrador:}") String correoDelAdministrador,
      @Value("${elpatio.reclutamiento.dias-sin-repetir:30}") int diasSinRepetir) {
    this.postulaciones = postulaciones;
    this.almacen = almacen;
    this.correo = correo;
    this.ids = ids;
    this.reloj = reloj;
    this.correoDelAdministrador = correoDelAdministrador;
    this.diasSinRepetir = diasSinRepetir;
  }

  /** Lo que llega del formulario. El archivo viene aparte, ya leido. */
  public record DatosPostulacion(
      String nombreCompleto,
      TipoDocumento tipoDocumento,
      String numeroDocumento,
      String email,
      String telefono,
      CargoDeInteres cargoInteres,
      String mensaje,
      boolean autorizacionDatos,
      String ip,
      String hojaDeVidaNombre,
      byte[] hojaDeVida) {}

  /**
   * Recibe una postulacion.
   *
   * El orden importa y es este a proposito: primero se valida todo lo barato
   * —duplicado, autorizacion, tipo de archivo—, y solo despues se escribe el
   * PDF al disco. Al reves, cada intento rechazado dejaria un archivo huerfano
   * en el volumen que nadie va a limpiar.
   */
  @Transactional
  public Postulacion recibir(DatosPostulacion datos) {
    if (datos.hojaDeVida() == null || datos.hojaDeVida().length == 0) {
      throw new ReglaDeNegocioError("Falta adjuntar la hoja de vida en PDF");
    }

    // El tipo se comprueba ANTES de tocar el disco: el almacén lo volvería a
    // mirar, pero para entonces ya habría que deshacer una escritura.
    TipoDeArchivo.exigirPdf(datos.hojaDeVida());

    exigirQueNoSeaRepetida(datos.numeroDocumento());

    String referencia = almacen.guardar(datos.hojaDeVidaNombre(), datos.hojaDeVida());

    Postulacion postulacion;
    try {
      postulacion =
          Postulacion.recibir(
              ids.nuevo("post"),
              datos.nombreCompleto(),
              datos.tipoDocumento(),
              datos.numeroDocumento(),
              datos.email(),
              datos.telefono(),
              datos.cargoInteres(),
              datos.mensaje(),
              referencia,
              datos.hojaDeVidaNombre(),
              datos.autorizacionDatos(),
              datos.ip(),
              reloj.ahora());
    } catch (RuntimeException e) {
      // El archivo ya está en el disco y la postulación no se pudo crear.
      // Sin esto queda un PDF con datos personales de alguien que no llegó a
      // registrarse, y nadie lo va a encontrar para borrarlo.
      almacen.borrar(referencia);
      throw e;
    }

    Postulacion guardada = postulaciones.guardar(postulacion);
    avisarAlAdministrador(guardada);
    return guardada;
  }

  /**
   * Rechaza el mismo documento dentro de la ventana configurada.
   *
   * No es sospecha de fraude: es que la gente vuelve a pulsar el botón cuando
   * la página tarda, y la bandeja termina con la misma hoja de vida cinco
   * veces. La ventana es configurable porque quien se postuló hace tres meses
   * y no tuvo respuesta tiene todo el derecho a volver a intentarlo.
   */
  private void exigirQueNoSeaRepetida(String numeroDocumento) {
    if (numeroDocumento == null || numeroDocumento.isBlank()) return;
    Instant desde = reloj.ahora().minus(Duration.ofDays(diasSinRepetir));
    List<Postulacion> recientes = postulaciones.delDocumentoDesde(numeroDocumento.trim(), desde);
    if (!recientes.isEmpty()) {
      throw new ReglaDeNegocioError(
          "Ya recibimos una hoja de vida con este documento hace poco. "
              + "La tenemos en cuenta; no hace falta enviarla de nuevo.");
    }
  }

  /**
   * Avisa que llegó alguien nuevo.
   *
   * Nunca tumba la postulación: que el correo no salga es un problema; que por
   * eso se pierda la hoja de vida de quien llenó el formulario es otro mucho
   * peor. El correo lleva el nombre y el cargo, no el resto de los datos: es un
   * aviso para que entren a la bandeja, no un volcado del expediente.
   */
  private void avisarAlAdministrador(Postulacion postulacion) {
    if (correoDelAdministrador == null || correoDelAdministrador.isBlank()) return;
    try {
      correo.enviar(
          correoDelAdministrador,
          "Nueva hoja de vida: " + postulacion.getCargoInteres().etiqueta(),
          postulacion.getNombreCompleto()
              + " se postuló para "
              + postulacion.getCargoInteres().etiqueta()
              + ".\n\nEntre al panel para ver la hoja de vida.");
    } catch (RuntimeException e) {
      registro.warn("No se pudo avisar de la postulación {}", postulacion.getId(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Bandeja
  // -------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Pagina<Postulacion> buscar(FiltroPostulaciones filtro) {
    return postulaciones.buscar(filtro);
  }

  @Transactional(readOnly = true)
  public long sinRevisar() {
    return postulaciones.sinRevisar();
  }

  @Transactional(readOnly = true)
  public Postulacion porId(String id) {
    return postulaciones
        .porId(id)
        .orElseThrow(() -> new NoEncontradoError("Esa postulación no existe"));
  }

  /** El PDF, para servirlo por el endpoint autenticado. */
  @Transactional(readOnly = true)
  public byte[] hojaDeVida(String id) {
    Postulacion postulacion = porId(id);
    byte[] contenido = almacen.leer(postulacion.getHojaDeVidaRef());
    if (contenido.length == 0) {
      // La fila existe y el archivo no. Pasa si el volumen se perdió en un
      // despliegue: conviene decirlo tal cual y no devolver un PDF vacío que
      // parezca corrupto.
      throw new NoEncontradoError("El archivo de esa hoja de vida ya no está disponible");
    }
    return contenido;
  }

  @Transactional
  public Postulacion cambiarEstado(String id, EstadoPostulacion estado) {
    Postulacion postulacion = porId(id);
    postulacion.cambiarEstado(estado, reloj.ahora());
    return postulaciones.guardar(postulacion);
  }

  @Transactional
  public Postulacion anotar(String id, String notas) {
    Postulacion postulacion = porId(id);
    postulacion.anotar(notas, reloj.ahora());
    return postulaciones.guardar(postulacion);
  }

  /**
   * Elimina la postulación y su archivo.
   *
   * Es el derecho de supresión de la Ley 1581, así que el borrado tiene que ser
   * de verdad y completo. El archivo se borra ANTES que la fila: si se hiciera
   * al revés y el borrado del archivo fallara, quedaría un PDF con los datos de
   * la persona en el volumen y ya sin ninguna fila que apunte a él, es decir,
   * imposible de encontrar para borrarlo después.
   */
  @Transactional
  public void eliminar(String id) {
    Postulacion postulacion = porId(id);
    almacen.borrar(postulacion.getHojaDeVidaRef());
    postulaciones.eliminar(id);
    registro.info("Postulación {} eliminada junto con su archivo", id);
  }

  /** Las del periodo, para el reporte. */
  @Transactional(readOnly = true)
  public List<Postulacion> entre(Instant desde, Instant hasta) {
    return postulaciones.entre(desde, hasta);
  }
}

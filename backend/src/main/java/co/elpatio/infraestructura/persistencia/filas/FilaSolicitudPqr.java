package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.pqr.EstadoPqr;
import co.elpatio.dominio.pqr.SolicitudPqr;
import co.elpatio.dominio.pqr.TipoSolicitud;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Fila de la tabla `solicitudes_pqr`. */
@Entity
@Table(name = "solicitudes_pqr")
public class FilaSolicitudPqr {

  @Id private String id;

  private String radicado;
  private String tipo;

  @Column(name = "nombre_completo")
  private String nombreCompleto;

  private String email;
  private String telefono;

  @Column(name = "fecha_visita")
  private LocalDate fechaVisita;

  private String asunto;
  private String descripcion;

  @Column(name = "adjunto_ref")
  private String adjuntoRef;

  @Column(name = "adjunto_nombre_original")
  private String adjuntoNombreOriginal;

  private String estado;

  @Column(name = "fecha_radicacion")
  private Instant fechaRadicacion;

  @Column(name = "fecha_limite_respuesta")
  private LocalDate fechaLimiteRespuesta;

  @Column(name = "fecha_respuesta")
  private Instant fechaRespuesta;

  private String respuesta;

  @Column(name = "respondido_por")
  private String respondidoPor;

  @Column(name = "autorizacion_datos")
  private boolean autorizacionDatos;

  @Column(name = "autorizacion_fecha")
  private Instant autorizacionFecha;

  @Column(name = "autorizacion_ip")
  private String autorizacionIp;

  @Column(name = "notas_internas")
  private String notasInternas;

  @Column(name = "actualizado_en")
  private Instant actualizadoEn;

  public SolicitudPqr aDominio() {
    SolicitudPqr s = new SolicitudPqr();
    s.setId(id);
    s.setRadicado(radicado);
    s.setTipo(TipoSolicitud.de(tipo));
    s.setNombreCompleto(nombreCompleto);
    s.setEmail(email);
    s.setTelefono(telefono);
    s.setFechaVisita(fechaVisita);
    s.setAsunto(asunto);
    s.setDescripcion(descripcion);
    s.setAdjuntoRef(adjuntoRef);
    s.setAdjuntoNombreOriginal(adjuntoNombreOriginal);
    s.setEstado(EstadoPqr.de(estado));
    s.setFechaRadicacion(fechaRadicacion);
    s.setFechaLimiteRespuesta(fechaLimiteRespuesta);
    s.setFechaRespuesta(fechaRespuesta);
    s.setRespuesta(respuesta);
    s.setRespondidoPor(respondidoPor);
    s.setAutorizacionDatos(autorizacionDatos);
    s.setAutorizacionFecha(autorizacionFecha);
    s.setAutorizacionIp(autorizacionIp);
    s.setNotasInternas(notasInternas);
    s.setActualizadoEn(actualizadoEn);
    return s;
  }

  public static FilaSolicitudPqr deDominio(SolicitudPqr s) {
    FilaSolicitudPqr fila = new FilaSolicitudPqr();
    fila.volcar(s);
    return fila;
  }

  public void volcar(SolicitudPqr s) {
    this.id = s.getId();
    this.radicado = s.getRadicado();
    this.tipo = s.getTipo().name();
    this.nombreCompleto = s.getNombreCompleto();
    this.email = s.getEmail();
    this.telefono = s.getTelefono();
    this.fechaVisita = s.getFechaVisita();
    this.asunto = s.getAsunto();
    this.descripcion = s.getDescripcion();
    this.adjuntoRef = s.getAdjuntoRef();
    this.adjuntoNombreOriginal = s.getAdjuntoNombreOriginal();
    this.estado = s.getEstado().name();
    this.fechaRadicacion = s.getFechaRadicacion();
    this.fechaLimiteRespuesta = s.getFechaLimiteRespuesta();
    this.fechaRespuesta = s.getFechaRespuesta();
    this.respuesta = s.getRespuesta();
    this.respondidoPor = s.getRespondidoPor();
    this.autorizacionDatos = s.isAutorizacionDatos();
    this.autorizacionFecha = s.getAutorizacionFecha();
    this.autorizacionIp = s.getAutorizacionIp();
    this.notasInternas = s.getNotasInternas();
    this.actualizadoEn = s.getActualizadoEn();
  }

  public String getId() { return id; }
}

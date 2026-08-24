package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.reclutamiento.CargoDeInteres;
import co.elpatio.dominio.reclutamiento.EstadoPostulacion;
import co.elpatio.dominio.reclutamiento.Postulacion;
import co.elpatio.dominio.reclutamiento.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila de la tabla `postulaciones`. */
@Entity
@Table(name = "postulaciones")
public class FilaPostulacion {

  @Id private String id;

  @Column(name = "nombre_completo")
  private String nombreCompleto;

  @Column(name = "tipo_documento")
  private String tipoDocumento;

  @Column(name = "numero_documento")
  private String numeroDocumento;

  private String email;
  private String telefono;

  @Column(name = "cargo_interes")
  private String cargoInteres;

  private String mensaje;

  @Column(name = "hoja_de_vida_ref")
  private String hojaDeVidaRef;

  @Column(name = "hoja_de_vida_nombre_original")
  private String hojaDeVidaNombreOriginal;

  private String estado;

  @Column(name = "fecha_postulacion")
  private Instant fechaPostulacion;

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

  public Postulacion aDominio() {
    Postulacion p = new Postulacion();
    p.setId(id);
    p.setNombreCompleto(nombreCompleto);
    p.setTipoDocumento(TipoDocumento.de(tipoDocumento));
    p.setNumeroDocumento(numeroDocumento);
    p.setEmail(email);
    p.setTelefono(telefono);
    p.setCargoInteres(CargoDeInteres.de(cargoInteres));
    p.setMensaje(mensaje);
    p.setHojaDeVidaRef(hojaDeVidaRef);
    p.setHojaDeVidaNombreOriginal(hojaDeVidaNombreOriginal);
    p.setEstado(EstadoPostulacion.de(estado));
    p.setFechaPostulacion(fechaPostulacion);
    p.setAutorizacionDatos(autorizacionDatos);
    p.setAutorizacionFecha(autorizacionFecha);
    p.setAutorizacionIp(autorizacionIp);
    p.setNotasInternas(notasInternas);
    p.setActualizadoEn(actualizadoEn);
    return p;
  }

  public static FilaPostulacion deDominio(Postulacion p) {
    FilaPostulacion fila = new FilaPostulacion();
    fila.volcar(p);
    return fila;
  }

  public void volcar(Postulacion p) {
    this.id = p.getId();
    this.nombreCompleto = p.getNombreCompleto();
    this.tipoDocumento = p.getTipoDocumento().name();
    this.numeroDocumento = p.getNumeroDocumento();
    this.email = p.getEmail();
    this.telefono = p.getTelefono();
    this.cargoInteres = p.getCargoInteres().name();
    this.mensaje = p.getMensaje();
    this.hojaDeVidaRef = p.getHojaDeVidaRef();
    this.hojaDeVidaNombreOriginal = p.getHojaDeVidaNombreOriginal();
    this.estado = p.getEstado().name();
    this.fechaPostulacion = p.getFechaPostulacion();
    this.autorizacionDatos = p.isAutorizacionDatos();
    this.autorizacionFecha = p.getAutorizacionFecha();
    this.autorizacionIp = p.getAutorizacionIp();
    this.notasInternas = p.getNotasInternas();
    this.actualizadoEn = p.getActualizadoEn();
  }

  public String getId() { return id; }
}

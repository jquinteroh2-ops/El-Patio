package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.sitio.FichaSitio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila unica de la tabla `ficha_sitio`. El id siempre es 1. */
@Entity
@Table(name = "ficha_sitio")
public class FilaFichaSitio {

  /** Identificador de la unica fila. Lo fija el esquema con un CHECK. */
  public static final int UNICA = 1;

  @Id private Integer id;

  private String direccion;
  private String ciudad;
  private String telefono;
  private String whatsapp;
  private String instagram;

  @Column(name = "actualizado_en")
  private Instant actualizadoEn;

  /** Sin el horario: esas filas viven en su propia tabla y las pega el adaptador. */
  public FichaSitio aDominio() {
    FichaSitio ficha = new FichaSitio();
    ficha.setDireccion(direccion);
    ficha.setCiudad(ciudad);
    ficha.setTelefono(telefono);
    ficha.setWhatsapp(whatsapp);
    ficha.setInstagram(instagram);
    ficha.setActualizadoEn(actualizadoEn);
    return ficha;
  }

  public void volcar(FichaSitio ficha) {
    this.id = UNICA;
    this.direccion = ficha.getDireccion();
    this.ciudad = ficha.getCiudad();
    this.telefono = ficha.getTelefono();
    this.whatsapp = ficha.getWhatsapp();
    this.instagram = ficha.getInstagram();
    this.actualizadoEn = ficha.getActualizadoEn();
  }
}

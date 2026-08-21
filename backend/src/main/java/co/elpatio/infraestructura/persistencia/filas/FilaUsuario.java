package co.elpatio.infraestructura.persistencia.filas;

import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla `usuarios`.
 *
 * Las clases de este paquete son el unico sitio del backend con anotaciones de
 * JPA: el dominio no sabe que existe una base de datos. La traduccion vive en
 * los metodos `aDominio` y `deDominio` para que quede al lado del esquema que
 * traduce y no se pierda en una clase de mapeo aparte.
 *
 * Los enumerados se guardan como texto en minuscula, el mismo literal que usa
 * tipos.ts, para que la base se pueda leer a ojo sin descifrar codigos.
 */
@Entity
@Table(name = "usuarios")
public class FilaUsuario {

  @Id private String id;

  private String nombre;

  private String rol;

  private String usuario;

  @Column(name = "clave_hash")
  private String claveHash;

  private boolean activo;

  private String correo;

  public Usuario aDominio() {
    Usuario dominio = new Usuario(id, nombre, Rol.de(rol), usuario, claveHash, activo);
    dominio.setCorreo(correo);
    return dominio;
  }

  public static FilaUsuario deDominio(Usuario usuario) {
    FilaUsuario fila = new FilaUsuario();
    fila.id = usuario.getId();
    fila.nombre = usuario.getNombre();
    fila.rol = usuario.getRol().codigo();
    fila.usuario = usuario.getUsuario();
    fila.claveHash = usuario.getClaveHash();
    fila.activo = usuario.isActivo();
    fila.correo = usuario.getCorreo();
    return fila;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getClaveHash() { return claveHash; }
}

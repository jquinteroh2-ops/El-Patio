package co.elpatio.dominio.personal;

/**
 * Personal del restaurante.
 *
 * `claveHash` no sale nunca de la capa de aplicacion: el DTO que viaja al
 * frontend deja el campo `clave` en blanco, porque la pantalla de usuarios solo
 * lo escribe y jamas necesita leerlo.
 */
public class Usuario {
  private String id;
  private String nombre;
  private Rol rol;
  private String usuario;
  private String claveHash;
  private boolean activo;

  /**
   * Correo de contacto, opcional.
   *
   * No se entra con el: la credencial es `usuario`. Este es por donde
   * administracion avisa, y por eso puede faltar sin que nada se rompa.
   */
  private String correo;

  public Usuario() {}

  public Usuario(String id, String nombre, Rol rol, String usuario, String claveHash, boolean activo) {
    this.id = id;
    this.nombre = nombre;
    this.rol = rol;
    this.usuario = usuario;
    this.claveHash = claveHash;
    this.activo = activo;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public Rol getRol() { return rol; }
  public void setRol(Rol rol) { this.rol = rol; }
  public String getUsuario() { return usuario; }
  public void setUsuario(String usuario) { this.usuario = usuario; }
  public String getClaveHash() { return claveHash; }
  public void setClaveHash(String claveHash) { this.claveHash = claveHash; }
  public boolean isActivo() { return activo; }
  public void setActivo(boolean activo) { this.activo = activo; }
  public String getCorreo() { return correo; }
  public void setCorreo(String correo) { this.correo = correo; }
}

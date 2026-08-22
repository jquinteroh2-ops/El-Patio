package co.elpatio.dominio.puertos;

/**
 * Donde viven las fotos.
 *
 * Es un puerto por la misma razon que la impresora lo es en el frontend: hoy
 * las fotos se guardan en un disco montado en Railway, manana pueden vivir en
 * un servicio de imagenes con CDN, y el dia que eso pase no hay que tocar ni el
 * dominio ni las pantallas. Solo cambia la implementacion.
 */
public interface AlmacenDeImagenes {

  /**
   * Guarda una imagen y devuelve el nombre con que quedo.
   *
   * Quien implemente esto DEBE reducir la imagen antes de guardarla. No es una
   * optimizacion: una foto de celular pesa varios megas, y una pagina con seis
   * de esas no abre con datos moviles, que es como la va a mirar la mayoria de
   * los clientes de un restaurante.
   *
   * @param nombreOriginal el nombre del archivo que subieron, solo para deducir
   *     el formato; nunca se usa como nombre de guardado, porque un nombre que
   *     viene de afuera puede traer rutas adentro.
   * @param contenido los bytes de la imagen.
   */
  String guardar(String nombreOriginal, byte[] contenido);

  /** Los bytes de una imagen ya guardada, o vacio si no existe. */
  byte[] leer(String nombre);

  /** Si existe. Se pregunta antes de responderle al navegador. */
  boolean existe(String nombre);

  /**
   * Borra una imagen.
   *
   * Se llama cuando la publicacion que la usaba cambio de foto o se elimino. No
   * falla si el archivo ya no esta: el objetivo es que no quede, no confirmar
   * que estaba.
   */
  void borrar(String nombre);

  /** El tipo de contenido con que se sirve. Depende de como se guardo. */
  String tipoDeContenido(String nombre);
}

package co.elpatio.dominio.puertos;

/**
 * Donde viven los archivos que suben los clientes y los aspirantes.
 *
 * Es el hermano de {@link AlmacenDeImagenes}, y vive aparte por una razon de
 * fondo: aquel REDUCE lo que guarda —una foto de celular se recomprime a 1600
 * px— y aqui eso seria destruir el documento. Una hoja de vida y el soporte de
 * una queja se guardan tal como llegaron, byte por byte, porque son la
 * evidencia de algo.
 *
 * <p><b>Lo que toda implementacion debe garantizar:</b>
 *
 * <ul>
 *   <li><b>El nombre de guardado lo pone el almacen, nunca el que subio.</b> Un
 *       nombre que viene de afuera puede traer rutas adentro —{@code
 *       ../../etc/passwd}— y escribir donde no debe. Se guarda con un UUID.
 *   <li><b>El tipo se valida por el contenido, no por la extension.</b>
 *       Renombrar un ejecutable a `.pdf` es lo primero que intenta cualquiera.
 *   <li><b>El binario no va a la base de datos.</b> Va al disco o a un servicio
 *       de objetos, y en la base queda solo la referencia.
 * </ul>
 */
public interface AlmacenDeDocumentos {

  /**
   * Guarda un documento y devuelve la referencia con que se recupera.
   *
   * @param nombreOriginal el nombre que traia, SOLO para guardarlo como dato y
   *     poder mostrarselo despues a quien lo recibe. Jamas se usa para construir
   *     la ruta.
   * @param contenido los bytes, tal como llegaron.
   */
  String guardar(String nombreOriginal, byte[] contenido);

  /** Los bytes de un documento guardado, o vacio si no esta. */
  byte[] leer(String referencia);

  boolean existe(String referencia);

  /**
   * Lo borra de verdad.
   *
   * No es limpieza: es el derecho de supresion de la Ley 1581. Cuando alguien
   * pide que borren sus datos, el archivo tiene que desaparecer, no quedar
   * huerfano en el disco con la fila de la base eliminada.
   *
   * No falla si ya no esta: el objetivo es que no quede, no confirmar que
   * estaba.
   */
  void borrar(String referencia);

  String tipoDeContenido(String referencia);
}

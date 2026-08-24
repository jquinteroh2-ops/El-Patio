package co.elpatio.dominio.puertos;

/**
 * El correo que sale del sistema.
 *
 * Hoy no hay ninguno: el proyecto nunca ha mandado un correo y no tiene
 * proveedor contratado. Este puerto existe igual, y con una implementacion que
 * no hace nada, para que el resto del sistema se escriba como si el correo
 * existiera. El dia que se contrate un proveedor se escribe un adaptador y no
 * se toca ni el dominio ni los servicios.
 *
 * <p><b>Lo que se le pide a toda implementacion:</b> que un fallo al enviar NO
 * tumbe la operacion que lo disparo. Que el correo de aviso no salga es un
 * problema; que por eso se pierda la postulacion de alguien que llenó el
 * formulario es otro mucho peor. Quien implemente esto registra el fallo y
 * sigue.
 */
public interface NotificadorPorCorreo {

  /**
   * Manda un correo.
   *
   * @param destinatario a quien.
   * @param asunto lo que se lee en la bandeja.
   * @param cuerpo texto plano. No HTML: lo que sale de aqui son avisos internos
   *     y acuses de recibo, y el texto plano llega a todas partes sin acabar en
   *     la carpeta de correo no deseado por una maquetacion mal cerrada.
   */
  void enviar(String destinatario, String asunto, String cuerpo);

  /** Si hay un proveedor detras. Sirve para no prometer en pantalla lo que no va a pasar. */
  boolean estaActivo();
}

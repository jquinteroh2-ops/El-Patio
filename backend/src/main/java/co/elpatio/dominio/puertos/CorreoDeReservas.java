package co.elpatio.dominio.puertos;

/**
 * El correo con que sale el aviso de una reserva nueva.
 *
 * Es un {@link NotificadorPorCorreo} como cualquier otro, envuelto en su propio
 * tipo para que Spring no lo confunda con el general: el aviso de una reserva
 * sale del buzón de reservas («Reservas El Patio»), no del de recursos
 * humanos. Si ese buzón todavía no tiene su llave de Zoho, se usa el general y
 * el aviso llega igual, solo que firmado por el otro buzón.
 */
public record CorreoDeReservas(NotificadorPorCorreo notificador) {}

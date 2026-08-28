package co.elpatio.dominio.canal;

/**
 * Rastro de como se origino un pedido o una reserva en un canal automatizado.
 *
 * Es deliberadamente generico y opcional. Lo llenaba el bot de WhatsApp con el
 * id de la conversacion; hoy nadie lo escribe, pero sigue aqui porque hay
 * pedidos viejos que lo traen y porque es donde el agente de voz dejaria la
 * transcripcion y la url del audio. Ningun proveedor concreto aparece en el
 * nombre de la clase ni de sus campos, para que agregar uno nuevo no toque
 * esta forma.
 */
public record MetadataOrigen(String idConversacion, String transcripcion, String urlAudio) {}

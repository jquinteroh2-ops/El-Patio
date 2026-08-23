package co.elpatio.dominio.canal;

/**
 * Rastro de como se origino un pedido o una reserva en un canal automatizado.
 *
 * Es deliberadamente generico y opcional: hoy lo llena WhatsApp con el id de
 * la conversacion, mas adelante el agente de voz lo llenara con la
 * transcripcion y la url del audio de la llamada. Ningun proveedor concreto
 * aparece en el nombre de la clase ni de sus campos, para que agregar uno
 * nuevo no toque esta forma.
 */
public record MetadataOrigen(String idConversacion, String transcripcion, String urlAudio) {}

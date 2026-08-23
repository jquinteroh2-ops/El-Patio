package co.elpatio.dominio.puertos;

import co.elpatio.dominio.comanda.Orden;

/**
 * Avisarle al cliente que paso con su pedido, por el mismo canal donde lo
 * hizo.
 *
 * `ServicioAnticipos` llama aqui sin saber si hay alguien del otro lado que
 * avisar: un pedido presencial no tiene a quien mandarle un mensaje, uno de
 * WhatsApp si. Quien implementa este puerto es quien decide, mirando
 * `orden.getCanal()`, si le toca hacer algo o quedarse callado.
 */
public interface NotificadorDeClientes {

  void avisarAnticipoConfirmado(Orden orden);

  void avisarAnticipoRechazado(Orden orden);
}

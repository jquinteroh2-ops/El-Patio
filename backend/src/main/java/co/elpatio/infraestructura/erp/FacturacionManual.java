package co.elpatio.infraestructura.erp;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El adaptador que funciona hoy, sin depender de que Globalsoft nos abra nada.
 *
 * No transmite. Deja la venta marcada para que una persona la digite en
 * Globalsoft, y eso es exactamente lo que el restaurante hace hoy a mano, solo
 * que ahora queda la lista de lo que falta digitar en vez de la memoria del
 * cajero.
 *
 * Es un NoOp deliberado y no un placeholder a medio hacer. La diferencia
 * importa: un placeholder que finge exito dejaria las ventas en FACTURADA_ERP
 * con un numero de documento inventado, y el contador cerraria el mes creyendo
 * que esta al dia. Este devuelve EN_ESPERA, que es la verdad, y la pantalla de
 * conciliacion lo muestra como pendiente de digitacion.
 *
 * Es tambien el adaptador por defecto. Si alguien se equivoca configurando, el
 * sistema cae en el que no puede hacer dano.
 */
@Component
@ConditionalOnProperty(name = "elpatio.erp.adaptador", havingValue = "manual", matchIfMissing = true)
public class FacturacionManual implements FacturacionExterna {

  @Override
  public ResultadoFacturacion emitirDocumento(VentaParaErp venta) {
    // Sin llamada externa no hay nada que pueda fallar, y por eso este metodo
    // no tiene try. Si algun dia alguien le agrega una llamada, que sea con la
    // misma regla del puerto: los fallos del ERP se devuelven, no se lanzan.
    return ResultadoFacturacion.enEspera(
        "Pendiente de digitacion en Globalsoft. Comanda n.º " + venta.numeroComanda());
  }

  @Override
  public String nombre() {
    return "manual";
  }
}

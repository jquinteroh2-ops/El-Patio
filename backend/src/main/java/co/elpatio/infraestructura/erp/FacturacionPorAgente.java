package co.elpatio.infraestructura.erp;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Globalsoft vive en un computador del restaurante, detras del router, y desde
 * la nube no hay forma de llamarlo. Con este adaptador la nube no lo intenta:
 * deja las ventas en la bandeja de salida y el agente instalado en ese
 * computador viene a buscarlas por {@code /api/agente-erp}, se las entrega a
 * Globalsoft alla adentro y contesta como le fue.
 *
 * Es el mismo camino de siempre visto desde el otro lado. Las preguntas salen
 * del restaurante hacia la nube, que es lo unico que el router deja pasar sin
 * que nadie abra puertos.
 */
@Component
@ConditionalOnProperty(name = "elpatio.erp.adaptador", havingValue = "agente")
public class FacturacionPorAgente implements FacturacionExterna {

  @Override
  public ResultadoFacturacion emitirDocumento(VentaParaErp venta) {
    // No deberia llamarse nunca: la tarea de cada minuto pregunta antes por
    // entregaDesdeLaNube(). Si algo la llama igual, se contesta la verdad —la
    // venta espera al agente— y no se inventa un documento.
    return ResultadoFacturacion.enEspera(
        "La entrega el agente del restaurante. Comanda n.º " + venta.numeroComanda());
  }

  @Override
  public String nombre() {
    return "agente";
  }

  @Override
  public boolean entregaDesdeLaNube() {
    return false;
  }
}

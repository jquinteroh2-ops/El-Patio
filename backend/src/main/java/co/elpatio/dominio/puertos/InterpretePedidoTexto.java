package co.elpatio.dominio.puertos;

import co.elpatio.dominio.interprete.ItemDelMenu;
import co.elpatio.dominio.interprete.ResultadoInterpretacion;
import java.util.List;

/**
 * Puerto de salida hacia lo que interpreta un pedido escrito en texto libre.
 *
 * Nombrado por lo que hace, no por el proveedor: hoy lo implementa Claude,
 * pero nada en el dominio ni en `OrquestadorWhatsApp` lo menciona. Quien
 * implemente esto recibe el menu real (solo id y nombre, nunca precios) y
 * SOLO puede devolver ids que esten en ese menu; el backend es quien busca el
 * precio real y arma el total.
 */
public interface InterpretePedidoTexto {

  ResultadoInterpretacion interpretar(String textoLibre, List<ItemDelMenu> menu);
}

package co.elpatio.dominio.cobro;

import co.elpatio.dominio.comanda.CargoAdicional;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import java.util.List;

/**
 * Armado de la cuenta. Traduccion literal de src/compartido/calculos.ts, para
 * que el total que el mesero ve en la tablet y el que se guarda en el pago sean
 * el mismo numero calculado por la misma regla.
 *
 * Reglas colombianas aplicadas aqui, en un solo lugar:
 *
 *  - A los restaurantes les aplica el Impuesto Nacional al Consumo, no IVA. Se
 *    calcula sobre el subtotal de alimentos y bebidas, antes de la propina, y
 *    no sobre los cargos adicionales, que no son consumo.
 *  - La propina es voluntaria: por defecto es cero y solo entra si alguien la
 *    agrego despues de consultarla con el cliente. Nunca se aplica sola.
 *  - El domicilio NO lleva INC ni propina. El INC grava el consumo de alimentos
 *    y bebidas, y llevar un pedido hasta una casa no es consumo; la propina
 *    retribuye el servicio de mesa, que en un domicilio no existe. El envio
 *    entra como una linea aparte, despues del impuesto.
 *
 * Todo se redondea a pesos enteros porque el peso colombiano no tiene
 * fracciones: un centavo que sobrevive al calculo termina descuadrando la caja.
 */
public final class CalculadoraCuenta {

  private CalculadoraCuenta() {}

  /** Subtotal de alimentos y bebidas. Los productos anulados no suman. */
  public static long subtotal(List<ItemOrden> items) {
    return items.stream().filter(ItemOrden::estaVigente).mapToLong(ItemOrden::precio).sum();
  }

  public static long totalCargos(List<CargoAdicional> cargos) {
    return cargos.stream().mapToLong(CargoAdicional::getValor).sum();
  }

  /** Cuenta sin propina: es el estado en que nace toda cuenta. */
  public static Cuenta calcular(Orden orden, int porcentajeInc) {
    return calcular(orden, porcentajeInc, 0, null);
  }

  /**
   * Arma la cuenta completa.
   *
   * `propinaManual` gana sobre el porcentaje cuando viene informada: el cajero
   * puede escribir el valor exacto que el cliente autorizo, que no siempre
   * coincide con un 5 o un 10 por ciento redondo.
   */
  public static Cuenta calcular(
      Orden orden, int porcentajeInc, int porcentajePropina, Long propinaManual) {
    long subtotal = subtotal(orden.getItems());
    long inc = Math.round((double) subtotal * porcentajeInc / 100);
    long cargos = totalCargos(orden.getCargosAdicionales());
    long envio = orden.getCostoEnvio();

    // El envio queda fuera de la base de la propina, igual que queda fuera de
    // la del impuesto: nadie propina por el domicilio que ya esta pagando.
    long propina =
        propinaManual != null
            ? Math.max(0, propinaManual)
            : Math.round((double) subtotal * porcentajePropina / 100);

    return new Cuenta(
        subtotal,
        inc,
        porcentajeInc,
        cargos,
        envio,
        propina,
        porcentajePropina,
        subtotal + inc + cargos + envio + propina);
  }

  // ---------------------------------------------------------------------------
  // Division de cuenta
  // ---------------------------------------------------------------------------

  /**
   * Reparte el total en partes iguales. El residuo de la division en pesos se
   * carga a la primera parte para que la suma cuadre exacta con el total.
   */
  public static long[] dividirEnPartesIguales(long total, int partes) {
    if (partes < 1) return new long[] {total};
    long base = Math.floorDiv(total, partes);
    long residuo = total - base * partes;
    long[] resultado = new long[partes];
    for (int i = 0; i < partes; i++) resultado[i] = i == 0 ? base + residuo : base;
    return resultado;
  }

  /**
   * Division por productos seleccionados: a la seleccion se le aplica su parte
   * proporcional de INC, cargos y propina, para que ninguna parte quede sin
   * impuesto y la suma de las partes sea el total.
   */
  public static ResultadoDivision dividirPorItems(
      List<ItemOrden> items, List<String> seleccionIds, Cuenta cuenta) {
    long subtotalSeleccion =
        items.stream()
            .filter(ItemOrden::estaVigente)
            .filter(i -> seleccionIds.contains(i.getId()))
            .mapToLong(ItemOrden::precio)
            .sum();

    if (cuenta.subtotal() == 0) return new ResultadoDivision(0, cuenta.total());

    double proporcion = (double) subtotalSeleccion / cuenta.subtotal();
    long valorSeleccion = Math.round(cuenta.total() * proporcion);
    return new ResultadoDivision(valorSeleccion, cuenta.total() - valorSeleccion);
  }

  public record ResultadoDivision(long valorSeleccion, long valorResto) {}
}

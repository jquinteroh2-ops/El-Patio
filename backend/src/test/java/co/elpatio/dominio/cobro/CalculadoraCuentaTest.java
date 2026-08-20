package co.elpatio.dominio.cobro;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.comanda.CargoAdicional;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.ModificadorSeleccionado;
import co.elpatio.dominio.comanda.Orden;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los mismos casos que resuelve src/compartido/calculos.ts.
 *
 * Es la prueba de que la traduccion del prototipo al backend no cambio ningun
 * numero: el total que el mesero veia en la tablet tiene que seguir siendo el
 * mismo peso por peso, porque es el que el cliente ya pago otras veces.
 */
class CalculadoraCuentaTest {

  private static ItemOrden item(long precioUnitario, int cantidad, ModificadorSeleccionado... mods) {
    ItemOrden item = new ItemOrden();
    item.setId("io" + precioUnitario + "_" + cantidad + "_" + mods.length);
    item.setItemCartaId("p1");
    item.setNombre("Plato");
    item.setPrecioUnitario(precioUnitario);
    item.setCantidad(cantidad);
    item.setDestino(Destino.COCINA);
    item.setEstado(EstadoItem.PENDIENTE);
    item.setModificadoresSeleccionados(List.of(mods));
    return item;
  }

  private static Orden orden(List<ItemOrden> items, List<CargoAdicional> cargos) {
    Orden orden = new Orden();
    orden.setId("ord1");
    orden.setMesaId("m1");
    orden.setMeseroId("u1");
    orden.setAbiertaEn(Instant.now());
    orden.setItems(new java.util.ArrayList<>(items));
    orden.setCargosAdicionales(new java.util.ArrayList<>(cargos));
    return orden;
  }

  private static Orden domicilio(List<ItemOrden> items, long costoEnvio) {
    Orden orden = orden(items, List.of());
    orden.setMesaId(null);
    orden.setTipo(co.elpatio.dominio.pedido.TipoPedido.DOMICILIO);
    orden.setEstadoPedido(co.elpatio.dominio.pedido.EstadoPedido.NUEVO);
    orden.setCostoEnvio(costoEnvio);
    return orden;
  }

  private static CargoAdicional cargo(long valor) {
    return new CargoAdicional("cg1", "Descorche", valor, "Katherine", Instant.now());
  }

  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Subtotal")
  class Subtotal {

    @Test
    @DisplayName("suma precio por cantidad")
    void sumaPrecioPorCantidad() {
      Orden orden = orden(List.of(item(22000, 2), item(45000, 1)), List.of());
      assertThat(CalculadoraCuenta.subtotal(orden.getItems())).isEqualTo(89000);
    }

    @Test
    @DisplayName("los modificadores suman antes de multiplicar por la cantidad")
    void modificadoresSumanPorUnidad() {
      // Dos cócteles con doble licor: (38000 + 9000) * 2, no 38000 * 2 + 9000.
      ItemOrden coctel = item(38000, 2, new ModificadorSeleccionado("Preparación", "Doble licor", 9000));
      assertThat(CalculadoraCuenta.subtotal(List.of(coctel))).isEqualTo(94000);
    }

    @Test
    @DisplayName("los productos anulados no suman")
    void anuladosNoSuman() {
      ItemOrden anulado = item(50000, 1);
      anulado.setEstado(EstadoItem.ANULADO);
      Orden orden = orden(List.of(item(30000, 1), anulado), List.of());
      assertThat(CalculadoraCuenta.subtotal(orden.getItems())).isEqualTo(30000);
    }
  }

  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Impuesto Nacional al Consumo")
  class Inc {

    @Test
    @DisplayName("es el 8% del subtotal de alimentos y bebidas")
    void ochoPorCientoDelSubtotal() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8);
      assertThat(cuenta.subtotal()).isEqualTo(100000);
      assertThat(cuenta.inc()).isEqualTo(8000);
      assertThat(cuenta.total()).isEqualTo(108000);
    }

    @Test
    @DisplayName("no se cobra sobre los cargos adicionales, que no son consumo")
    void noAplicaSobreCargos() {
      Cuenta cuenta =
          CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of(cargo(35000))), 8);
      assertThat(cuenta.inc()).isEqualTo(8000);
      assertThat(cuenta.cargosAdicionales()).isEqualTo(35000);
      assertThat(cuenta.total()).isEqualTo(143000);
    }

    @Test
    @DisplayName("se calcula antes de la propina y no sobre ella")
    void seCalculaAntesDeLaPropina() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8, 10, null);
      assertThat(cuenta.inc()).isEqualTo(8000);
      assertThat(cuenta.propina()).isEqualTo(10000);
      assertThat(cuenta.total()).isEqualTo(118000);
    }

    @Test
    @DisplayName("se redondea a pesos enteros")
    void redondeaAPesos() {
      // 22.500 * 8% = 1.800 exacto; 22.499 * 8% = 1.799,92 -> 1.800.
      assertThat(CalculadoraCuenta.calcular(orden(List.of(item(22499, 1)), List.of()), 8).inc())
          .isEqualTo(1800);
    }

    @Test
    @DisplayName("un establecimiento sin INC deja el impuesto en cero")
    void porcentajeCeroNoCobra() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(80000, 1)), List.of()), 0);
      assertThat(cuenta.inc()).isZero();
      assertThat(cuenta.total()).isEqualTo(80000);
    }
  }

  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Propina")
  class Propina {

    @Test
    @DisplayName("es cero mientras nadie la autorice: nunca se aplica sola")
    void porDefectoEsCero() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8);
      assertThat(cuenta.propina()).isZero();
      assertThat(cuenta.porcentajePropina()).isZero();
      assertThat(cuenta.total()).isEqualTo(108000);
    }

    @Test
    @DisplayName("el porcentaje se aplica sobre el subtotal, no sobre el total con INC")
    void porcentajeSobreElSubtotal() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8, 10, null);
      // 10% de 100.000 y no de 108.000.
      assertThat(cuenta.propina()).isEqualTo(10000);
    }

    @Test
    @DisplayName("no se calcula sobre los cargos adicionales")
    void noAplicaSobreCargos() {
      Cuenta cuenta =
          CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of(cargo(80000))), 8, 10, null);
      assertThat(cuenta.propina()).isEqualTo(10000);
      assertThat(cuenta.total()).isEqualTo(198000);
    }

    @Test
    @DisplayName("un valor manual gana sobre el porcentaje sugerido")
    void manualGanaSobrePorcentaje() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8, 10, 7000L);
      assertThat(cuenta.propina()).isEqualTo(7000);
      assertThat(cuenta.total()).isEqualTo(115000);
    }

    @Test
    @DisplayName("una propina manual negativa se trata como cero")
    void manualNegativaEsCero() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8, 0, -5000L);
      assertThat(cuenta.propina()).isZero();
    }

    @Test
    @DisplayName("una propina manual en cero no la reemplaza por el porcentaje")
    void manualEnCeroSeRespeta() {
      // El cliente dijo que no: el 10% sugerido no puede volver a colarse.
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(100000, 1)), List.of()), 8, 10, 0L);
      assertThat(cuenta.propina()).isZero();
      assertThat(cuenta.total()).isEqualTo(108000);
    }
  }

  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Domicilio")
  class Domicilio {

    @Test
    @DisplayName("el envío no causa INC: llevar un pedido no es consumo")
    void elEnvioNoCausaInc() {
      Cuenta cuenta = CalculadoraCuenta.calcular(domicilio(List.of(item(50000, 1)), 7000), 8);
      assertThat(cuenta.subtotal()).isEqualTo(50000);
      // 8% de 50.000, no de 57.000.
      assertThat(cuenta.inc()).isEqualTo(4000);
      assertThat(cuenta.costoEnvio()).isEqualTo(7000);
      assertThat(cuenta.total()).isEqualTo(61000);
    }

    @Test
    @DisplayName("el envío no entra en la base de la propina")
    void elEnvioNoEntraEnLaPropina() {
      Cuenta cuenta = CalculadoraCuenta.calcular(domicilio(List.of(item(50000, 1)), 7000), 8, 10, null);
      // 10% de 50.000, no de 57.000: nadie propina por el domicilio que paga.
      assertThat(cuenta.propina()).isEqualTo(5000);
      assertThat(cuenta.total()).isEqualTo(50000 + 4000 + 7000 + 5000);
    }

    @Test
    @DisplayName("el envío va después del impuesto, como línea aparte")
    void elEnvioVaDespuesDelImpuesto() {
      Cuenta cuenta = CalculadoraCuenta.calcular(domicilio(List.of(item(50000, 1)), 7000), 8);
      assertThat(cuenta.subtotal() + cuenta.inc() + cuenta.costoEnvio()).isEqualTo(cuenta.total());
    }

    @Test
    @DisplayName("para llevar no cobra envío")
    void paraLlevarNoCobraEnvio() {
      Orden orden = orden(List.of(item(50000, 1)), List.of());
      orden.setTipo(co.elpatio.dominio.pedido.TipoPedido.LLEVAR);
      Cuenta cuenta = CalculadoraCuenta.calcular(orden, 8);
      assertThat(cuenta.costoEnvio()).isZero();
      assertThat(cuenta.total()).isEqualTo(54000);
    }

    @Test
    @DisplayName("una orden de mesa nunca trae envío")
    void mesaSinEnvio() {
      Cuenta cuenta = CalculadoraCuenta.calcular(orden(List.of(item(50000, 1)), List.of()), 8);
      assertThat(cuenta.costoEnvio()).isZero();
    }
  }

  @Nested
  @DisplayName("División de la cuenta")
  class Division {

    @Test
    @DisplayName("en partes iguales, el residuo se carga a la primera parte")
    void residuoALaPrimeraParte() {
      long[] partes = CalculadoraCuenta.dividirEnPartesIguales(100000, 3);
      assertThat(partes).containsExactly(33334, 33333, 33333);
      assertThat(partes[0] + partes[1] + partes[2]).isEqualTo(100000);
    }

    @Test
    @DisplayName("una sola parte se lleva el total")
    void unaSolaParte() {
      assertThat(CalculadoraCuenta.dividirEnPartesIguales(87500, 1)).containsExactly(87500);
    }

    @Test
    @DisplayName("la selección se lleva su parte proporcional de INC, cargos y propina")
    void seleccionLlevaSuParteProporcional() {
      ItemOrden mio = item(40000, 1);
      ItemOrden tuyo = item(60000, 1);
      Orden orden = orden(List.of(mio, tuyo), List.of());
      Cuenta cuenta = CalculadoraCuenta.calcular(orden, 8, 10, null);

      var resultado =
          CalculadoraCuenta.dividirPorItems(orden.getItems(), List.of(mio.getId()), cuenta);

      // 40% del subtotal se lleva el 40% del total, impuesto y propina incluidos.
      assertThat(resultado.valorSeleccion()).isEqualTo(Math.round(cuenta.total() * 0.4));
      assertThat(resultado.valorSeleccion() + resultado.valorResto()).isEqualTo(cuenta.total());
    }

    @Test
    @DisplayName("una cuenta sin consumo no se puede repartir por productos")
    void subtotalCeroDejaTodoEnElResto() {
      Orden orden = orden(List.of(), List.of(cargo(45000)));
      Cuenta cuenta = CalculadoraCuenta.calcular(orden, 8);

      var resultado = CalculadoraCuenta.dividirPorItems(orden.getItems(), List.of(), cuenta);

      assertThat(resultado.valorSeleccion()).isZero();
      assertThat(resultado.valorResto()).isEqualTo(45000);
    }
  }

  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Cuenta completa de una mesa real")
  class CuentaCompleta {

    @Test
    @DisplayName("cuadra peso por peso con el desglose que ve el cliente")
    void cuadraElDesglose() {
      Orden orden =
          orden(
              List.of(
                  item(22000, 2), // croquetas
                  item(68000, 1, new ModificadorSeleccionado("Guarnición", "Risotto", 9000)),
                  item(38000, 3)), // cócteles
              List.of(cargo(45000)));

      Cuenta cuenta = CalculadoraCuenta.calcular(orden, 8, 10, null);

      long subtotalEsperado = 44000 + 77000 + 114000;
      assertThat(cuenta.subtotal()).isEqualTo(subtotalEsperado);
      assertThat(cuenta.inc()).isEqualTo(Math.round(subtotalEsperado * 0.08));
      assertThat(cuenta.cargosAdicionales()).isEqualTo(45000);
      assertThat(cuenta.propina()).isEqualTo(Math.round(subtotalEsperado * 0.10));
      assertThat(cuenta.total())
          .isEqualTo(cuenta.subtotal() + cuenta.inc() + cuenta.cargosAdicionales() + cuenta.propina());
    }
  }
}

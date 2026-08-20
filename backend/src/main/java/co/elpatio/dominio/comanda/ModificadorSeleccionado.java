package co.elpatio.dominio.comanda;

/**
 * Lo que el cliente escogio para un plato concreto. Se guarda el precio del
 * momento y no una referencia al modificador: si manana sube el recargo del
 * doble licor, las comandas viejas no pueden cambiar de valor.
 */
public record ModificadorSeleccionado(String nombre, String valor, long precioAdicional) {}

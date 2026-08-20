package co.elpatio.dominio.cobro;

/** Una parte de un pago mixto o de una cuenta dividida. */
public record DivisionPago(String nombre, long valor, MetodoPago metodo) {}

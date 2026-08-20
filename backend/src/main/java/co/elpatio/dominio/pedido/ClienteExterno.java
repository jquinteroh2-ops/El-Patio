package co.elpatio.dominio.pedido;

/**
 * Quien pidio desde fuera del salon.
 *
 * Se guarda con la orden y no en una tabla de clientes: el restaurante no lleva
 * fidelizacion, y una tabla de personas obligaria a responder por datos
 * personales que hoy nadie necesita.
 */
public record ClienteExterno(String nombre, String telefono, String direccion, String barrio) {}

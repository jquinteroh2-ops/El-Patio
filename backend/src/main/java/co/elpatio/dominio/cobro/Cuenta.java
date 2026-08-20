package co.elpatio.dominio.cobro;

/**
 * Desglose de una cuenta. El orden de los campos es el mismo en que se le
 * muestra al cliente, para que nadie vea un cargo por primera vez al pagar.
 */
public record Cuenta(
    /** Alimentos y bebidas, con modificadores incluidos. */
    long subtotal,
    /** Impuesto Nacional al Consumo sobre el subtotal, antes de propina. */
    long inc,
    int porcentajeInc,
    /** Decoracion, descorche, servicios especiales. No causan INC. */
    long cargosAdicionales,
    /** Voluntaria. Cero mientras el cliente no la autorice. */
    long propina,
    int porcentajePropina,
    long total) {}

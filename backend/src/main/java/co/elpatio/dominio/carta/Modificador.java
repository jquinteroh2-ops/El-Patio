package co.elpatio.dominio.carta;

import java.util.List;

/**
 * Pregunta que el mesero le hace al cliente al tomar el plato: termino de la
 * carne, guarnicion, punto de picante.
 */
public record Modificador(
    String id,
    String nombre,
    TipoModificador tipo,
    List<OpcionModificador> opciones,
    boolean obligatorio) {}

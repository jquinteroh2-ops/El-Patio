package co.elpatio.dominio.interprete;

import java.util.List;

/**
 * Lo que devuelve interpretar un texto libre contra el menu.
 *
 * `noReconocidos` lleva el texto tal cual lo escribio el cliente para lo que
 * no se pudo cruzar con ningun id del menu: el bot se lo muestra y pregunta,
 * nunca adivina cual seria el mas parecido.
 */
public record ResultadoInterpretacion(List<ItemReconocido> reconocidos, List<String> noReconocidos) {}

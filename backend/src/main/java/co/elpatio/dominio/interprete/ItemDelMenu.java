package co.elpatio.dominio.interprete;

/**
 * Lo unico que un interprete de texto libre puede ver del menu: id y nombre.
 *
 * Nunca el precio: el precio real lo busca el backend por el id despues, para
 * que un modelo de lenguaje jamas tenga la ultima palabra sobre cuanto cuesta
 * algo.
 */
public record ItemDelMenu(String id, String nombre) {}

package co.elpatio.dominio.carta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemCartaTest {

  private static ItemCarta plato(String portada, String... galeria) {
    ItemCarta item = new ItemCarta();
    item.setNombre("Pave de res gratinado");
    item.setImagen(portada);
    item.setGaleria(List.of(galeria));
    return item;
  }

  // -------------------------------------------------------------------------
  // Las fotos del plato
  // -------------------------------------------------------------------------

  @Test
  void la_portada_va_primero() {
    assertThat(plato("portada.jpg", "otra.jpg", "tercera.jpg").fotos())
        .containsExactly("portada.jpg", "otra.jpg", "tercera.jpg");
  }

  @Test
  void un_plato_sin_fotos_no_tiene_ninguna() {
    assertThat(plato(null).fotos()).isEmpty();
  }

  @Test
  void la_galeria_puede_venir_sin_portada() {
    // Pasa mientras el administrador esta editando: quita la portada y las
    // demas siguen ahi. No debe aparecer un hueco al principio de la lista.
    assertThat(plato(null, "otra.jpg").fotos()).containsExactly("otra.jpg");
  }

  /**
   * Los vacios no cuentan como foto.
   *
   * Un nombre en blanco es lo que deja un formulario mal guardado, y contarlo
   * haria dos cosas malas: pedirle al servidor una ruta sin nombre —hueco roto
   * en medio del carrusel— y, en la limpieza de huerfanas, mandar a borrar un
   * archivo sin nombre.
   */
  @Test
  void los_nombres_en_blanco_no_son_fotos() {
    ItemCarta item = new ItemCarta();
    item.setImagen("  ");
    item.setGaleria(Arrays.asList("real.jpg", "", null, "otra.jpg"));

    assertThat(item.fotos()).containsExactly("real.jpg", "otra.jpg");
  }

  @Test
  void una_galeria_nula_se_guarda_como_lista_vacia() {
    // El JSON que llega del panel puede traer `galeria: null`, y a partir de
    // ahi todo el que la recorra tendria que acordarse de comprobarlo.
    ItemCarta item = new ItemCarta();
    item.setGaleria(null);

    assertThat(item.getGaleria()).isEmpty();
  }

  @Test
  void la_lista_de_fotos_no_deja_tocar_el_plato_por_detras() {
    ItemCarta item = plato("portada.jpg");
    List<String> fotos = item.fotos();

    assertThat(fotos).isUnmodifiable();
    assertThat(item.fotos()).containsExactly("portada.jpg");
  }

  @Test
  void la_galeria_que_se_entrega_no_se_sigue_moviendo_por_fuera() {
    // Si el plato se quedara con la lista que le pasaron, quien la modifique
    // despues le estaria cambiando las fotos sin saberlo.
    List<String> galeria = new ArrayList<>(List.of("una.jpg"));
    ItemCarta item = new ItemCarta();
    item.setImagen("portada.jpg");
    item.setGaleria(galeria);

    galeria.add("colada.jpg");

    assertThat(item.fotos()).containsExactly("portada.jpg", "una.jpg");
  }
}

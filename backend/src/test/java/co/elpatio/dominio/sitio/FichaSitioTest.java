package co.elpatio.dominio.sitio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La ficha del sitio.
 *
 * Casi todo lo que se comprueba aqui protege al SITIO PUBLICO de un formulario:
 * un WhatsApp con espacios rompe el enlace del chat sin dar ningun aviso, y una
 * franja a medias se ve como un error de la pagina y no como una decision.
 */
class FichaSitioTest {

  private static final Instant AHORA = Instant.parse("2026-01-10T20:00:00Z");

  private static FichaSitio conWhatsapp(String whatsapp) {
    FichaSitio ficha = new FichaSitio();
    ficha.editar("Calle 26 #31-2", "Turbaco", "+57 304 403 2936", whatsapp, "elpatio", List.of(), AHORA);
    return ficha;
  }

  @Test
  void el_whatsapp_se_guarda_en_el_formato_que_exige_el_enlace() {
    assertThat(conWhatsapp("+57 304 403 2936").getWhatsapp()).isEqualTo("573044032936");
  }

  @Test
  void un_whatsapp_demasiado_corto_no_pasa() {
    assertThatThrownBy(() -> conWhatsapp("30440"))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("indicativo");
  }

  @Test
  void la_arroba_de_instagram_se_cae_para_no_duplicarla_en_el_enlace() {
    FichaSitio ficha = new FichaSitio();
    ficha.editar("Calle 26", "Turbaco", "3044032936", "573044032936", "@elpatio", List.of(), AHORA);
    assertThat(ficha.getInstagram()).isEqualTo("elpatio");
  }

  @Test
  void una_franja_a_medias_no_se_publica() {
    FichaSitio ficha = new FichaSitio();
    assertThatThrownBy(
            () ->
                ficha.editar(
                    "Calle 26", "Turbaco", "3044032936", "573044032936", "elpatio",
                    List.of(new FranjaHorario("Domingo", "  ")), AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("días y las horas");
  }

  @Test
  void una_franja_vacia_del_todo_se_descarta_sin_protestar() {
    // Es la fila que quedo despues de tocar «agregar» y arrepentirse.
    FichaSitio ficha = new FichaSitio();
    ficha.editar(
        "Calle 26", "Turbaco", "3044032936", "573044032936", "elpatio",
        List.of(new FranjaHorario("Lunes", "Cerrado"), new FranjaHorario("", "")), AHORA);

    assertThat(ficha.getHorario()).containsExactly(new FranjaHorario("Lunes", "Cerrado"));
  }

  @Test
  void sin_direccion_no_se_guarda() {
    FichaSitio ficha = new FichaSitio();
    assertThatThrownBy(
            () -> ficha.editar("", "Turbaco", "3044032936", "573044032936", "elpatio", List.of(), AHORA))
        .isInstanceOf(ReglaDeNegocioError.class)
        .hasMessageContaining("dirección");
  }
}

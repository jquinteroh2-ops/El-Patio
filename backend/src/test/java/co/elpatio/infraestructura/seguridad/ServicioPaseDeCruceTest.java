package co.elpatio.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.Reloj;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * El pase con que el dueno cruza al panel del otro restaurante.
 *
 * Lo que se protege aqui es la puerta de un sistema que maneja la caja de un
 * restaurante, abierta desde otro sistema. Casi todas las pruebas comprueban
 * que el pase NO sirva: firmado con otro secreto, vencido, o emitido por un
 * despliegue que no tiene el cruce configurado.
 */
class ServicioPaseDeCruceTest {

  private static final String SECRETO = "un-secreto-de-cruce-de-mas-de-32-caracteres";
  private static final String OTRO_SECRETO = "otro-secreto-distinto-de-mas-de-32-caracteres";
  private static final Instant AHORA = Instant.parse("2026-08-31T20:00:00Z");

  private static Reloj relojEn(Instant momento) {
    Reloj reloj = mock(Reloj.class);
    when(reloj.ahora()).thenReturn(momento);
    return reloj;
  }

  private static ServicioPaseDeCruce servicio(String secreto, Instant momento) {
    return new ServicioPaseDeCruce(new LlaveDeCruce(secreto, "La Carreta Gourmet"), relojEn(momento));
  }

  private static Usuario dueno() {
    return new Usuario("u_admin", "Jose Quintero", Rol.ADMINISTRADOR, "admin", "hash", true);
  }

  @Test
  void el_pase_lleva_el_nombre_de_usuario_y_no_el_identificador() {
    // Es la pieza sobre la que se sostiene todo el cruce: los identificadores
    // se generan en cada base por separado, asi que el de aqui no significa
    // nada alla. El nombre de usuario es lo unico que las dos bases comparten.
    ServicioPaseDeCruce emisor = servicio(SECRETO, AHORA);
    ServicioPaseDeCruce destino = servicio(SECRETO, AHORA);

    ServicioPaseDeCruce.Pase pase = destino.verificar(emisor.emitir(dueno())).orElseThrow();

    assertThat(pase.usuario()).isEqualTo("admin");
    assertThat(pase.usuario()).isNotEqualTo("u_admin");
    assertThat(pase.rol()).isEqualTo(Rol.ADMINISTRADOR);
    assertThat(pase.origen()).isEqualTo("La Carreta Gourmet");
  }

  @Test
  void un_pase_firmado_con_otro_secreto_no_vale() {
    ServicioPaseDeCruce impostor = servicio(OTRO_SECRETO, AHORA);
    ServicioPaseDeCruce destino = servicio(SECRETO, AHORA);

    assertThat(destino.verificar(impostor.emitir(dueno()))).isEmpty();
  }

  @Test
  void un_pase_de_hace_un_minuto_ya_no_vale() {
    // Dura treinta segundos: lo que tarda el navegador en cargar la otra
    // pagina. Un minuto despues, lo que quedo en el historial ya no abre nada.
    String pase = servicio(SECRETO, AHORA).emitir(dueno());

    ServicioPaseDeCruce destino = servicio(SECRETO, AHORA.plus(Duration.ofMinutes(1)));

    assertThat(destino.verificar(pase)).isEmpty();
  }

  @Test
  void un_pase_recien_emitido_si_vale() {
    String pase = servicio(SECRETO, AHORA).emitir(dueno());

    ServicioPaseDeCruce destino = servicio(SECRETO, AHORA.plus(Duration.ofSeconds(10)));

    assertThat(destino.verificar(pase)).isPresent();
  }

  @Test
  void dos_pases_seguidos_no_son_el_mismo_papel() {
    // El identificador es lo que permite que un pase sirva UNA sola vez. Si dos
    // emisiones compartieran identificador, canjear una invalidaria la otra.
    ServicioPaseDeCruce emisor = servicio(SECRETO, AHORA);

    String primero = emisor.emitir(dueno());
    String segundo = emisor.emitir(dueno());

    assertThat(emisor.verificar(primero).orElseThrow().jti())
        .isNotEqualTo(emisor.verificar(segundo).orElseThrow().jti());
  }

  @Test
  void sin_secreto_configurado_el_cruce_queda_apagado_en_los_dos_sentidos() {
    // Es el estado por defecto, y el correcto para un despliegue suelto que no
    // tiene restaurante hermano: ni emite pases ni acepta los de nadie.
    ServicioPaseDeCruce suelto = servicio("", AHORA);

    assertThat(suelto.activo()).isFalse();
    assertThatThrownBy(() -> suelto.emitir(dueno())).isInstanceOf(IllegalStateException.class);
    assertThat(suelto.verificar(servicio(SECRETO, AHORA).emitir(dueno()))).isEmpty();
  }

  @Test
  void un_secreto_corto_no_deja_arrancar() {
    // Se falla al arrancar y no al primer cruce: este secreto abre la puerta
    // del panel del otro restaurante, y uno corto se rompe por fuerza bruta.
    assertThatThrownBy(() -> servicio("muy-corto", AHORA))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 caracteres");
  }

  @Test
  void un_pase_manipulado_no_vale() {
    ServicioPaseDeCruce servicio = servicio(SECRETO, AHORA);
    String pase = servicio.emitir(dueno());

    // Se le cambia un caracter al cuerpo, que es donde va el nombre de usuario.
    String partes[] = pase.split("\\.");
    String alterado = partes[0] + "." + partes[1].substring(0, partes[1].length() - 2) + "XY." + partes[2];

    assertThat(servicio.verificar(alterado)).isEmpty();
  }

  @Test
  void ni_lo_vacio_ni_la_basura_pasan_por_pase() {
    ServicioPaseDeCruce servicio = servicio(SECRETO, AHORA);

    assertThat(servicio.verificar(null)).isEmpty();
    assertThat(servicio.verificar("")).isEmpty();
    assertThat(servicio.verificar("   ")).isEmpty();
    assertThat(servicio.verificar("esto-no-es-un-token")).isEmpty();
  }
}

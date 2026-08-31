package co.elpatio.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * El sobre con que un restaurante le manda al otro un cambio sobre la cuenta
 * del dueno.
 *
 * Lo que va dentro es el hash de una clave que abre el panel de un sistema que
 * maneja una caja, asi que casi todo lo que se comprueba aqui es que un sobre
 * que no sea autentico NO se abra.
 *
 * El envio por HTTP no se prueba aqui: eso es una llamada a otro servidor y se
 * comprueba contra el sistema levantado, no con un doble que responda lo que
 * uno quiera oir.
 */
class ServicioEspejoDeCuentaTest {

  private static final String SECRETO = "un-secreto-de-cruce-de-mas-de-32-caracteres";
  private static final String OTRO_SECRETO = "otro-secreto-distinto-de-mas-de-32-caracteres";

  private static ServicioEspejoDeCuenta servicio(String secreto) {
    return new ServicioEspejoDeCuenta(new LlaveDeCruce(secreto, "La Carreta Gourmet"), "https://hermano");
  }

  private static ServicioEspejoDeCuenta.Cambio cambio(String claveHash) {
    return new ServicioEspejoDeCuenta.Cambio(
        "admin", "jose", "Jose Quintero", "jose@elpatio.co", claveHash, null);
  }

  /** El sobre tal como lo produce el restaurante que manda el cambio. */
  private static String sobreFirmadoPor(ServicioEspejoDeCuenta emisor, ServicioEspejoDeCuenta.Cambio c) {
    return emisor.firmar(c);
  }

  @Test
  void el_sobre_lleva_el_usuario_anterior_como_sujeto() {
    // Es la pieza sobre la que se sostiene todo: si el dueno se cambia el nombre
    // de usuario y el destino buscara por el NUEVO, no encontraria nada y las
    // dos cuentas quedarian separadas para siempre.
    ServicioEspejoDeCuenta emisor = servicio(SECRETO);
    ServicioEspejoDeCuenta destino = servicio(SECRETO);

    ServicioEspejoDeCuenta.Cambio leido =
        destino.abrir(sobreFirmadoPor(emisor, cambio("$2a$10$hash"))).orElseThrow();

    assertThat(leido.usuarioAnterior()).isEqualTo("admin");
    assertThat(leido.usuario()).isEqualTo("jose");
    assertThat(leido.nombre()).isEqualTo("Jose Quintero");
    assertThat(leido.claveHash()).isEqualTo("$2a$10$hash");
    assertThat(leido.origen()).isEqualTo("La Carreta Gourmet");
  }

  @Test
  void sin_cambio_de_clave_el_hash_viaja_nulo() {
    // Es lo que le dice al destino «no toques la tuya». Sin esta distincion,
    // recibir siempre un hash haria imposible saber si la clave cambio.
    ServicioEspejoDeCuenta emisor = servicio(SECRETO);

    ServicioEspejoDeCuenta.Cambio leido =
        emisor.abrir(sobreFirmadoPor(emisor, cambio(null))).orElseThrow();

    assertThat(leido.claveHash()).isNull();
  }

  @Test
  void un_sobre_firmado_con_otro_secreto_no_se_abre() {
    ServicioEspejoDeCuenta impostor = servicio(OTRO_SECRETO);
    ServicioEspejoDeCuenta destino = servicio(SECRETO);

    assertThat(destino.abrir(sobreFirmadoPor(impostor, cambio("$2a$10$hash")))).isEmpty();
  }

  @Test
  void un_sobre_manipulado_no_se_abre() {
    ServicioEspejoDeCuenta servicio = servicio(SECRETO);
    String sobre = sobreFirmadoPor(servicio, cambio("$2a$10$hash"));

    String[] partes = sobre.split("\\.");
    String alterado =
        partes[0] + "." + partes[1].substring(0, partes[1].length() - 2) + "XY." + partes[2];

    assertThat(servicio.abrir(alterado)).isEmpty();
  }

  @Test
  void sin_secreto_no_se_manda_ni_se_recibe_nada() {
    // El estado por defecto de un despliegue suelto: no tiene hermano y no
    // tiene por que aceptarle cambios de cuenta a nadie.
    ServicioEspejoDeCuenta suelto =
        new ServicioEspejoDeCuenta(new LlaveDeCruce("", "La Carreta Gourmet"), "https://hermano");

    assertThat(suelto.puedeReplicar()).isFalse();
    assertThat(suelto.puedeRecibir()).isFalse();
    assertThat(suelto.replicar(cambio("$2a$10$hash")))
        .isEqualTo(ServicioEspejoDeCuenta.Resultado.APAGADO);
    assertThat(suelto.abrir(sobreFirmadoPor(servicio(SECRETO), cambio("h")))).isEmpty();
  }

  @Test
  void con_secreto_pero_sin_direccion_del_hermano_tampoco_se_replica() {
    // Pasa mientras se configura el segundo restaurante: el secreto ya esta
    // puesto en los dos, pero uno todavia no sabe a donde escribirle.
    ServicioEspejoDeCuenta sinHermano =
        new ServicioEspejoDeCuenta(new LlaveDeCruce(SECRETO, "La Carreta Gourmet"), "");

    assertThat(sinHermano.puedeReplicar()).isFalse();
    // Recibir si puede: la firma es lo unico que hace falta para eso.
    assertThat(sinHermano.puedeRecibir()).isTrue();
  }

  @Test
  void ni_lo_vacio_ni_la_basura_pasan_por_sobre() {
    ServicioEspejoDeCuenta servicio = servicio(SECRETO);

    assertThat(servicio.abrir(null)).isEmpty();
    assertThat(servicio.abrir("")).isEmpty();
    assertThat(servicio.abrir("   ")).isEmpty();
    assertThat(servicio.abrir("esto-no-es-un-sobre")).isEmpty();
  }
}

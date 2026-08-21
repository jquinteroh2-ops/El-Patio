package co.elpatio.infraestructura.config;

import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.Repositorios;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el personal la primera vez que arranca el sistema.
 *
 * Las claves se generan al azar y se imprimen una sola vez en consola. En el
 * prototipo estaban escritas como "1234" en datosSemilla.ts: eso servia para
 * una demostracion, pero una clave en el repositorio es una clave publicada, y
 * este sistema va a manejar el dinero de la caja.
 *
 * Si ya hay usuarios no se hace nada: el arranque tiene que ser repetible sin
 * pisar las claves que el administrador ya cambio.
 *
 * La excepcion es {@link ModoDemostracion}. Cuando esta encendido hay que poder
 * enseñar el sistema, y para eso las claves tienen que ser conocidas y las seis
 * cuentas tienen que existir aunque la base ya se haya usado. Ahi si se pisan
 * las claves, en cada arranque y a proposito.
 */
@Component
public class SembradorUsuarios implements ApplicationRunner {

  private static final Logger registro = LoggerFactory.getLogger(SembradorUsuarios.class);

  /**
   * Alfabeto sin caracteres que se confunden al dictarlos en voz alta en un
   * salon con ruido: nada de O contra 0, ni l contra 1 contra I.
   */
  private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final Repositorios.DeUsuarios usuarios;
  private final PasswordEncoder claves;
  private final ModoDemostracion demostracion;
  private final SecureRandom azar = new SecureRandom();

  public SembradorUsuarios(
      Repositorios.DeUsuarios usuarios, PasswordEncoder claves, ModoDemostracion demostracion) {
    this.usuarios = usuarios;
    this.claves = claves;
    this.demostracion = demostracion;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    if (demostracion.activo()) {
      sembrarParaDemostracion();
      return;
    }

    if (usuarios.hayAlguno()) {
      registro.info("El personal ya está creado: no se siembra nada.");
      return;
    }

    sembrarConClavesAleatorias();
  }

  /**
   * Deja las seis cuentas listas con la misma clave conocida.
   *
   * Busca por nombre de acceso y no por identificador porque lo que tiene que
   * quedar garantizado es que quien escriba `admin` entre: si la fila ya existe
   * de un arranque anterior se le reescribe la clave, y si no existe se crea.
   * Asi el modo funciona igual sobre una base recien creada que sobre una que
   * ya lleva semanas de ventas.
   */
  private void sembrarParaDemostracion() {
    StringBuilder aviso = new StringBuilder();
    aviso.append("\n");
    aviso.append("===========================================================\n");
    aviso.append("  MODO DEMOSTRACIÓN ACTIVO — CLAVES CONOCIDAS\n");
    aviso.append("  Quite ELPATIO_CLAVE_DEMO antes de operar de verdad.\n");
    aviso.append("===========================================================\n");

    for (ModoDemostracion.Cuenta cuenta : demostracion.cuentas()) {
      Usuario usuario =
          usuarios.porNombreDeUsuario(cuenta.usuario()).orElseGet(Usuario::new);

      if (usuario.getId() == null) usuario.setId("u_" + cuenta.usuario());
      usuario.setNombre(cuenta.nombre());
      usuario.setRol(cuenta.rol());
      usuario.setUsuario(cuenta.usuario());
      usuario.setClaveHash(claves.encode(demostracion.clave()));
      usuario.setActivo(true);
      usuarios.guardar(usuario);

      aviso.append(
          String.format(
              "  %-14s %-10s %s%n",
              cuenta.rol().codigo(), cuenta.usuario(), demostracion.clave()));
    }

    aviso.append("===========================================================\n");
    System.out.println(aviso);
    registro.warn(
        "Modo demostración: las {} cuentas quedaron con una clave conocida.",
        demostracion.cuentas().size());
  }

  private void sembrarConClavesAleatorias() {
    StringBuilder aviso = new StringBuilder();
    aviso.append("\n");
    aviso.append("===========================================================\n");
    aviso.append("  PERSONAL CREADO — ESTAS CLAVES NO SE VUELVEN A MOSTRAR\n");
    aviso.append("  Anótelas ahora y cámbielas desde /admin/configuracion.\n");
    aviso.append("===========================================================\n");

    for (ModoDemostracion.Cuenta cuenta : demostracion.cuentas()) {
      String clave = claveAleatoria();
      Usuario usuario =
          new Usuario(
              "u_" + cuenta.usuario(),
              cuenta.nombre(),
              cuenta.rol(),
              cuenta.usuario(),
              claves.encode(clave),
              true);
      usuarios.guardar(usuario);

      aviso.append(String.format("  %-14s %-10s %s%n", cuenta.rol().codigo(), cuenta.usuario(), clave));
    }

    aviso.append("===========================================================\n");

    // Va por System.out y no por el registrador: los registros estructurados de
    // produccion se envian a un agregador, y estas claves no pueden terminar
    // guardadas en un indice de busqueda.
    System.out.println(aviso);
    registro.info("Personal inicial creado. Las claves se imprimieron en la salida estándar.");
  }

  private String claveAleatoria() {
    StringBuilder clave = new StringBuilder(10);
    for (int i = 0; i < 10; i++) clave.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
    return clave.toString();
  }
}

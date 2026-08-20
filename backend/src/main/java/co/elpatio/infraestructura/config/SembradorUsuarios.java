package co.elpatio.infraestructura.config;

import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.Repositorios;
import java.security.SecureRandom;
import java.util.List;
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
 */
@Component
public class SembradorUsuarios implements ApplicationRunner {

  private static final Logger registro = LoggerFactory.getLogger(SembradorUsuarios.class);

  /**
   * Alfabeto sin caracteres que se confunden al dictarlos en voz alta en un
   * salon con ruido: nada de O contra 0, ni l contra 1 contra I.
   */
  private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private static final List<Plantilla> PLANTILLAS =
      List.of(
          new Plantilla("María Fernanda Ospina", Rol.MESERO, "mesero"),
          new Plantilla("Deivis Cabarcas", Rol.MESERO, "mesero2"),
          new Plantilla("Jhon Alexis Padilla", Rol.COCINA, "cocina"),
          new Plantilla("Katherine Villalba", Rol.CAJERO, "cajero"),
          new Plantilla("Álvaro Restrepo Díaz", Rol.ADMINISTRADOR, "admin"));

  private record Plantilla(String nombre, Rol rol, String usuario) {}

  private final Repositorios.DeUsuarios usuarios;
  private final PasswordEncoder claves;
  private final SecureRandom azar = new SecureRandom();

  public SembradorUsuarios(Repositorios.DeUsuarios usuarios, PasswordEncoder claves) {
    this.usuarios = usuarios;
    this.claves = claves;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    if (usuarios.hayAlguno()) {
      registro.info("El personal ya está creado: no se siembra nada.");
      return;
    }

    StringBuilder aviso = new StringBuilder();
    aviso.append("\n");
    aviso.append("===========================================================\n");
    aviso.append("  PERSONAL CREADO — ESTAS CLAVES NO SE VUELVEN A MOSTRAR\n");
    aviso.append("  Anótelas ahora y cámbielas desde /admin/configuracion.\n");
    aviso.append("===========================================================\n");

    for (Plantilla plantilla : PLANTILLAS) {
      String clave = claveAleatoria();
      Usuario usuario =
          new Usuario(
              "u_" + plantilla.usuario(),
              plantilla.nombre(),
              plantilla.rol(),
              plantilla.usuario(),
              claves.encode(clave),
              true);
      usuarios.guardar(usuario);

      aviso.append(
          String.format(
              "  %-14s %-10s %s%n", plantilla.rol().codigo(), plantilla.usuario(), clave));
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

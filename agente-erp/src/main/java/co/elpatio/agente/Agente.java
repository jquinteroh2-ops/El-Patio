package co.elpatio.agente;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * El mensajero entre El Patio en la nube y Globalsoft en el computador del
 * restaurante.
 *
 * Cada poco le pregunta a la nube si hay ventas nuevas, se las entrega a
 * Globalsoft y le cuenta a la nube como le fue. Si se cae el internet espera y
 * vuelve a intentar, cada vez con mas calma, sin perder nada: las ventas siguen
 * guardadas en la nube hasta que el agente diga que las entrego.
 *
 * <pre>
 *   agente-erp [ruta\agente.properties] [--una-vez]
 * </pre>
 *
 * {@code --una-vez} hace una sola pasada y termina. Sirve para probar la
 * instalacion sin dejar nada corriendo.
 */
public class Agente {

  private static final Logger registro = Logger.getLogger("agente");

  /** Lo mas que espera entre intentos cuando la nube no contesta. */
  private static final Duration ESPERA_MAXIMA = Duration.ofMinutes(5);

  private final Nube nube;
  private final Entregador entregador;
  private final RegistroDeEntregas entregas;
  private final int tanda;

  public Agente(Nube nube, Entregador entregador, RegistroDeEntregas entregas, int tanda) {
    this.nube = nube;
    this.entregador = entregador;
    this.entregas = entregas;
    this.tanda = tanda;
  }

  /**
   * Una pasada: pide una tanda, la entrega y la reporta.
   *
   * Una venta que falla no detiene a las demas. Si lo que falla es avisarle a
   * la nube, la entrega ya quedo anotada: cuando la nube la vuelva a ofrecer,
   * el agente solo repite la respuesta.
   *
   * @return cuantas ventas llegaron en la tanda. Si llego llena, hay mas.
   * @throws IOException si no se pudo hablar con la nube para pedir la tanda.
   */
  public int pasada() throws IOException, InterruptedException {
    List<Nube.EnvioReclamado> envios = nube.reclamar(tanda);
    for (Nube.EnvioReclamado envio : envios) {
      String llave = envio.llave();
      Resultado resultado = entregas.buscar(llave).orElse(null);
      if (resultado != null) {
        registro.info("Comanda " + envio.numeroComanda() + " ya se habia entregado; se repite la respuesta");
      } else {
        resultado = entregador.entregar(envio.venta());
        // Un rechazo no se anota: no llego nada a Globalsoft y la proxima vez
        // hay que intentar de verdad, no repetir el rechazo.
        if (!"rechazado".equals(resultado.desenlace())) {
          entregas.anotar(llave, envio.envioId(), resultado);
        }
        registro.info("Comanda " + envio.numeroComanda() + ": " + resultado.desenlace()
            + (resultado.motivo() == null ? "" : " — " + resultado.motivo()));
      }
      try {
        nube.reportar(envio.envioId(), resultado);
      } catch (IOException e) {
        registro.warning("No se pudo avisar a El Patio de la comanda " + envio.numeroComanda()
            + " (" + e.getMessage() + "). Se avisara cuando la vuelva a ofrecer.");
      }
    }
    return envios.size();
  }

  /** Corre para siempre. Solo sale si lo apagan. */
  public void correr(Duration intervalo) throws InterruptedException {
    Duration espera = intervalo;
    while (true) {
      try {
        int recibidas = pasada();
        espera = intervalo;
        // Tanda llena: hay mas esperando, se sigue sin dormir.
        if (recibidas >= tanda) continue;
      } catch (IOException e) {
        // Cada fallo seguido dobla la espera, hasta cinco minutos: un internet
        // caido toda la noche no tiene por que llenar la bitacora.
        Duration doble = espera.multipliedBy(2);
        espera = doble.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : doble;
        registro.warning("Sin conexion con El Patio: " + e.getMessage()
            + ". Se reintenta en " + espera.toSeconds() + " s");
      } catch (RuntimeException e) {
        // Un error de programacion no puede dejar al restaurante sin agente.
        registro.log(Level.SEVERE, "Error inesperado en la pasada", e);
      }
      Thread.sleep(espera.toMillis());
    }
  }

  public static void main(String[] args) throws Exception {
    boolean unaVez = false;
    Path archivo = null;
    for (String arg : args) {
      if (arg.equals("--una-vez")) unaVez = true;
      else archivo = Path.of(arg);
    }
    if (archivo == null) archivo = carpetaDelPrograma().resolve("agente.properties");

    Configuracion config = Configuracion.leer(archivo);
    prepararBitacora(config.carpetaDatos().resolve("registros"));

    Entregador entregador =
        switch (config.modoEntrega()) {
          case "carpeta" -> new EntregaPorCarpeta(config.carpetaEntrega());
          default -> throw new IllegalArgumentException(
              "entrega.modo desconocido: " + config.modoEntrega() + " (hoy solo existe: carpeta)");
        };
    RegistroDeEntregas entregas =
        new RegistroDeEntregas(config.carpetaDatos().resolve("entregadas.jsonl"));
    ClienteNube nube = new ClienteNube(config.urlNube(), config.llave());

    registro.info("Agente de El Patio arrancando. Nube: " + config.urlNube()
        + " · entrega: " + entregador.nombre() + " · ventas ya entregadas: " + entregas.cuantas());
    try {
      registro.info("Conexion con El Patio correcta (adaptador: " + nube.comprobar() + ")");
    } catch (IOException e) {
      // No se sale: si es el internet, se arregla solo; si es la llave, la
      // bitacora lo dice en cada intento hasta que alguien la corrija.
      registro.warning("Todavia no hay conexion con El Patio: " + e.getMessage());
    }

    Agente agente = new Agente(nube, entregador, entregas, config.tanda());
    if (unaVez) {
      try {
        int n = agente.pasada();
        registro.info("Pasada de prueba terminada: " + n + " venta(s) recibida(s)");
      } catch (IOException e) {
        registro.severe("La pasada de prueba no pudo hablar con El Patio: " + e.getMessage());
        System.exit(1);
      }
      return;
    }
    agente.correr(config.intervalo());
  }

  /**
   * Donde esta instalado. Empaquetado con jpackage es la carpeta del .exe; en
   * desarrollo, la carpeta desde donde se lanzo.
   */
  private static Path carpetaDelPrograma() {
    String exe = System.getProperty("jpackage.app-path");
    if (exe != null) return Path.of(exe).toAbsolutePath().getParent();
    return Path.of("").toAbsolutePath();
  }

  /** Bitacora en archivo (5 archivos de 2 MB que se van turnando) y en consola. */
  private static void prepararBitacora(Path carpeta) throws IOException {
    System.setProperty("java.util.logging.SimpleFormatter.format",
        "%1$tF %1$tT %4$s %5$s%6$s%n");
    Files.createDirectories(carpeta);
    Logger raiz = Logger.getLogger("");
    for (var h : raiz.getHandlers()) raiz.removeHandler(h);
    FileHandler archivo =
        new FileHandler(carpeta.resolve("agente-%g.log").toString(), 2_000_000, 5, true);
    archivo.setEncoding("UTF-8");
    archivo.setFormatter(new SimpleFormatter());
    ConsoleHandler consola = new ConsoleHandler();
    consola.setFormatter(new SimpleFormatter());
    // La consola de Windows no es UTF-8: sin esto las tildes salen como basura.
    String codificacion = System.getProperty("stderr.encoding");
    if (codificacion != null) consola.setEncoding(codificacion);
    raiz.addHandler(archivo);
    raiz.addHandler(consola);
  }
}

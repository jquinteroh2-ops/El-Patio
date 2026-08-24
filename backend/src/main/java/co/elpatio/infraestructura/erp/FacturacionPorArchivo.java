package co.elpatio.infraestructura.erp;

import co.elpatio.dominio.erp.ResultadoFacturacion;
import co.elpatio.dominio.erp.VentaParaErp;
import co.elpatio.dominio.puertos.FacturacionExterna;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deja cada venta como un archivo plano para que Globalsoft lo importe.
 *
 * Es el camino mas probable con un Globalsoft instalado en la maquina del
 * restaurante: El Patio corre en la nube y no puede abrir una conexion hacia un
 * servidor que vive detras del router del local. Un archivo si cruza esa
 * frontera, porque lo mueve alguien —una carpeta sincronizada, un SFTP, una
 * persona que lo descarga— en vez de exigir que el ERP sea alcanzable.
 *
 * <p><b>Escritura atomica.</b> El archivo se escribe con un nombre temporal y
 * se renombra al final. Sin eso, un importador que barra la carpeta cada minuto
 * puede toparse con un archivo a medio escribir y cargar media venta; el
 * renombrado dentro del mismo sistema de archivos es atomico y el importador o
 * ve el archivo completo o no lo ve.
 *
 * <p>Que el archivo quede escrito NO significa que Globalsoft lo haya
 * importado. Por eso devuelve EN_ESPERA y no CONFIRMADO: el numero del
 * documento lo pone el ERP, y hasta que alguien lo traiga de vuelta la venta
 * sigue sin conciliar. Devolver CONFIRMADO aqui seria darle al contador un
 * tablero en verde sobre ventas que nadie ha importado.
 */
@Component
@ConditionalOnProperty(name = "elpatio.erp.adaptador", havingValue = "archivo")
public class FacturacionPorArchivo implements FacturacionExterna {

  private static final Logger registro = LoggerFactory.getLogger(FacturacionPorArchivo.class);

  private final Path carpeta;

  public FacturacionPorArchivo(@Value("${elpatio.erp.archivo.carpeta}") String carpeta) {
    this.carpeta = Path.of(carpeta);
  }

  @Override
  public ResultadoFacturacion emitirDocumento(VentaParaErp venta) {
    String nombre = MapeadorGlobalsoft.nombreArchivo(venta);
    Path destino = carpeta.resolve(nombre);

    // La llave de idempotencia va en el nombre del archivo, asi que un reintento
    // apunta al mismo sitio. Si ya esta, no se reescribe: puede que el
    // importador lo tenga tomado, y volver a escribirlo encima es como se
    // duplica una venta.
    if (Files.exists(destino)) {
      return ResultadoFacturacion.enEspera("Ya estaba depositado: " + nombre);
    }

    try {
      Files.createDirectories(carpeta);
      Path temporal = carpeta.resolve(nombre + ".parcial");
      String contenido = MapeadorGlobalsoft.ENCABEZADO + "\n" + MapeadorGlobalsoft.aPlano(venta);
      Files.writeString(temporal, contenido, StandardCharsets.UTF_8);
      Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
      return ResultadoFacturacion.enEspera("Depositado para importar: " + nombre);
    } catch (IOException e) {
      // Un disco lleno o una carpeta sin permisos es un fallo esperable del
      // entorno, no del adaptador: se devuelve para que la cola lo reintente.
      registro.warn("No se pudo depositar la venta {} para el ERP", venta.pagoId(), e);
      return ResultadoFacturacion.rechazado(
          "No se pudo escribir el archivo: " + e.getMessage(), null);
    }
  }

  @Override
  public String nombre() {
    return "archivo";
  }
}

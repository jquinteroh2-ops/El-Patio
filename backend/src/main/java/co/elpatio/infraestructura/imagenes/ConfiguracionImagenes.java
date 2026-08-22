package co.elpatio.infraestructura.imagenes;

import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decide donde viven las fotos.
 *
 * Es el unico sitio que lo decide, y decide por lo que hay configurado: si
 * estan las credenciales de Cloudinary, se usa Cloudinary; si no, el disco. No
 * hay una bandera aparte que encender, porque una bandera y unas credenciales
 * son dos cosas que se pueden contradecir, y entonces hay que averiguar cual
 * mando.
 *
 * El disco sigue siendo util: es lo que corre en la maquina de desarrollo, sin
 * cuenta de nadie ni red.
 */
@Configuration
public class ConfiguracionImagenes {

  private static final Logger registro = LoggerFactory.getLogger(ConfiguracionImagenes.class);

  @Bean
  public AlmacenDeImagenes almacenDeImagenes(
      @Value("${elpatio.imagenes.ruta}") String ruta,
      @Value("${elpatio.imagenes.cloudinary.nombre:}") String nombreNube,
      @Value("${elpatio.imagenes.cloudinary.llave:}") String llave,
      @Value("${elpatio.imagenes.cloudinary.secreto:}") String secreto) {

    boolean completo = !nombreNube.isBlank() && !llave.isBlank() && !secreto.isBlank();

    // A medias no se arranca. Con una credencial suelta, el sistema se iria al
    // disco sin avisar y las fotos se perderian en el siguiente despliegue: un
    // fallo silencioso que solo se nota cuando el sitio queda con los marcos
    // vacios. Es mejor no arrancar y que se vea.
    boolean aMedias =
        !completo && (!nombreNube.isBlank() || !llave.isBlank() || !secreto.isBlank());
    if (aMedias) {
      throw new IllegalStateException(
          "Cloudinary esta configurado a medias: hacen falta nombre, llave y secreto");
    }

    if (completo) {
      registro.info("Las fotos se guardan en Cloudinary, en la cuenta {}", nombreNube);
      return new AlmacenCloudinary(nombreNube, llave, secreto);
    }

    registro.warn(
        "Las fotos se guardan en disco ({}). En Railway esa ruta TIENE que ser un volumen: "
            + "si no, se borran en el siguiente despliegue.",
        ruta);
    return new AlmacenEnDisco(ruta);
  }
}

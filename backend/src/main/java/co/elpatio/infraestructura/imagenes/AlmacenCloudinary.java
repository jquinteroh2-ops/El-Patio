package co.elpatio.infraestructura.imagenes;

import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.TreeMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Las fotos, en Cloudinary.
 *
 * Se sube el original SIN tocar. Es la diferencia con el almacen en disco, que
 * tiene que reducir antes de guardar porque despues sirve un solo archivo para
 * todo el mundo: aqui el original queda intacto y cada dispositivo recibe la
 * version que le sirve, pedida desde la propia direccion de la imagen
 * («f_auto,q_auto,w_600»). Un celular baja unos 60 KB en WebP y un computador
 * la ve nitida, de la misma foto y con el mismo enlace.
 *
 * Lo que se guarda en la base es la direccion completa que devuelve Cloudinary.
 * El navegador la pide directo a la CDN y no pasa por este servidor, que es de
 * donde sale la ganancia de velocidad.
 */
public class AlmacenCloudinary implements AlmacenDeImagenes {

  /** La carpeta dentro de la cuenta, para no mezclar con otros proyectos. */
  private static final String CARPETA = "elpatio";

  /** Lo que se acepta que entre. Igual que en disco: mas es una foto sin recortar. */
  private static final long PESO_MAXIMO_BYTES = 25L * 1024 * 1024;

  private final String nombreNube;
  private final String llave;
  private final String secreto;
  private final RestClient http;
  private final ObjectMapper json = new ObjectMapper();

  public AlmacenCloudinary(String nombreNube, String llave, String secreto) {
    this.nombreNube = nombreNube;
    this.llave = llave;
    this.secreto = secreto;
    this.http = RestClient.create();
  }

  @Override
  public String guardar(String nombreOriginal, byte[] contenido) {
    if (contenido == null || contenido.length == 0) {
      throw new ReglaDeNegocioError("La imagen llego vacia");
    }
    if (contenido.length > PESO_MAXIMO_BYTES) {
      throw new ReglaDeNegocioError("La imagen pesa mas de 25 MB. Reduzcala antes de subirla");
    }

    String marca = String.valueOf(Instant.now().getEpochSecond());
    var parametros = new TreeMap<String, String>();
    parametros.put("folder", CARPETA);
    parametros.put("timestamp", marca);

    var cuerpo = new LinkedMultiValueMap<String, Object>();
    cuerpo.add("file", new ByteArrayResource(contenido) {
      @Override
      public String getFilename() {
        // Cloudinary necesita un nombre de archivo para aceptar la parte. El
        // que venga de afuera no se usa: podria traer una ruta adentro.
        return "foto";
      }
    });
    cuerpo.add("api_key", llave);
    cuerpo.add("timestamp", marca);
    cuerpo.add("folder", CARPETA);
    cuerpo.add("signature", firmar(parametros));

    JsonNode respuesta = pedir("upload", cuerpo);
    JsonNode url = respuesta.get("secure_url");
    if (url == null || url.asText().isBlank()) {
      throw new IllegalStateException("Cloudinary no devolvio la direccion de la imagen");
    }
    return url.asText();
  }

  /**
   * No hay que leer nada: la foto la sirve Cloudinary directamente.
   *
   * Este metodo existe porque el puerto lo pide, y devolver vacio es la
   * respuesta correcta: si alguien intenta servir por aqui una imagen que vive
   * en la CDN, es mejor que no aparezca a que el servidor se ponga a
   * descargarla y reenviarla, que es justo lo que se quiso evitar.
   */
  @Override
  public byte[] leer(String nombre) {
    return new byte[0];
  }

  @Override
  public boolean existe(String nombre) {
    return nombre != null && nombre.startsWith("http");
  }

  @Override
  public void borrar(String nombre) {
    String id = identificadorDe(nombre);
    if (id == null) {
      return;
    }
    String marca = String.valueOf(Instant.now().getEpochSecond());
    var parametros = new TreeMap<String, String>();
    parametros.put("public_id", id);
    parametros.put("timestamp", marca);

    var cuerpo = new LinkedMultiValueMap<String, Object>();
    cuerpo.add("public_id", id);
    cuerpo.add("api_key", llave);
    cuerpo.add("timestamp", marca);
    cuerpo.add("signature", firmar(parametros));
    try {
      pedir("destroy", cuerpo);
    } catch (RuntimeException e) {
      // Que no se pueda borrar alla no puede impedir que la publicacion se
      // elimine aqui: lo que le importa al dueno es que deje de salir en el
      // sitio. La foto huerfana no hace dano.
    }
  }

  @Override
  public String tipoDeContenido(String nombre) {
    return "image/jpeg";
  }

  // ---------------------------------------------------------------------------

  /**
   * El envio a Cloudinary.
   *
   * El cuerpo se arma con un `LinkedMultiValueMap` y NO con `MultipartBodyBuilder`.
   * Aquella es la clase del mundo reactivo de Spring y arrastra
   * `org.reactivestreams.Publisher`, que este backend no tiene en el classpath:
   * compila sin quejarse y revienta al ejecutarse con un `NoClassDefFoundError`
   * la primera vez que alguien sube una foto. Este mapa lo convierte el
   * `FormHttpMessageConverter` de siempre, sin dependencias de mas.
   */
  private JsonNode pedir(String accion, MultiValueMap<String, Object> cuerpo) {
    String respuesta;
    try {
      respuesta =
          http.post()
              .uri("https://api.cloudinary.com/v1_1/%s/image/%s".formatted(nombreNube, accion))
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(cuerpo)
              .retrieve()
              .body(String.class);
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.NotFound e) {
      // Las dos formas en que Cloudinary dice «esas credenciales no sirven»:
      // 401 cuando la llave o el secreto estan mal, 404 cuando el nombre de la
      // cuenta no existe. Al dueno hay que decirselo asi, y no con un «error
      // del servidor», porque es el unico que puede arreglarlo.
      //
      // El nombre de la cuenta se nombra porque no es secreto y es el que mas
      // se equivoca: viaja en la direccion publica de cada foto. La llave y el
      // secreto NO se mencionan nunca.
      throw new ReglaDeNegocioError(
          "Cloudinary rechazo la subida. Revise las credenciales del servicio;"
              + " la cuenta configurada es \"" + nombreNube + "\"");
    } catch (RestClientException e) {
      throw new ReglaDeNegocioError(
          "No se pudo hablar con Cloudinary. Intente de nuevo en un momento");
    }
    try {
      return json.readTree(respuesta);
    } catch (Exception e) {
      throw new IllegalStateException("Cloudinary respondio algo que no se pudo leer", e);
    }
  }

  /**
   * La firma que exige Cloudinary: SHA-1 de los parametros ordenados mas el
   * secreto.
   *
   * El secreto entra en el calculo y NO viaja en la peticion. Por eso este
   * codigo vive en el servidor y no en el navegador: publicarlo permitiria a
   * cualquiera subir y borrar fotos de la cuenta del restaurante.
   */
  private String firmar(TreeMap<String, String> parametros) {
    StringBuilder cadena = new StringBuilder();
    parametros.forEach(
        (clave, valor) -> {
          if (cadena.length() > 0) {
            cadena.append('&');
          }
          cadena.append(clave).append('=').append(valor);
        });
    cadena.append(secreto);
    try {
      byte[] resumen =
          MessageDigest.getInstance("SHA-1")
              .digest(cadena.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : resumen) {
        hex.append("%02x".formatted(b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No hay SHA-1 disponible", e);
    }
  }

  /**
   * Saca el identificador de Cloudinary a partir de la direccion guardada.
   *
   * Una direccion se ve asi:
   *   https://res.cloudinary.com/NUBE/image/upload/v1712345678/elpatio/ab12cd.jpg
   * y el identificador es «elpatio/ab12cd»: lo que hay despues de la version y
   * sin la extension. Se hace asi, y no guardando el identificador aparte,
   * porque la direccion es lo unico que necesita el navegador y tener un solo
   * dato en la base evita que los dos se desincronicen.
   */
  static String identificadorDe(String direccion) {
    if (direccion == null || !direccion.startsWith("http")) {
      return null;
    }
    int subida = direccion.indexOf("/upload/");
    if (subida < 0) {
      return null;
    }
    String resto = direccion.substring(subida + "/upload/".length());
    // La version («v1712345678/») solo esta cuando Cloudinary la incluye.
    int barra = resto.indexOf('/');
    if (barra > 0 && resto.charAt(0) == 'v' && resto.substring(1, barra).chars().allMatch(Character::isDigit)) {
      resto = resto.substring(barra + 1);
    }
    int punto = resto.lastIndexOf('.');
    return punto > 0 ? resto.substring(0, punto) : resto;
  }
}

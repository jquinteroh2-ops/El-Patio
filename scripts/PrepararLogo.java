import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Prepara el logo del restaurante para el sitio.
 *
 * El restaurante entregó un JPG de 150x150: el arco con la monstera y la
 * orquídea sobre «EL PATIO», dibujado en negro sobre un fondo crema.
 *
 * <p><b>El problema que resuelve este programa.</b> El sitio es oscuro. Pegar
 * ese JPG tal cual en el encabezado pone un recuadro claro sobre un fondo casi
 * negro, y se ve exactamente como lo que es: una foto pegada encima, no un
 * logo. Lo que hace falta es lo que en imprenta se llama la versión en
 * negativo: el mismo dibujo con el trazo claro y sin fondo.
 *
 * <p>Como el logo es un dibujo de líneas de un solo color, esa versión se puede
 * derivar del original sin perder nada: lo oscuro de la imagen es el trazo y lo
 * claro es el fondo, así que basta con leer cuánta tinta hay en cada punto y
 * usar ESO como transparencia, pintando encima el color que convenga. No es un
 * recorte a mano ni una aproximación: cada trazo conserva su suavizado.
 *
 * <p>SE EJECUTA A MANO, no en la compilación: el logo cambia cada varios años.
 * Cuando el restaurante entregue el vectorial —que sigue siendo lo que hay que
 * pedirle— este archivo deja de hacer falta.
 *
 *   cd scripts && javac -d . PrepararLogo.java && java -cp . PrepararLogo
 */
public class PrepararLogo {

  /** Un píxel cuenta como fondo si está a menos de esto del color del margen. */
  private static final int TOLERANCIA = 18;

  /** Aire que se deja alrededor del dibujo tras recortar, en píxeles. */
  private static final int MARGEN = 4;

  /** El crema de la casa (crema-100). Es el color del trazo sobre lo oscuro. */
  private static final Color CREMA = new Color(0xF5, 0xEE, 0xE1);

  /** El fondo del sitio (onix-950), para la imagen que se comparte. */
  private static final Color ONIX = new Color(0x0A, 0x09, 0x08);

  public static void main(String[] argumentos) throws Exception {
    File entrada = new File(System.getProperty("user.home"), "Desktop/Logo el patio.jpg");
    if (!entrada.exists()) {
      System.err.println("No encontré el logo en " + entrada);
      System.exit(1);
    }

    BufferedImage original = ImageIO.read(entrada);
    System.out.println("Original: " + original.getWidth() + "x" + original.getHeight());

    BufferedImage recortado = recortarMargen(original);
    BufferedImage emblema = recortarEmblema(recortado);
    System.out.println("Logo: " + recortado.getWidth() + "  ·  Emblema: " + emblema.getWidth());

    File destino = new File("../public");

    // ---- El emblema, en claro y sin fondo ----
    //
    // Es el que va en el encabezado y en la pestaña, sobre oscuro. Sale sin
    // fondo para que se integre con lo que tenga detrás en vez de recortarse
    // contra ello.
    escribir(aNegativo(emblema, CREMA), 256, new File(destino, "emblema.png"));
    escribir(aNegativo(emblema, CREMA), 32, new File(destino, "favicon-32.png"));

    // ---- El logo completo, en claro y sin fondo ----
    //
    // Con su texto. Va donde el logo aparece solo y en grande.
    escribir(aNegativo(recortado, CREMA), 320, new File(destino, "logo.png"));

    // ---- Los dos con fondo sólido ----
    //
    // La imagen que se ve al compartir el enlace y el icono de iOS NO pueden
    // ser transparentes: WhatsApp y la pantalla de inicio ponen detrás el color
    // que les parezca —a veces blanco—, y un logo crema sobre blanco desaparece.
    // Se les hornea el fondo oscuro de la casa.
    escribir(sobreFondo(aNegativo(recortado, CREMA), ONIX), 320, new File(destino, "logo-og.png"));
    escribir(sobreFondo(aNegativo(emblema, CREMA), ONIX), 180, new File(destino, "logo-180.png"));

    System.out.println("Listo. Los archivos quedaron en public/");
  }

  /**
   * Convierte el dibujo en su versión en negativo: trazo del color pedido,
   * fondo transparente.
   *
   * La clave es de dónde sale la transparencia. No se recorta por un umbral
   * —eso dejaría los bordes dentados—, sino que se usa la propia oscuridad del
   * píxel: donde el original es negro del todo, el resultado es opaco; donde es
   * el crema del fondo, es transparente; y en los bordes suavizados de cada
   * trazo queda la media tinta que les corresponde. El dibujo conserva sus
   * líneas finas intactas.
   */
  private static BufferedImage aNegativo(BufferedImage origen, Color tinta) {
    Color fondo = new Color(origen.getRGB(0, 0));
    // Cuánto puede oscurecerse un píxel respecto al fondo. Es el recorrido
    // completo de tinta y sirve para llevar la oscuridad a una escala de 0 a 1.
    float recorrido = luminancia(fondo);
    if (recorrido <= 0) recorrido = 1;

    BufferedImage salida =
        new BufferedImage(origen.getWidth(), origen.getHeight(), BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < origen.getHeight(); y++) {
      for (int x = 0; x < origen.getWidth(); x++) {
        Color pixel = new Color(origen.getRGB(x, y));
        float oscuridad = (recorrido - luminancia(pixel)) / recorrido;
        int alfa = Math.max(0, Math.min(255, Math.round(oscuridad * 255)));
        salida.setRGB(
            x, y, (alfa << 24) | (tinta.getRed() << 16) | (tinta.getGreen() << 8) | tinta.getBlue());
      }
    }
    return salida;
  }

  /** Pone la imagen transparente sobre un fondo sólido. */
  private static BufferedImage sobreFondo(BufferedImage origen, Color fondo) {
    BufferedImage salida =
        new BufferedImage(origen.getWidth(), origen.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = salida.createGraphics();
    lienzo.setColor(fondo);
    lienzo.fillRect(0, 0, salida.getWidth(), salida.getHeight());
    lienzo.drawImage(origen, 0, 0, null);
    lienzo.dispose();
    return salida;
  }

  /**
   * Cuánta luz tiene un color, con los pesos con que el ojo la percibe.
   *
   * No es el promedio de los tres canales: el ojo es mucho más sensible al
   * verde que al azul, y promediando a partes iguales un trazo verde saldría
   * más claro de lo que se ve.
   */
  private static float luminancia(Color color) {
    return 0.2126f * color.getRed() + 0.7152f * color.getGreen() + 0.0722f * color.getBlue();
  }

  /**
   * Quita el margen de color plano que rodea al dibujo.
   *
   * El color de fondo se toma de la esquina superior izquierda en vez de
   * asumirlo blanco: el archivo viene sobre crema, y dar por hecho el blanco
   * dejaría el margen entero sin recortar.
   */
  private static BufferedImage recortarMargen(BufferedImage imagen) {
    Color fondo = new Color(imagen.getRGB(0, 0));

    int izquierda = imagen.getWidth();
    int derecha = 0;
    int arriba = imagen.getHeight();
    int abajo = 0;

    for (int y = 0; y < imagen.getHeight(); y++) {
      for (int x = 0; x < imagen.getWidth(); x++) {
        if (esFondo(new Color(imagen.getRGB(x, y)), fondo)) continue;
        if (x < izquierda) izquierda = x;
        if (x > derecha) derecha = x;
        if (y < arriba) arriba = y;
        if (y > abajo) abajo = y;
      }
    }

    if (derecha <= izquierda || abajo <= arriba) return imagen;

    izquierda = Math.max(0, izquierda - MARGEN);
    arriba = Math.max(0, arriba - MARGEN);
    derecha = Math.min(imagen.getWidth() - 1, derecha + MARGEN);
    abajo = Math.min(imagen.getHeight() - 1, abajo + MARGEN);

    // Se recorta a un CUADRADO centrado sobre el dibujo. Si saliera rectangular,
    // el navegador lo deformaría al meterlo en el hueco cuadrado del favicon.
    int ancho = derecha - izquierda + 1;
    int alto = abajo - arriba + 1;
    int lado = Math.max(ancho, alto);
    int x0 = Math.max(0, izquierda - (lado - ancho) / 2);
    int y0 = Math.max(0, arriba - (lado - alto) / 2);
    lado = Math.min(lado, Math.min(imagen.getWidth() - x0, imagen.getHeight() - y0));

    return imagen.getSubimage(x0, y0, lado, lado);
  }

  /**
   * Se queda con el arco y las plantas, sin el texto de abajo.
   *
   * El corte no está escrito a mano: se busca la franja horizontal vacía que
   * separa el dibujo del texto. Un valor fijo funcionaría con este archivo y
   * fallaría con el siguiente, y el siguiente lo va a preparar alguien que no
   * escribió esto.
   */
  private static BufferedImage recortarEmblema(BufferedImage logo) {
    Color fondo = new Color(logo.getRGB(0, 0));
    int alto = logo.getHeight();

    // Se busca desde el 45% hacia abajo: por encima de ahí está el arco, y sus
    // huecos internos también son franjas vacías que no hay que confundir con
    // la separación.
    int desde = (int) (alto * 0.45);
    int inicioVacio = -1;
    for (int y = desde; y < alto; y++) {
      if (filaVacia(logo, y, fondo)) {
        inicioVacio = y;
        break;
      }
    }
    if (inicioVacio < 0) return logo;

    // Dónde vuelve a haber tinta: es donde empieza el texto.
    int finVacio = inicioVacio;
    while (finVacio < alto && filaVacia(logo, finVacio, fondo)) finVacio++;

    // Se corta por la MITAD de la franja vacía, no justo al empezar.
    // Cortando al principio, las astas de la «E» y la «L» —que suben un poco
    // más que el resto— asoman por abajo como dos manchas sin explicación.
    int lado = Math.min((inicioVacio + finVacio) / 2, alto);
    int x0 = Math.max(0, (logo.getWidth() - lado) / 2);
    lado = Math.min(lado, logo.getWidth() - x0);
    return logo.getSubimage(x0, 0, lado, lado);
  }

  private static boolean filaVacia(BufferedImage imagen, int y, Color fondo) {
    for (int x = 0; x < imagen.getWidth(); x++) {
      if (!esFondo(new Color(imagen.getRGB(x, y)), fondo)) return false;
    }
    return true;
  }

  private static boolean esFondo(Color pixel, Color fondo) {
    return Math.abs(pixel.getRed() - fondo.getRed()) < TOLERANCIA
        && Math.abs(pixel.getGreen() - fondo.getGreen()) < TOLERANCIA
        && Math.abs(pixel.getBlue() - fondo.getBlue()) < TOLERANCIA;
  }

  /**
   * Escala y guarda como PNG.
   *
   * Conserva el canal de transparencia si lo hay. No se reduce a paleta: con
   * transparencia, una paleta obliga a elegir entre bordes dentados o tramado,
   * y estos archivos ya son pequeños porque casi todo el cuadro está vacío.
   */
  private static void escribir(BufferedImage origen, int lado, File destino) throws Exception {
    BufferedImage salida =
        new BufferedImage(
            lado,
            lado,
            origen.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);

    Graphics2D lienzo = salida.createGraphics();
    lienzo.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    lienzo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    lienzo.drawImage(origen, 0, 0, lado, lado, null);
    lienzo.dispose();

    ImageIO.write(salida, "png", destino);
    System.out.println("  " + destino.getName() + " (" + lado + "x" + lado + ")");
  }
}

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Prepara el logo del restaurante para el sitio.
 *
 * El archivo que entregó el restaurante es un JPG de 150x150 con un margen
 * crema alrededor. Este programa hace tres cosas con él:
 *
 *   1. Le recorta el margen sobrante, para que el dibujo llene el cuadro y no
 *      se vea diminuto dentro de un marco vacío al ponerlo en el encabezado.
 *   2. Lo pasa a PNG. El JPG comprime por bloques y en un dibujo de líneas
 *      finas sobre fondo plano eso deja un halo sucio alrededor de cada trazo.
 *   3. Saca los tamaños que necesita el sitio: el del encabezado, el de la
 *      pestaña y el del icono de iOS.
 *
 * SE EJECUTA A MANO Y UNA SOLA VEZ. No es parte de la compilación: el logo
 * cambia cada varios años, y montar una tarea de construcción para eso sería
 * más máquina que trabajo. Cuando el restaurante entregue el vectorial —que es
 * lo que hay que pedirle—, este archivo deja de hacer falta.
 *
 *   cd scripts && javac -d . PrepararLogo.java && java -cp . PrepararLogo
 */
public class PrepararLogo {

  /** Un píxel cuenta como fondo si está a menos de esto del color del margen. */
  private static final int TOLERANCIA = 18;

  /** Aire que se deja alrededor del dibujo tras recortar, en píxeles. */
  private static final int MARGEN = 4;

  public static void main(String[] argumentos) throws Exception {
    File entrada = new File(System.getProperty("user.home"), "Desktop/Logo el patio.jpg");
    if (!entrada.exists()) {
      System.err.println("No encontré el logo en " + entrada);
      System.exit(1);
    }

    BufferedImage original = ImageIO.read(entrada);
    System.out.println("Original: " + original.getWidth() + "x" + original.getHeight());

    BufferedImage recortado = recortarMargen(original);
    System.out.println("Recortado: " + recortado.getWidth() + "x" + recortado.getHeight());

    File destino = new File("../public");

    // El logo completo, con su texto. Va donde el logo aparece solo: la imagen
    // que se ve al compartir el enlace, y el icono de la pantalla de inicio.
    escribir(recortado, 320, new File(destino, "logo.png"));
    escribir(recortado, 180, new File(destino, "logo-180.png"));

    // El emblema sin el texto, para el encabezado.
    //
    // Hace falta porque el logo YA dice «EL PATIO» y en el encabezado el nombre
    // vuelve a estar como texto al lado: usar el logo completo ahí pondría el
    // nombre dos veces, una encima de otra. Recortar la parte de arriba deja el
    // arco con las plantas, que es lo que acompaña bien a un nombre escrito.
    BufferedImage emblema = recortarEmblema(recortado);
    System.out.println("Emblema: " + emblema.getWidth() + "x" + emblema.getHeight());
    escribir(emblema, 256, new File(destino, "emblema.png"));

    // La pestaña del navegador. Se usa el emblema y no el logo completo: a 32
    // píxeles el texto «EL PATIO» es una mancha ilegible que solo ensucia el
    // dibujo. Un icono a ese tamaño tiene que ser una silueta reconocible.
    escribir(emblema, 32, new File(destino, "favicon-32.png"));

    System.out.println("Listo. Los archivos quedaron en public/");
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
    // Centrado horizontalmente: el arco es más angosto que el cuadro completo.
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

  private static void escribir(BufferedImage origen, int lado, File destino) throws Exception {
    BufferedImage salida = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
    Graphics2D lienzo = salida.createGraphics();
    lienzo.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    lienzo.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    lienzo.drawImage(origen, 0, 0, lado, lado, null);
    lienzo.dispose();

    ImageIO.write(salida, "png", destino);
    System.out.println("  " + destino.getName() + " (" + lado + "x" + lado + ")");
  }
}

package co.elpatio.dominio.archivo;

import co.elpatio.dominio.error.ReglaDeNegocioError;

/**
 * Que es realmente un archivo, mirando sus primeros bytes.
 *
 * <p><b>Por que no basta la extension ni el Content-Type.</b> Los dos los pone
 * quien sube el archivo, y los dos se cambian en un segundo. Renombrar
 * {@code algo.exe} a {@code hoja-de-vida.pdf} es lo primero que intenta
 * cualquiera, y el navegador manda de buena fe el {@code Content-Type} que le
 * diga el sistema operativo. Lo unico que no se puede falsificar sin cambiar el
 * archivo de verdad son sus primeros bytes: un PDF empieza por {@code %PDF-} y
 * un JPEG por {@code FF D8 FF}. Eso es lo que se mira aqui.
 *
 * <p>Esto NO convierte el archivo en seguro: un PDF valido puede llevar dentro
 * cosas desagradables. Lo que consigue es que lo que se guarda sea del tipo que
 * dice ser, y que el navegador de quien lo descargue no acabe interpretando
 * como pagina algo que se subio como documento.
 */
public enum TipoDeArchivo {
  PDF("application/pdf", "pdf"),
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png");

  private final String tipoDeContenido;
  private final String extension;

  TipoDeArchivo(String tipoDeContenido, String extension) {
    this.tipoDeContenido = tipoDeContenido;
    this.extension = extension;
  }

  public String tipoDeContenido() { return tipoDeContenido; }

  public String extension() { return extension; }

  /**
   * Reconoce el tipo por los primeros bytes, o null si no es ninguno conocido.
   *
   * Devuelve null en vez de lanzar: quien llama sabe que tipos acepta en su
   * caso, y el mensaje de error util —«solo se aceptan PDF»— depende de eso.
   */
  public static TipoDeArchivo reconocer(byte[] contenido) {
    if (contenido == null || contenido.length < 8) return null;

    // %PDF- en ASCII. Los cinco bytes van al principio del archivo.
    if (contenido[0] == 0x25
        && contenido[1] == 0x50
        && contenido[2] == 0x44
        && contenido[3] == 0x46
        && contenido[4] == 0x2D) {
      return PDF;
    }

    // FF D8 FF: el marcador de inicio de imagen de JPEG.
    if ((contenido[0] & 0xFF) == 0xFF
        && (contenido[1] & 0xFF) == 0xD8
        && (contenido[2] & 0xFF) == 0xFF) {
      return JPEG;
    }

    // 89 50 4E 47 0D 0A 1A 0A: la firma completa de PNG. Los cuatro ultimos
    // bytes existen justamente para detectar transferencias que corrompieron
    // los saltos de linea, asi que se comprueban los ocho.
    if ((contenido[0] & 0xFF) == 0x89
        && contenido[1] == 0x50
        && contenido[2] == 0x4E
        && contenido[3] == 0x47
        && (contenido[4] & 0xFF) == 0x0D
        && (contenido[5] & 0xFF) == 0x0A
        && (contenido[6] & 0xFF) == 0x1A
        && (contenido[7] & 0xFF) == 0x0A) {
      return PNG;
    }

    return null;
  }

  /**
   * Exige que el archivo sea PDF de verdad.
   *
   * Lo usa la hoja de vida: el restaurante pidio PDF y una hoja de vida en
   * formato de imagen no se puede leer bien ni imprimir decentemente.
   */
  public static TipoDeArchivo exigirPdf(byte[] contenido) {
    TipoDeArchivo tipo = reconocer(contenido);
    if (tipo != PDF) {
      throw new ReglaDeNegocioError("El archivo debe ser un PDF. Convierta el documento y vuelva a intentar");
    }
    return tipo;
  }

  /**
   * Exige que sea PDF o imagen.
   *
   * Lo usa el adjunto de una PQR: quien se queja suele tener una foto del plato
   * o del recibo en el celular, y obligarlo a convertirla a PDF desde el
   * telefono es como se pierde una queja legitima.
   */
  public static TipoDeArchivo exigirPdfOImagen(byte[] contenido) {
    TipoDeArchivo tipo = reconocer(contenido);
    if (tipo == null) {
      throw new ReglaDeNegocioError("El archivo debe ser un PDF o una imagen (JPG o PNG)");
    }
    return tipo;
  }
}

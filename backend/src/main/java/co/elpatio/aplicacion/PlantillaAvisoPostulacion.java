package co.elpatio.aplicacion;

import co.elpatio.dominio.reclutamiento.Postulacion;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * El correo que le llega a recursos humanos cuando alguien deja su hoja de vida.
 *
 * <p><b>Por qué tablas y estilos en línea:</b> Gmail, Outlook y los clientes del
 * celular ignoran las hojas de estilo y la mitad del CSS moderno. Lo único que
 * se ve igual en todos es una tabla de 600 px con el estilo escrito en cada
 * etiqueta, que es como se arman los correos desde hace veinte años.
 *
 * <p><b>Por qué el logo va por URL:</b> Gmail no muestra SVG ni imágenes
 * incrustadas en base64. El PNG publicado en el sitio sí.
 *
 * <p><b>Todo lo que escribe el aspirante se escapa.</b> El formulario es
 * público: sin esto, cualquiera podría meter HTML en el correo de RR. HH.
 */
final class PlantillaAvisoPostulacion {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter FECHA =
      DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", Locale.forLanguageTag("es-CO"));

  // La paleta del sitio (tailwind.config.js).
  private static final String NOCHE = "#121110";
  private static final String ORO = "#C09A33";
  private static final String ORO_SUAVE = "#F0E2B8";
  private static final String CREMA = "#F7F2E8";
  private static final String TINTA = "#2C2825";
  private static final String GRIS = "#7A716A";
  private static final String LINEA = "#E6DCC8";

  private static final String SERIF = "Georgia,'Times New Roman',serif";
  private static final String SANS = "'Helvetica Neue',Helvetica,Arial,sans-serif";

  private PlantillaAvisoPostulacion() {}

  static String html(Postulacion p, String urlSitio, String nombreAdjunto) {
    String sitio = urlSitio == null ? "" : urlSitio.replaceAll("/+$", "");
    String nombre = texto(p.getNombreCompleto());
    String cargo = p.getCargoInteres() == null ? "" : p.getCargoInteres().etiqueta();
    String email = texto(p.getEmail());
    String telefono = texto(p.getTelefono());
    String documento =
        (p.getTipoDocumento() == null ? "" : p.getTipoDocumento().codigo())
            + " "
            + texto(p.getNumeroDocumento());
    String recibida = cuando(p.getFechaPostulacion());

    StringBuilder h = new StringBuilder(8000);
    h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<meta name=\"color-scheme\" content=\"light\">")
        .append("<title>Nueva hoja de vida</title></head>")
        .append("<body style=\"margin:0;padding:0;background:")
        .append(CREMA)
        .append(";\">");

    // Lo que se lee en la bandeja junto al asunto, antes de abrir el correo.
    h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
        .append(e(nombre))
        .append(" quiere trabajar con nosotros como ")
        .append(e(cargo))
        .append(". La hoja de vida va adjunta.</div>");

    h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:")
        .append(CREMA)
        .append(";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">");
    h.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"width:100%;max-width:600px;background:#ffffff;border-radius:14px;overflow:hidden;")
        .append("border:1px solid ")
        .append(LINEA)
        .append(";\">");

    // ---------- Cabecera: el logo sobre negro, como en el sitio ----------
    h.append("<tr><td align=\"center\" style=\"background:")
        .append(NOCHE)
        .append(";padding:36px 24px 28px;\">")
        .append("<img src=\"")
        .append(e(sitio))
        .append("/logo.png\" width=\"110\" height=\"110\" alt=\"Restaurante El Patio\" ")
        .append("style=\"display:block;width:110px;height:110px;border:0;margin:0 auto;\">")
        .append("<p style=\"margin:18px 0 0;font-family:")
        .append(SANS)
        .append(";font-size:11px;letter-spacing:4px;text-transform:uppercase;color:")
        .append(ORO)
        .append(";\">Recursos humanos</p>")
        .append("</td></tr>");
    h.append("<tr><td style=\"height:3px;line-height:3px;font-size:0;background:")
        .append(ORO)
        .append(";\">&nbsp;</td></tr>");

    // ---------- Título ----------
    h.append("<tr><td style=\"padding:36px 40px 8px;\">")
        .append("<h1 style=\"margin:0;font-family:")
        .append(SERIF)
        .append(";font-weight:normal;font-size:30px;line-height:1.2;color:")
        .append(TINTA)
        .append(";\">Nueva hoja de vida</h1>")
        .append("<p style=\"margin:10px 0 0;font-family:")
        .append(SANS)
        .append(";font-size:14px;line-height:1.6;color:")
        .append(GRIS)
        // Sin punto final: la hora ya termina en «p. m.».
        .append(";\">Llegó por el sitio web")
        .append(recibida.isEmpty() ? "" : " &nbsp;·&nbsp; " + e(recibida))
        .append("</p></td></tr>");

    // ---------- La persona ----------
    h.append("<tr><td style=\"padding:20px 40px 4px;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"background:")
        .append(CREMA)
        .append(";border-radius:12px;border-left:4px solid ")
        .append(ORO)
        .append(";\"><tr><td style=\"padding:22px 24px;\">")
        .append("<p style=\"margin:0;font-family:")
        .append(SERIF)
        .append(";font-size:24px;line-height:1.25;color:")
        .append(TINTA)
        .append(";\">")
        .append(e(nombre))
        .append("</p>")
        .append("<p style=\"margin:12px 0 0;\"><span style=\"display:inline-block;padding:5px 14px;")
        .append("border:1px solid ")
        .append(ORO)
        .append(";border-radius:999px;background:")
        .append(ORO_SUAVE)
        .append(";font-family:")
        .append(SANS)
        .append(";font-size:12px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:#7A5E1F;\">")
        .append(e(cargo))
        .append("</span></p>")
        .append("</td></tr></table></td></tr>");

    // ---------- Datos de contacto ----------
    h.append("<tr><td style=\"padding:20px 40px 0;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
    fila(h, "Documento", e(documento));
    fila(
        h,
        "Correo",
        "<a href=\"mailto:" + e(email) + "\" style=\"color:" + TINTA + ";text-decoration:underline;\">"
            + e(email) + "</a>");
    fila(
        h,
        "Teléfono",
        "<a href=\"tel:" + e(soloDigitos(telefono)) + "\" style=\"color:" + TINTA + ";text-decoration:none;\">"
            + e(telefono) + "</a>");
    h.append("</table></td></tr>");

    // ---------- El mensaje, si dejó uno ----------
    String mensaje = texto(p.getMensaje()).trim();
    if (!mensaje.isEmpty()) {
      h.append("<tr><td style=\"padding:26px 40px 0;\">")
          .append("<p style=\"margin:0 0 10px;font-family:")
          .append(SANS)
          .append(";font-size:11px;letter-spacing:2px;text-transform:uppercase;color:")
          .append(GRIS)
          .append(";\">Lo que nos cuenta</p>")
          .append("<p style=\"margin:0;padding:4px 0 4px 18px;border-left:2px solid ")
          .append(LINEA)
          .append(";font-family:")
          .append(SERIF)
          .append(";font-style:italic;font-size:16px;line-height:1.65;color:")
          .append(TINTA)
          .append(";\">«")
          .append(e(mensaje).replace("\r\n", "\n").replace("\n", "<br>"))
          .append("»</p></td></tr>");
    }

    // ---------- Botones para responder ----------
    String asuntoRespuesta = "Restaurante El Patio - su hoja de vida";
    String saludo = "Hola " + primerNombre(nombre) + ", le escribimos del Restaurante El Patio por su hoja de vida.";
    h.append("<tr><td style=\"padding:32px 40px 0;\">")
        .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
    boton(
        h,
        "mailto:" + email + "?subject=" + url(asuntoRespuesta),
        "Responder por correo",
        true);
    String whatsapp = numeroWhatsapp(telefono);
    if (!whatsapp.isEmpty()) {
      h.append("<td style=\"width:10px;\">&nbsp;</td>");
      boton(h, "https://wa.me/" + whatsapp + "?text=" + url(saludo), "Escribir por WhatsApp", false);
    }
    h.append("</tr></table></td></tr>");

    // ---------- El adjunto ----------
    h.append("<tr><td style=\"padding:28px 40px 0;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"border:1px dashed ")
        .append(LINEA)
        .append(";border-radius:10px;\"><tr><td style=\"padding:14px 18px;font-family:")
        .append(SANS)
        .append(";font-size:13px;line-height:1.5;color:")
        .append(GRIS)
        .append(";\">&#128206;&nbsp; <b style=\"color:")
        .append(TINTA)
        .append(";\">")
        .append(e(nombreAdjunto))
        .append("</b> va adjunta a este correo. También queda en el panel, en ")
        .append("<a href=\"")
        .append(e(sitio))
        .append("/admin/postulaciones\" style=\"color:#7A5E1F;\">Postulaciones</a>.")
        .append("</td></tr></table></td></tr>");

    // ---------- Pie ----------
    h.append("<tr><td style=\"padding:36px 40px 0;\">&nbsp;</td></tr>");
    h.append("<tr><td align=\"center\" style=\"background:")
        .append(NOCHE)
        .append(";padding:26px 32px;\">")
        .append("<p style=\"margin:0;font-family:")
        .append(SERIF)
        .append(";font-size:16px;letter-spacing:5px;color:")
        .append(ORO_SUAVE)
        .append(";\">EL PATIO</p>")
        .append("<p style=\"margin:8px 0 0;font-family:")
        .append(SANS)
        .append(";font-size:12px;line-height:1.6;color:#B5ADA6;\">")
        .append("Calle 26 #31-2, Turbaco, Bolívar &nbsp;·&nbsp; ")
        .append("<a href=\"")
        .append(e(sitio))
        .append("\" style=\"color:")
        .append(ORO)
        .append(";text-decoration:none;\">restauranteelpatio.com</a></p>")
        .append("<p style=\"margin:12px 0 0;font-family:")
        .append(SANS)
        .append(";font-size:11px;line-height:1.5;color:#8A8079;\">")
        .append("Aviso automático del sitio web. Son datos personales: se usan solo para selección de ")
        .append("personal, conforme a la Ley 1581 de 2012.</p>")
        .append("</td></tr>");

    h.append("</table></td></tr></table></body></html>");
    return h.toString();
  }

  private static void fila(StringBuilder h, String etiqueta, String valorHtml) {
    h.append("<tr><td style=\"padding:12px 0;border-bottom:1px solid ")
        .append(LINEA)
        .append(";width:110px;vertical-align:top;font-family:")
        .append(SANS)
        .append(";font-size:11px;letter-spacing:2px;text-transform:uppercase;color:")
        .append(GRIS)
        .append(";\">")
        .append(etiqueta)
        .append("</td><td style=\"padding:12px 0;border-bottom:1px solid ")
        .append(LINEA)
        .append(";font-family:")
        .append(SANS)
        .append(";font-size:15px;color:")
        .append(TINTA)
        .append(";\">")
        .append(valorHtml)
        .append("</td></tr>");
  }

  private static void boton(StringBuilder h, String enlace, String etiqueta, boolean principal) {
    h.append("<td style=\"border-radius:8px;background:")
        .append(principal ? ORO : "#ffffff")
        .append(";border:1px solid ")
        .append(principal ? ORO : TINTA)
        .append(";\"><a href=\"")
        .append(e(enlace))
        .append("\" style=\"display:inline-block;padding:13px 22px;font-family:")
        .append(SANS)
        .append(";font-size:14px;font-weight:bold;color:")
        .append(principal ? NOCHE : TINTA)
        .append(";text-decoration:none;border-radius:8px;\">")
        .append(etiqueta)
        .append("</a></td>");
  }

  private static String cuando(Instant instante) {
    if (instante == null) return "";
    return FECHA.format(instante.atZone(ZONA));
  }

  private static String texto(String valor) {
    return valor == null ? "" : valor;
  }

  private static String primerNombre(String nombre) {
    String limpio = nombre.trim();
    int espacio = limpio.indexOf(' ');
    return espacio < 0 ? limpio : limpio.substring(0, espacio);
  }

  private static String soloDigitos(String valor) {
    return valor.replaceAll("[^0-9+]", "");
  }

  /** Celular colombiano de 10 dígitos con o sin el 57; otra cosa no se adivina. */
  static String numeroWhatsapp(String telefono) {
    String digitos = telefono == null ? "" : telefono.replaceAll("\\D", "");
    if (digitos.length() == 10 && digitos.startsWith("3")) return "57" + digitos;
    if (digitos.length() == 12 && digitos.startsWith("573")) return digitos;
    return "";
  }

  private static String url(String valor) {
    return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** Escapa para HTML, también dentro de atributos. */
  static String e(String valor) {
    if (valor == null) return "";
    StringBuilder s = new StringBuilder(valor.length() + 16);
    for (char c : valor.toCharArray()) {
      switch (c) {
        case '&' -> s.append("&amp;");
        case '<' -> s.append("&lt;");
        case '>' -> s.append("&gt;");
        case '"' -> s.append("&quot;");
        case '\'' -> s.append("&#39;");
        default -> s.append(c);
      }
    }
    return s.toString();
  }
}

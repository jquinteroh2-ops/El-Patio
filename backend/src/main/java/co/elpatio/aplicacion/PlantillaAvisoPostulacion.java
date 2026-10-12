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
 * <p>Se arma como una carta con membrete: fondo blanco, el logo arriba, una
 * línea dorada y los datos en una tabla. Sin tarjetas, etiquetas ni íconos: el
 * restaurante lo quería sobrio, como los correos de una empresa.
 *
 * <p><b>Por qué tablas y estilos en línea:</b> Gmail, Outlook y los clientes del
 * celular ignoran las hojas de estilo. Lo único que se ve igual en todos es una
 * tabla de 600 px con el estilo escrito en cada etiqueta.
 *
 * <p><b>Por qué el logo va por URL:</b> Gmail no muestra SVG ni imágenes
 * incrustadas en base64. Es {@code public/logo-correo.png}, el logo del sitio
 * en dorado oscuro para que se vea sobre blanco.
 *
 * <p><b>Todo lo que escribe el aspirante se escapa.</b> El formulario es
 * público: sin esto, cualquiera podría meter HTML en el correo de RR. HH.
 */
final class PlantillaAvisoPostulacion {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");
  private static final DateTimeFormatter DIA =
      DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));
  private static final DateTimeFormatter HORA =
      DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"));

  private static final String ORO = "#C09A33";
  private static final String ENLACE = "#7A5E1F";
  private static final String TINTA = "#1F1F1F";
  private static final String TEXTO = "#3A3A3A";
  private static final String GRIS = "#6B6B6B";
  private static final String LINEA = "#E8E8E8";
  private static final String FUENTE = "Arial,Helvetica,sans-serif";

  private PlantillaAvisoPostulacion() {}

  static String html(Postulacion p, String urlSitio, String nombreAdjunto) {
    String sitio = urlSitio == null ? "" : urlSitio.replaceAll("/+$", "");
    String nombre = texto(p.getNombreCompleto()).trim();
    String cargo = p.getCargoInteres() == null ? "" : p.getCargoInteres().etiqueta();
    String email = texto(p.getEmail()).trim();
    String telefono = texto(p.getTelefono()).trim();
    String documento =
        ((p.getTipoDocumento() == null ? "" : p.getTipoDocumento().codigo())
                + " "
                + texto(p.getNumeroDocumento()))
            .trim();

    StringBuilder h = new StringBuilder(6000);
    h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<title>Nueva postulación</title></head>")
        .append("<body style=\"margin:0;padding:0;background:#F3F3F1;\">");

    // Lo que se lee en la bandeja junto al asunto, antes de abrir el correo.
    h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
        .append(e(nombre))
        .append(" se postuló al cargo de ")
        .append(e(cargo))
        .append(". La hoja de vida va adjunta.</div>");

    h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"background:#F3F3F1;\"><tr><td align=\"center\" style=\"padding:28px 12px;\">")
        .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"width:100%;max-width:600px;background:#FFFFFF;border:1px solid ")
        .append(LINEA)
        .append(";\">");

    // ---------- Membrete ----------
    h.append("<tr><td style=\"padding:28px 40px 22px;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
        .append("<td style=\"width:64px;vertical-align:middle;\"><img src=\"")
        .append(e(sitio))
        .append("/logo-correo.png\" width=\"56\" height=\"56\" alt=\"Restaurante El Patio\" ")
        .append("style=\"display:block;width:56px;height:56px;border:0;\"></td>")
        .append("<td style=\"vertical-align:middle;padding-left:14px;font-family:")
        .append(FUENTE)
        .append(";\"><div style=\"font-size:15px;font-weight:bold;color:")
        .append(TINTA)
        .append(";\">Restaurante El Patio</div>")
        .append("<div style=\"font-size:13px;color:")
        .append(GRIS)
        .append(";margin-top:2px;\">Recursos Humanos</div></td>")
        .append("</tr></table></td></tr>");
    h.append("<tr><td style=\"padding:0 40px;\"><div style=\"height:2px;line-height:2px;font-size:0;background:")
        .append(ORO)
        .append(";\">&nbsp;</div></td></tr>");

    // ---------- Cuerpo ----------
    h.append("<tr><td style=\"padding:28px 40px 8px;font-family:")
        .append(FUENTE)
        .append(";color:")
        .append(TEXTO)
        .append(";font-size:14px;line-height:1.6;\">")
        .append("<h1 style=\"margin:0 0 12px;font-size:19px;line-height:1.3;font-weight:bold;color:")
        .append(TINTA)
        .append(";\">Nueva postulación: ")
        .append(e(cargo))
        .append("</h1>")
        .append("<p style=\"margin:0 0 22px;\">Se recibió una hoja de vida a través del sitio web")
        .append(cuando(p.getFechaPostulacion()))
        .append(". Estos son los datos del aspirante:</p>");

    h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"border-top:1px solid ")
        .append(LINEA)
        .append(";\">");
    fila(h, "Nombre", "<b style=\"color:" + TINTA + ";\">" + e(nombre) + "</b>");
    fila(h, "Cargo", e(cargo));
    fila(h, "Documento", e(documento));
    fila(h, "Correo", enlace("mailto:" + email, email));
    fila(h, "Teléfono", e(telefono));
    h.append("</table>");

    String mensaje = texto(p.getMensaje()).trim();
    if (!mensaje.isEmpty()) {
      h.append("<p style=\"margin:24px 0 6px;font-weight:bold;color:")
          .append(TINTA)
          .append(";\">Mensaje del aspirante</p>")
          .append("<p style=\"margin:0;\">")
          .append(e(mensaje).replace("\r\n", "\n").replace("\n", "<br>"))
          .append("</p>");
    }

    h.append("<p style=\"margin:24px 0 0;\"><b style=\"color:")
        .append(TINTA)
        .append(";\">Adjunto:</b> ")
        .append(e(nombreAdjunto))
        .append("</p>");

    // ---------- Para responder: enlaces de texto, como en una carta ----------
    String asuntoRespuesta = "Restaurante El Patio - Proceso de selección";
    String saludo =
        "Buen día, " + primerNombre(nombre) + ". Le escribimos del Restaurante El Patio por su hoja de vida.";
    h.append("<p style=\"margin:24px 0 0;\">")
        .append(enlace("mailto:" + email + "?subject=" + url(asuntoRespuesta), "Responder por correo"));
    String whatsapp = numeroWhatsapp(telefono);
    if (!whatsapp.isEmpty()) {
      h.append(" &nbsp;&nbsp;|&nbsp;&nbsp; ")
          .append(enlace("https://wa.me/" + whatsapp + "?text=" + url(saludo), "Escribir por WhatsApp"));
    }
    h.append(" &nbsp;&nbsp;|&nbsp;&nbsp; ")
        .append(enlace(sitio + "/admin/postulaciones", "Ver en el panel"))
        .append("</p>");
    h.append("</td></tr>");

    // ---------- Pie ----------
    h.append("<tr><td style=\"padding:28px 40px 30px;\">")
        .append("<div style=\"border-top:1px solid ")
        .append(LINEA)
        .append(";padding-top:16px;font-family:")
        .append(FUENTE)
        .append(";font-size:12px;line-height:1.6;color:")
        .append(GRIS)
        .append(";\">")
        .append("Restaurante El Patio &middot; Calle 26 #31-2, Turbaco, Bolívar &middot; ")
        .append(enlace(sitio, sitio.replaceFirst("^https?://", "")))
        .append("<br>Mensaje generado automáticamente por el sitio web. Contiene datos personales ")
        .append("del aspirante, que se tratan conforme a la Ley 1581 de 2012.")
        .append("</div></td></tr>");

    h.append("</table></td></tr></table></body></html>");
    return h.toString();
  }

  private static void fila(StringBuilder h, String etiqueta, String valorHtml) {
    h.append("<tr><td style=\"padding:10px 16px 10px 0;border-bottom:1px solid ")
        .append(LINEA)
        .append(";width:120px;vertical-align:top;font-family:")
        .append(FUENTE)
        .append(";font-size:13px;color:")
        .append(GRIS)
        .append(";\">")
        .append(etiqueta)
        .append("</td><td style=\"padding:10px 0;border-bottom:1px solid ")
        .append(LINEA)
        .append(";font-family:")
        .append(FUENTE)
        .append(";font-size:14px;color:")
        .append(TEXTO)
        .append(";\">")
        .append(valorHtml)
        .append("</td></tr>");
  }

  private static String enlace(String destino, String etiqueta) {
    return "<a href=\"" + e(destino) + "\" style=\"color:" + ENLACE + ";text-decoration:underline;\">"
        + e(etiqueta) + "</a>";
  }

  /** « el 12 de octubre de 2026 a las 3:25 p. m.», o nada si no hay fecha. */
  private static String cuando(Instant instante) {
    if (instante == null) return "";
    var local = instante.atZone(ZONA);
    return " el " + e(DIA.format(local)) + " a las " + e(HORA.format(local)).replaceAll("\\.$", "");
  }

  private static String texto(String valor) {
    return valor == null ? "" : valor;
  }

  private static String primerNombre(String nombre) {
    int espacio = nombre.indexOf(' ');
    return espacio < 0 ? nombre : nombre.substring(0, espacio);
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

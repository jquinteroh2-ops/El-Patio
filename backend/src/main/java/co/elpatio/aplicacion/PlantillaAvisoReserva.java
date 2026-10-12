package co.elpatio.aplicacion;

import static co.elpatio.aplicacion.PlantillaAvisoPostulacion.e;
import static co.elpatio.aplicacion.PlantillaAvisoPostulacion.numeroWhatsapp;

import co.elpatio.dominio.reserva.Ocasion;
import co.elpatio.dominio.reserva.Reserva;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * El correo que le llega al buzón de reservas cuando alguien reserva por el sitio.
 *
 * <p>Es la misma carta con membrete del aviso de hojas de vida —ver
 * {@link PlantillaAvisoPostulacion}, que explica por qué tablas, estilos en
 * línea y el logo por URL—, con los datos de la reserva y lo que hace falta
 * para contestarla: confirmar por WhatsApp con el mensaje ya escrito, llamar, o
 * abrirla en el panel para separarle la mesa.
 *
 * <p>Todo lo que escribe el cliente se escapa: el formulario es público.
 */
final class PlantillaAvisoReserva {

  private static final ZoneId ZONA = ZoneId.of("America/Bogota");
  private static final Locale ES = Locale.forLanguageTag("es-CO");
  private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
  private static final DateTimeFormatter DIA_CON_ANIO =
      DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
  private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ES);

  private static final String ORO = "#C09A33";
  private static final String ENLACE = "#7A5E1F";
  private static final String TINTA = "#1F1F1F";
  private static final String TEXTO = "#3A3A3A";
  private static final String GRIS = "#6B6B6B";
  private static final String LINEA = "#E8E8E8";
  private static final String FUENTE = "Arial,Helvetica,sans-serif";

  private PlantillaAvisoReserva() {}

  /** «sábado 18 de octubre de 2026». */
  static String dia(Instant cuando) {
    return mayusculaInicial(DIA_CON_ANIO.format(cuando.atZone(ZONA)));
  }

  /** «8:00 p. m.». */
  static String hora(Instant cuando) {
    return HORA.format(cuando.atZone(ZONA));
  }

  static String ocasion(Ocasion o) {
    if (o == null) return "";
    return switch (o) {
      case CUMPLEANOS -> "Cumpleaños";
      case ANIVERSARIO -> "Aniversario";
      case NEGOCIOS -> "Reunión de negocios";
      case NINGUNA -> "";
    };
  }

  /** El texto plano del aviso: lo que ve quien no tiene HTML. */
  static String texto(Reserva r) {
    StringBuilder t = new StringBuilder("Llegó una solicitud de reserva por el sitio web.\n\n");
    t.append("Nombre: ").append(r.getNombreCliente()).append('\n');
    t.append("Teléfono: ").append(r.getTelefono()).append('\n');
    t.append("Fecha: ").append(dia(r.getFechaHora())).append('\n');
    t.append("Hora: ").append(hora(r.getFechaHora())).append('\n');
    t.append("Personas: ").append(r.getPersonas()).append('\n');
    if (!ocasion(r.getOcasion()).isEmpty()) t.append("Ocasión: ").append(ocasion(r.getOcasion())).append('\n');
    if (r.getNotas() != null && !r.getNotas().isBlank()) {
      t.append("\nNotas del cliente:\n").append(r.getNotas().trim()).append('\n');
    }
    t.append("\nConfírmela por WhatsApp y sepárele la mesa en el panel, en Reservas.");
    return t.toString();
  }

  static String html(Reserva r, String urlSitio) {
    String sitio = urlSitio == null ? "" : urlSitio.replaceAll("/+$", "");
    boolean conSitio = !sitio.isBlank();
    String nombre = r.getNombreCliente() == null ? "" : r.getNombreCliente().trim();
    String telefono = r.getTelefono() == null ? "" : r.getTelefono().trim();
    ZonedDateTime cuando = r.getFechaHora().atZone(ZONA);
    String dia = dia(r.getFechaHora());
    String hora = hora(r.getFechaHora());
    String personas = r.getPersonas() + (r.getPersonas() == 1 ? " persona" : " personas");
    String ocasion = ocasion(r.getOcasion());

    StringBuilder h = new StringBuilder(6000);
    h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<title>Nueva reserva</title></head>")
        .append("<body style=\"margin:0;padding:0;background:#F3F3F1;\">");

    // Lo que se lee en la bandeja junto al asunto, antes de abrir el correo.
    h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
        .append(e(nombre)).append(" reservó para ").append(e(personas)).append(" el ")
        .append(e(DIA.format(cuando))).append(" a las ").append(e(hora)).append(".</div>");

    h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"background:#F3F3F1;\"><tr><td align=\"center\" style=\"padding:28px 12px;\">")
        .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"width:100%;max-width:600px;background:#FFFFFF;border:1px solid ").append(LINEA).append(";\">");

    // ---------- Membrete ----------
    h.append("<tr><td style=\"padding:28px 40px 22px;\">")
        .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
    if (conSitio) {
      h.append("<td style=\"width:64px;vertical-align:middle;\"><img src=\"").append(e(sitio))
          .append("/logo-correo.png\" width=\"56\" height=\"56\" alt=\"Restaurante El Patio\" ")
          .append("style=\"display:block;width:56px;height:56px;border:0;\"></td>");
    }
    h.append("<td style=\"vertical-align:middle;padding-left:").append(conSitio ? "14px" : "0")
        .append(";font-family:").append(FUENTE).append(";\">")
        .append("<div style=\"font-size:15px;font-weight:bold;color:").append(TINTA).append(";\">Restaurante El Patio</div>")
        .append("<div style=\"font-size:13px;color:").append(GRIS).append(";margin-top:2px;\">Reservas</div></td>")
        .append("</tr></table></td></tr>");
    h.append("<tr><td style=\"padding:0 40px;\"><div style=\"height:2px;line-height:2px;font-size:0;background:")
        .append(ORO).append(";\">&nbsp;</div></td></tr>");

    // ---------- Cuerpo ----------
    h.append("<tr><td style=\"padding:28px 40px 8px;font-family:").append(FUENTE).append(";color:").append(TEXTO)
        .append(";font-size:14px;line-height:1.6;\">")
        .append("<h1 style=\"margin:0 0 12px;font-size:19px;line-height:1.3;font-weight:bold;color:").append(TINTA)
        .append(";\">Nueva solicitud de reserva</h1>")
        .append("<p style=\"margin:0 0 22px;\">Llegó por el sitio web y está esperando confirmación. ")
        .append("Estos son los datos:</p>");

    h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
        .append("style=\"border-top:1px solid ").append(LINEA).append(";\">");
    fila(h, "Nombre", "<b style=\"color:" + TINTA + ";\">" + e(nombre) + "</b>");
    fila(h, "Fecha", "<b style=\"color:" + TINTA + ";\">" + e(dia) + "</b>");
    fila(h, "Hora", "<b style=\"color:" + TINTA + ";\">" + e(hora) + "</b>");
    fila(h, "Personas", e(personas));
    if (!ocasion.isEmpty()) fila(h, "Ocasión", e(ocasion));
    fila(h, "Teléfono", enlace("tel:" + telefono.replaceAll("[^0-9+]", ""), telefono));
    h.append("</table>");

    String notas = r.getNotas() == null ? "" : r.getNotas().trim();
    if (!notas.isEmpty()) {
      h.append("<p style=\"margin:24px 0 6px;font-weight:bold;color:").append(TINTA).append(";\">Notas del cliente</p>")
          .append("<p style=\"margin:0;\">").append(e(notas).replace("\r\n", "\n").replace("\n", "<br>")).append("</p>");
    }

    // ---------- Para contestar ----------
    String confirmacion =
        "Hola, " + primerNombre(nombre) + ". Le escribimos del Restaurante El Patio para confirmar su reserva el "
            + DIA.format(cuando) + " a las " + hora + ", para " + personas + ". ¡Lo esperamos!";
    h.append("<p style=\"margin:24px 0 0;\">");
    String whatsapp = numeroWhatsapp(telefono);
    boolean primero = true;
    if (!whatsapp.isEmpty()) {
      h.append(enlace("https://wa.me/" + whatsapp + "?text=" + url(confirmacion), "Confirmar por WhatsApp"));
      primero = false;
    }
    if (!primero) h.append(" &nbsp;&nbsp;|&nbsp;&nbsp; ");
    h.append(enlace("tel:" + telefono.replaceAll("[^0-9+]", ""), "Llamar"));
    if (conSitio) {
      h.append(" &nbsp;&nbsp;|&nbsp;&nbsp; ").append(enlace(sitio + "/admin/reservas", "Ver en el panel"));
    }
    h.append("</p>")
        .append("<p style=\"margin:14px 0 0;font-size:13px;color:").append(GRIS).append(";\">")
        .append("La reserva queda como solicitada hasta que se confirme en el panel.</p>")
        .append("</td></tr>");

    // ---------- Pie ----------
    h.append("<tr><td style=\"padding:28px 40px 30px;\">")
        .append("<div style=\"border-top:1px solid ").append(LINEA).append(";padding-top:16px;font-family:").append(FUENTE)
        .append(";font-size:12px;line-height:1.6;color:").append(GRIS).append(";\">")
        .append("Restaurante El Patio &middot; Calle 26 #31-2, Turbaco, Bolívar")
        .append(conSitio ? " &middot; " + enlace(sitio, sitio.replaceFirst("^https?://", "")) : "")
        .append("<br>Mensaje generado automáticamente por el sitio web.")
        .append("</div></td></tr>");

    h.append("</table></td></tr></table></body></html>");
    return h.toString();
  }

  private static void fila(StringBuilder h, String etiqueta, String valorHtml) {
    h.append("<tr><td style=\"padding:10px 16px 10px 0;border-bottom:1px solid ").append(LINEA)
        .append(";width:120px;vertical-align:top;font-family:").append(FUENTE).append(";font-size:13px;color:")
        .append(GRIS).append(";\">").append(etiqueta).append("</td><td style=\"padding:10px 0;border-bottom:1px solid ")
        .append(LINEA).append(";font-family:").append(FUENTE).append(";font-size:14px;color:").append(TEXTO)
        .append(";\">").append(valorHtml).append("</td></tr>");
  }

  private static String enlace(String destino, String etiqueta) {
    return "<a href=\"" + e(destino) + "\" style=\"color:" + ENLACE + ";text-decoration:underline;\">"
        + e(etiqueta) + "</a>";
  }

  private static String primerNombre(String nombre) {
    int espacio = nombre.indexOf(' ');
    return espacio < 0 ? nombre : nombre.substring(0, espacio);
  }

  private static String mayusculaInicial(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String url(String valor) {
    return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
  }
}

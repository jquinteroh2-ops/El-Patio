package co.elpatio.infraestructura.correo;

import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * El correo que sí sale, por SMTP.
 *
 * Solo se activa si hay un usuario configurado ({@code elpatio.correo.usuario},
 * que en Railway llega por {@code ELPATIO_SMTP_USUARIO}). Sin esa variable esta
 * clase ni se crea, y el sistema sigue con {@link CorreoRegistrado}: un
 * despliegue sin proveedor contratado -como La Carreta mientras no tenga el
 * suyo propio, aunque corra el mismo código- no se entera de este cambio.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "elpatio.correo", name = "usuario")
public class CorreoPorSmtp implements NotificadorPorCorreo {

  private static final Logger registro = LoggerFactory.getLogger(CorreoPorSmtp.class);

  private final JavaMailSender remitente;
  private final String direccionRemitente;

  public CorreoPorSmtp(
      JavaMailSender remitente, @Value("${elpatio.correo.usuario}") String direccionRemitente) {
    this.remitente = remitente;
    this.direccionRemitente = direccionRemitente;
  }

  @Override
  public void enviar(String destinatario, String asunto, String cuerpo) {
    SimpleMailMessage mensaje = new SimpleMailMessage();
    mensaje.setFrom(direccionRemitente);
    mensaje.setTo(destinatario);
    mensaje.setSubject(asunto);
    mensaje.setText(cuerpo);
    remitente.send(mensaje);
    registro.info("Correo enviado a {}", destinatario);
  }

  @Override
  public boolean estaActivo() {
    return true;
  }
}

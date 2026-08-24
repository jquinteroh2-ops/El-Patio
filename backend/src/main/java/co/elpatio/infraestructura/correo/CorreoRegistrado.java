package co.elpatio.infraestructura.correo;

import co.elpatio.dominio.puertos.NotificadorPorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * El correo que hoy no sale a ninguna parte.
 *
 * Escribe en la bitacora lo que habria enviado y devuelve el control. No es un
 * error ni un pendiente olvidado: el restaurante no tiene proveedor de correo
 * contratado, y el sistema tiene que funcionar completo sin el.
 *
 * <p>Registra a nivel INFO y con el destinatario y el asunto, pero NO el
 * cuerpo. El cuerpo de un acuse de PQR lleva el nombre de quien se quejo y de
 * que se quejo: son datos personales, y la bitacora de produccion la lee mas
 * gente que la bandeja del administrador.
 *
 * <p><b>Cuando se contrate proveedor</b>, se escribe otra implementacion de
 * {@link NotificadorPorCorreo}, se le pone {@code @Primary} o se condiciona
 * esta a que no haya otra, y no se toca una linea de los servicios que la usan.
 */
@Component
public class CorreoRegistrado implements NotificadorPorCorreo {

  private static final Logger registro = LoggerFactory.getLogger(CorreoRegistrado.class);

  @Override
  public void enviar(String destinatario, String asunto, String cuerpo) {
    registro.info(
        "[correo no enviado: sin proveedor configurado] para={} asunto={}", destinatario, asunto);
  }

  @Override
  public boolean estaActivo() {
    return false;
  }
}

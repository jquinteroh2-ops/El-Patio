package co.elpatio.infraestructura.whatsapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Deja constancia de que ya se proceso un `message.id` de WhatsApp.
 *
 * Meta reenvia el mismo mensaje si el primer 200 se demoro o se perdio; la
 * clave primaria sobre `message_id` es lo que hace que un reintento no vuelva
 * a mover la conversacion un paso mas.
 */
@Entity
@Table(name = "mensajes_whatsapp_procesados")
public class FilaMensajeProcesado {

  @Id
  @Column(name = "message_id")
  private String messageId;

  @Column(name = "procesado_en")
  private Instant procesadoEn;

  public FilaMensajeProcesado() {}

  public FilaMensajeProcesado(String messageId, Instant procesadoEn) {
    this.messageId = messageId;
    this.procesadoEn = procesadoEn;
  }
}

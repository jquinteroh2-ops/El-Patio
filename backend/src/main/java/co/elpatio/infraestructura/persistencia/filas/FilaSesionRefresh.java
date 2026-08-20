package co.elpatio.infraestructura.persistencia.filas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Refresh token emitido a un dispositivo.
 *
 * Se guarda el hash y no el token: si alguien se lleva una copia de la base, no
 * se lleva sesiones utilizables. La fila existe para poder revocar: sin ella,
 * cerrar sesion en una tablet perdida no serviria de nada hasta que el token
 * expirara solo.
 */
@Entity
@Table(name = "sesiones_refresh")
public class FilaSesionRefresh {

  @Id private String id;

  @Column(name = "usuario_id")
  private String usuarioId;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "emitido_en")
  private Instant emitidoEn;

  @Column(name = "expira_en")
  private Instant expiraEn;

  private boolean revocado;

  public FilaSesionRefresh() {}

  public FilaSesionRefresh(String id, String usuarioId, String tokenHash, Instant emitidoEn, Instant expiraEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tokenHash = tokenHash;
    this.emitidoEn = emitidoEn;
    this.expiraEn = expiraEn;
    this.revocado = false;
  }

  public String getId() { return id; }
  public String getUsuarioId() { return usuarioId; }
  public Instant getExpiraEn() { return expiraEn; }
  public boolean isRevocado() { return revocado; }
  public void revocar() { this.revocado = true; }
}

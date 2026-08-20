package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaSesionRefresh;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoSesionesRefresh extends JpaRepository<FilaSesionRefresh, String> {

  Optional<FilaSesionRefresh> findByTokenHash(String tokenHash);

  /** Las sesiones vencidas no sirven ni para auditar: se barren solas. */
  @Modifying
  @Query("delete from FilaSesionRefresh s where s.expiraEn < :corte")
  int borrarVencidas(@Param("corte") Instant corte);

  @Modifying
  @Query("update FilaSesionRefresh s set s.revocado = true where s.usuarioId = :usuarioId")
  int revocarTodasDe(@Param("usuarioId") String usuarioId);
}

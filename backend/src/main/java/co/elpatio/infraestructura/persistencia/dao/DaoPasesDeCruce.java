package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPaseDeCruce;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoPasesDeCruce extends JpaRepository<FilaPaseDeCruce, String> {

  /**
   * Los pases vencidos se olvidan.
   *
   * Pasado el vencimiento del propio token la firma ya no valida, asi que
   * recordar que se uso no protege de nada. Se barre en cada canje, igual que
   * las sesiones de refresco: los cruces son pocos y basta para que la tabla no
   * crezca sin control.
   */
  @Modifying
  @Query("delete from FilaPaseDeCruce p where p.expiraEn < :corte")
  int borrarVencidos(@Param("corte") Instant corte);
}

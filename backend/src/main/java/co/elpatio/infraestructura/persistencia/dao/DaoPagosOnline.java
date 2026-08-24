package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPagoOnline;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoPagosOnline extends JpaRepository<FilaPagoOnline, String> {

  Optional<FilaPagoOnline> findByReferencia(String referencia);

  List<FilaPagoOnline> findByEstadoAndExpiraEnBefore(String estado, Instant instante);

  /**
   * Los del periodo, para el reporte de anticipos.
   *
   * El rango se filtra en la base y no despues en memoria: traer la tabla
   * entera para quedarse con un dia funciona hasta que la tabla crece, y
   * entonces deja de funcionar en produccion y no en desarrollo.
   */
  List<FilaPagoOnline> findByCreadaEnBetweenOrderByCreadaEnDesc(Instant desde, Instant hasta);
}

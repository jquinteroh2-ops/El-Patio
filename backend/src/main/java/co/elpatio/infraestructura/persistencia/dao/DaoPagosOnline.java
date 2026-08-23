package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPagoOnline;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoPagosOnline extends JpaRepository<FilaPagoOnline, String> {

  Optional<FilaPagoOnline> findByReferencia(String referencia);

  List<FilaPagoOnline> findByEstadoAndExpiraEnBefore(String estado, Instant instante);
}

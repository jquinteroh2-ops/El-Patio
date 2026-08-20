package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPago;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoPagos extends JpaRepository<FilaPago, String> {

  List<FilaPago> findByFechaHoraBetweenOrderByFechaHoraDesc(Instant desde, Instant hasta);
}

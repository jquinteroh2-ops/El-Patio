package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaReserva;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoReservas extends JpaRepository<FilaReserva, String> {

  List<FilaReserva> findAllByOrderByFechaHoraAsc();
}

package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaZonaDomicilio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoZonasDomicilio extends JpaRepository<FilaZonaDomicilio, String> {

  List<FilaZonaDomicilio> findAllByOrderByOrdenAsc();
}

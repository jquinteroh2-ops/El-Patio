package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaFranjaHorario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoFranjasHorario extends JpaRepository<FilaFranjaHorario, String> {

  List<FilaFranjaHorario> findAllByOrderByOrdenAsc();
}

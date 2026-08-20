package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaItemCarta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoItemsCarta extends JpaRepository<FilaItemCarta, String> {

  List<FilaItemCarta> findAllByOrderByNombreAsc();
}

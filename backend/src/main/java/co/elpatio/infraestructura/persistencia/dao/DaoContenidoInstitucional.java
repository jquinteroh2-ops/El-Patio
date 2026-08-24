package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaContenidoInstitucional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoContenidoInstitucional
    extends JpaRepository<FilaContenidoInstitucional, String> {

  List<FilaContenidoInstitucional> findAllByOrderByOrdenAsc();

  /** Solo lo que se pinta en el sitio publico. */
  List<FilaContenidoInstitucional> findByVisibleTrueOrderByOrdenAsc();
}

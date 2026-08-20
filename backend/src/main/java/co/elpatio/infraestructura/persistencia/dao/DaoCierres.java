package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaCierreCaja;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoCierres extends JpaRepository<FilaCierreCaja, String> {

  List<FilaCierreCaja> findAllByOrderByFechaHoraDesc();
}

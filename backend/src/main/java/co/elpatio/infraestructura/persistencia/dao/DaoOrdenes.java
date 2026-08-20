package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaOrden;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoOrdenes extends JpaRepository<FilaOrden, String> {

  /**
   * Comandas todavia vivas: es lo que miran cocina, el mapa de mesas y las
   * alertas. Se excluyen las cerradas para que la pantalla de cocina no crezca
   * con el historico del mes.
   */
  @Query("select o from FilaOrden o where o.estado not in ('pagada', 'anulada')")
  List<FilaOrden> activas();

  List<FilaOrden> findByAbiertaEnGreaterThanEqualOrderByAbiertaEnAsc(Instant desde);

  @Query("select o from FilaOrden o where o.id in :ids")
  List<FilaOrden> porIds(@Param("ids") List<String> ids);
}

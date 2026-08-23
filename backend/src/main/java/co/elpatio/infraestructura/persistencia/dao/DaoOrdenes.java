package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaOrden;
import java.time.Instant;
import java.time.LocalDate;
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

  /**
   * El numero mas alto que ya ocupa una comanda ese dia, o 0 si no hay ninguna.
   *
   * Sirve para que el contador de consecutivos nunca entregue un numero que la
   * base ya tiene: si los dos se desalinean —un respaldo restaurado, filas
   * borradas a mano, datos de demostracion retirados— la llave
   * (dia_operativo, numero) rechaza el insert y el restaurante se queda sin
   * poder abrir comandas.
   */
  @Query("select coalesce(max(o.numero), 0) from FilaOrden o where o.diaOperativo = :dia")
  int maximoNumeroDe(@Param("dia") LocalDate dia);
}

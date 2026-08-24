package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaEnvioErp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoEnviosErp extends JpaRepository<FilaEnvioErp, String> {

  Optional<FilaEnvioErp> findByPagoId(String pagoId);

  /**
   * Lo que el worker tiene que sacar en esta pasada.
   *
   * Lleva limite porque una caida larga del ERP deja cientos de pendientes, y
   * despertar con todos en memoria y mandarlos de golpe convierte el primer
   * minuto de recuperacion en una avalancha contra un servidor que acaba de
   * levantarse. De a poco llega igual y no lo vuelve a tumbar.
   */
  @Query(
      """
      select e from FilaEnvioErp e
      where e.estado = 'PENDIENTE_ENVIO_ERP'
        and e.proximoIntento <= :ahora
      order by e.proximoIntento asc
      """)
  List<FilaEnvioErp> pendientesListos(@Param("ahora") Instant ahora, Limit limite);

  /** Los envios de un rango, para la pantalla de conciliacion. */
  @Query(
      """
      select e from FilaEnvioErp e
      where e.creadoEn >= :desde and e.creadoEn < :hasta
      order by e.creadoEn desc
      """)
  List<FilaEnvioErp> entre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

  long countByEstado(String estado);
}

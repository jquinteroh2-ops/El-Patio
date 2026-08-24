package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaConsecutivoPqr;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoConsecutivosPqr extends JpaRepository<FilaConsecutivoPqr, Integer> {

  /**
   * Toma el bloqueo de escritura sobre el contador del año.
   *
   * Es lo que impide que dos personas radicando en el mismo segundo reciban el
   * mismo numero. El bloqueo se libera al cerrar la transaccion, que es la
   * misma que inserta la solicitud: si esa transaccion se revierte, el numero
   * vuelve a quedar libre y no se abre un hueco.
   *
   * Devuelve null el primer dia del año, cuando la fila todavia no existe. Ese
   * caso lo resuelve quien llama insertandola.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from FilaConsecutivoPqr c where c.ano = :ano")
  FilaConsecutivoPqr bloquear(@Param("ano") int ano);
}

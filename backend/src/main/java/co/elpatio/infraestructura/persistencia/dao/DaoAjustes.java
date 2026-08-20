package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaAjustes;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoAjustes extends JpaRepository<FilaAjustes, Integer> {

  /**
   * Toma el bloqueo de escritura sobre la unica fila de ajustes.
   *
   * Es lo que impide que dos meseros abriendo mesa en el mismo segundo reciban
   * el mismo numero de comanda. El bloqueo se libera al cerrar la transaccion,
   * que es la misma que inserta la comanda: si esa transaccion se revierte, el
   * numero vuelve a quedar libre y no se abre un hueco en el consecutivo.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from FilaAjustes a where a.id = :id")
  FilaAjustes bloquearParaConsecutivo(@Param("id") int id);
}

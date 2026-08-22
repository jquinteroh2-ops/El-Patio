package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPublicacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoPublicaciones extends JpaRepository<FilaPublicacion, String> {

  /**
   * Por orden del dueno primero, y las mas nuevas de segundas.
   *
   * El segundo criterio importa: sin el, todas las que se crean con orden 0
   * —que es el valor por defecto y el que va a quedar casi siempre— salen en
   * un orden que decide la base y que cambia entre consultas.
   */
  List<FilaPublicacion> findAllByOrderByOrdenAscCreadaEnDesc();
}

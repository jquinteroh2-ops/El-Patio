package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaUsuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoUsuarios extends JpaRepository<FilaUsuario, String> {

  /** El mesero escribe su usuario en una tablet: no se le puede exigir la mayuscula exacta. */
  @Query("select u from FilaUsuario u where lower(u.usuario) = lower(:usuario)")
  Optional<FilaUsuario> porNombreDeUsuario(@Param("usuario") String usuario);
}

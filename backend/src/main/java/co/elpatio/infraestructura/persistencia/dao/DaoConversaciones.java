package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaConversacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoConversaciones extends JpaRepository<FilaConversacion, String> {

  List<FilaConversacion> findByCanalAndIdentificadorExternoOrderByIniciadaEnDesc(
      String canal, String identificadorExterno);
}

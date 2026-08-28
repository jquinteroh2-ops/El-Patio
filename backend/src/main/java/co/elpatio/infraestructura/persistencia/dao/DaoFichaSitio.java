package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaFichaSitio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoFichaSitio extends JpaRepository<FilaFichaSitio, Integer> {}

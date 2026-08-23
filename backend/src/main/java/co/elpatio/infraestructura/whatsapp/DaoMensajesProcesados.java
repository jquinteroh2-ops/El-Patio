package co.elpatio.infraestructura.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DaoMensajesProcesados extends JpaRepository<FilaMensajeProcesado, String> {}

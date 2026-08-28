package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.sitio.FichaSitio;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La ficha del sitio: horario de atencion y datos de contacto.
 *
 * Es lo que antes estaba escrito a mano en el codigo del frontend. Se movio
 * aqui porque el horario es el dato que mas se mueve del sitio -una temporada,
 * un festivo, un dia que se cierra por mantenimiento- y no puede costar un
 * despliegue cada vez.
 */
@Service
public class ServicioSitio {

  private final Repositorios.DeFichaSitio fichas;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioSitio(
      Repositorios.DeFichaSitio fichas, Reloj reloj, PublicadorEventos eventos) {
    this.fichas = fichas;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  @Transactional(readOnly = true)
  public FichaSitio obtener() {
    return fichas.leer();
  }

  @Transactional
  public FichaSitio guardar(Dtos.FichaSitioDto cambios) {
    FichaSitio ficha = fichas.leer();
    ficha.editar(
        cambios.direccion(),
        cambios.ciudad(),
        cambios.telefono(),
        cambios.whatsapp(),
        cambios.instagram(),
        cambios.horario(),
        reloj.ahora());

    FichaSitio guardada = fichas.guardar(ficha);
    // El sitio publico lo lee sin recargar: sin este aviso, quien corrige el
    // horario no lo ve cambiado y cree que no se guardo.
    eventos.publicar(List.of("sitio"));
    return guardada;
  }
}

package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.ajustes.Ajustes;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los parametros del establecimiento. */
@Service
public class ServicioAjustes {

  private final Repositorios.DeAjustes ajustes;
  private final PublicadorEventos eventos;

  public ServicioAjustes(Repositorios.DeAjustes ajustes, PublicadorEventos eventos) {
    this.ajustes = ajustes;
    this.eventos = eventos;
  }

  @Transactional(readOnly = true)
  public Dtos.AjustesDto obtener() {
    return aDto(ajustes.leer());
  }

  /**
   * Actualiza solo lo que venga informado.
   *
   * El consecutivo y su fecha no se pueden tocar desde aqui a proposito: son el
   * respaldo de que la numeracion de comprobantes no tiene saltos, y dejar que
   * una pantalla los reescriba destruiria justo esa garantia.
   */
  @Transactional
  public Dtos.AjustesDto actualizar(Dtos.CambiosAjustes cambios) {
    Ajustes actuales = ajustes.leer();

    if (cambios.porcentajeInc() != null) {
      int inc = cambios.porcentajeInc();
      if (inc < 0 || inc > 100) throw new ReglaDeNegocioError("El INC debe estar entre 0 y 100");
      actuales.setPorcentajeInc(inc);
    }
    if (cambios.domiciliosPausados() != null) {
      actuales.setDomiciliosPausados(cambios.domiciliosPausados());
    }
    if (cambios.domiciliosDesde() != null) actuales.setDomiciliosDesde(cambios.domiciliosDesde());
    if (cambios.domiciliosHasta() != null) actuales.setDomiciliosHasta(cambios.domiciliosHasta());

    if (actuales.getDomiciliosDesde().isAfter(actuales.getDomiciliosHasta())) {
      throw new ReglaDeNegocioError("La hora de apertura de domicilios va antes que la de cierre");
    }

    Dtos.AjustesDto guardados = aDto(ajustes.guardar(actuales));
    // 'pedidos' tambien: pausar el canal tiene que apagar el boton del sitio
    // publico en el momento, no cuando alguien recargue.
    eventos.publicar(List.of("ajustes", "pedidos"));
    return guardados;
  }

  private Dtos.AjustesDto aDto(Ajustes valor) {
    return new Dtos.AjustesDto(
        valor.getPorcentajeInc(),
        valor.getConsecutivoOrden(),
        valor.getFechaConsecutivo(),
        valor.isDomiciliosPausados(),
        valor.getDomiciliosDesde(),
        valor.getDomiciliosHasta());
  }
}

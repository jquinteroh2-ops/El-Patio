package co.elpatio.infraestructura.web;

import co.elpatio.aplicacion.ServicioSitio;
import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.sitio.FichaSitio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A que horas abrimos y como nos encuentran.
 *
 * La lectura es publica porque es contenido de la pagina de inicio: el horario
 * y el telefono son justamente lo que busca quien todavia no es cliente.
 *
 * Escribir es trabajo de mostrador y no solo de administracion. Quien contesta
 * «¿hasta que hora abren hoy?» es recepcion, y es quien primero se entera de
 * que el horario publicado quedo viejo; obligarla a pedirle el cambio al
 * administrador es lo que hace que el sitio muestre un horario equivocado
 * durante una semana.
 */
@RestController
@RequestMapping("/api/sitio")
public class ControladorSitio {

  private final ServicioSitio servicio;

  public ControladorSitio(ServicioSitio servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  public FichaSitio obtener() {
    return servicio.obtener();
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('RECEPCION', 'CAJERO', 'ADMINISTRADOR')")
  public FichaSitio guardar(@RequestBody Dtos.FichaSitioDto ficha) {
    return servicio.guardar(ficha);
  }
}

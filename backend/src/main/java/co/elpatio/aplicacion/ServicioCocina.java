package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.Destino;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.Mesa;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que ven cocina y barra, y lo que pueden cambiar desde ahi. */
@Service
public class ServicioCocina {

  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeUsuarios usuarios;
  private final Reloj reloj;
  private final PublicadorEventos eventos;

  public ServicioCocina(
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeMesas mesas,
      Repositorios.DeUsuarios usuarios,
      Reloj reloj,
      PublicadorEventos eventos) {
    this.ordenes = ordenes;
    this.mesas = mesas;
    this.usuarios = usuarios;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  /**
   * Un bloque por turno de envio, para que las entradas y los fuertes de una
   * misma mesa no se mezclen en la pantalla.
   *
   * El mas antiguo va primero: lo que lleva mas tiempo esperando manda, y ese
   * orden es el que evita que un plato se quede olvidado al final de la lista.
   */
  @Transactional(readOnly = true)
  public List<Dtos.TurnoEnCocina> comandasActivas(Destino destino) {
    Map<String, Mesa> porMesa =
        mesas.listar().stream().collect(Collectors.toMap(Mesa::getId, Function.identity()));
    Map<String, String> nombres =
        usuarios.listar().stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

    List<Dtos.TurnoEnCocina> bloques = new ArrayList<>();

    for (Orden orden : ordenes.activas()) {
      // Un domicilio o un para llevar no tiene mesa. Cocina los ve igual que
      // los del salon, solo cambia la etiqueta: no hay una pantalla aparte ni
      // una regla distinta, que era justo lo que habia que evitar.
      Mesa mesa = orden.getMesaId() == null ? null : porMesa.get(orden.getMesaId());
      if (mesa == null && !orden.esExterno()) continue;

      for (Orden.GrupoTurno grupo : orden.agruparPorTurno(orden.itemsEnviadosDe(destino))) {
        // Un turno servido por completo ya no ocupa espacio en la pantalla.
        if (grupo.items().stream().allMatch(i -> i.getEstado() == EstadoItem.SERVIDO)) continue;

        bloques.add(
            new Dtos.TurnoEnCocina(
                orden.getId(),
                orden.getNumero(),
                mesa == null ? null : mesa.getId(),
                mesa == null ? orden.etiquetaCanal() : mesa.etiqueta(),
                mesa == null ? null : mesa.getZona(),
                nombres.getOrDefault(orden.getMeseroId(), ""),
                grupo.turno(),
                grupo.items().get(0).getEnviadoEn() != null
                    ? grupo.items().get(0).getEnviadoEn()
                    : orden.getAbiertaEn(),
                estadoDeTurno(grupo.items()),
                grupo.items(),
                orden.getNotas()));
      }
    }

    bloques.sort(Comparator.comparing(Dtos.TurnoEnCocina::enviadoEn));
    return bloques;
  }

  /**
   * Estado agregado de un turno: se toma el menos avanzado de sus productos,
   * porque un turno no esta listo hasta que salga el ultimo plato.
   */
  private String estadoDeTurno(List<ItemOrden> items) {
    if (items.stream().anyMatch(i -> i.getEstado() == EstadoItem.PENDIENTE)) return "pendiente";
    if (items.stream().anyMatch(i -> i.getEstado() == EstadoItem.EN_PREPARACION)) return "en_preparacion";
    return "listo";
  }

  @Transactional
  public void cambiarEstadoItem(String ordenId, String itemId, EstadoItem estado) {
    Orden orden = exigirOrden(ordenId);
    orden.cambiarEstadoItem(itemId, estado, reloj.ahora());
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "cocina", "mesas"));
  }

  @Transactional
  public void cambiarEstadoTurno(String ordenId, int turno, Destino destino, EstadoItem estado) {
    Orden orden = exigirOrden(ordenId);
    orden.cambiarEstadoTurno(turno, destino, estado, reloj.ahora());
    ordenes.guardar(orden);
    eventos.publicar(List.of("ordenes", "cocina", "mesas"));
  }

  private Orden exigirOrden(String ordenId) {
    return ordenes.porId(ordenId).orElseThrow(() -> new NoEncontradoError("La comanda ya no existe"));
  }
}

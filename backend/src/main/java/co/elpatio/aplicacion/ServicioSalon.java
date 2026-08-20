package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.cobro.CalculadoraCuenta;
import co.elpatio.dominio.comanda.EstadoItem;
import co.elpatio.dominio.comanda.ItemOrden;
import co.elpatio.dominio.comanda.Orden;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.dominio.salon.Mesa;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El mapa de mesas y su administracion. */
@Service
public class ServicioSalon {

  private final Repositorios.DeMesas mesas;
  private final Repositorios.DeOrdenes ordenes;
  private final Repositorios.DeUsuarios usuarios;
  private final Repositorios.DeAjustes ajustes;
  private final GeneradorIds ids;
  private final PublicadorEventos eventos;

  public ServicioSalon(
      Repositorios.DeMesas mesas,
      Repositorios.DeOrdenes ordenes,
      Repositorios.DeUsuarios usuarios,
      Repositorios.DeAjustes ajustes,
      GeneradorIds ids,
      PublicadorEventos eventos) {
    this.mesas = mesas;
    this.ordenes = ordenes;
    this.usuarios = usuarios;
    this.ajustes = ajustes;
    this.ids = ids;
    this.eventos = eventos;
  }

  /**
   * El mapa completo del salon.
   *
   * Se resuelve en tres consultas y no en una por mesa: son dieciocho mesas y
   * la comandera lo relee en cada aviso de cambio, asi que el costo por mesa se
   * multiplica rapido.
   */
  @Transactional(readOnly = true)
  public List<Dtos.MesaEnMapa> listarMesas() {
    int porcentajeInc = ajustes.leer().getPorcentajeInc();
    Map<String, Orden> activas =
        ordenes.activas().stream().collect(Collectors.toMap(Orden::getId, Function.identity()));
    Map<String, String> nombres =
        usuarios.listar().stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

    return mesas.listar().stream()
        .map(
            mesa -> {
              Orden orden = mesa.getOrdenActivaId() == null ? null : activas.get(mesa.getOrdenActivaId());
              List<ItemOrden> vigentes = orden == null ? List.of() : orden.itemsVigentes();

              return new Dtos.MesaEnMapa(
                  mesa.getId(),
                  mesa.getNumero(),
                  mesa.getNombre(),
                  mesa.getZona(),
                  mesa.getCapacidad(),
                  mesa.getEstado(),
                  mesa.getMeseroId(),
                  mesa.getOrdenActivaId(),
                  orden == null ? null : orden.getNumero(),
                  orden == null ? null : orden.getAbiertaEn(),
                  orden == null ? 0 : CalculadoraCuenta.calcular(orden, porcentajeInc).total(),
                  orden == null ? 0 : orden.getComensales(),
                  (int) vigentes.stream()
                      .filter(i -> i.getEstado() == EstadoItem.PENDIENTE
                          || i.getEstado() == EstadoItem.EN_PREPARACION)
                      .count(),
                  (int) vigentes.stream().filter(i -> i.getEstado() == EstadoItem.LISTO).count(),
                  mesa.getMeseroId() == null ? null : nombres.get(mesa.getMeseroId()));
            })
        .toList();
  }

  @Transactional
  public Dtos.MesaDto guardarMesa(Dtos.MesaDto entrada) {
    Mesa mesa =
        entrada.id() == null || entrada.id().isBlank()
            ? new Mesa()
            : mesas.porId(entrada.id()).orElseGet(Mesa::new);

    if (mesa.getId() == null) mesa.setId(ids.nuevo("m"));
    mesa.setNumero(entrada.numero());
    mesa.setNombre(entrada.nombre());
    mesa.setZona(entrada.zona());
    mesa.setCapacidad(entrada.capacidad());
    // El estado y la ocupacion los maneja el flujo de sala, no la pantalla de
    // configuracion: guardar una mesa no puede liberar una cuenta abierta.
    if (mesa.getEstado() == null) mesa.setEstado(entrada.estado());

    Dtos.MesaDto guardada = Dtos.MesaDto.de(mesas.guardar(mesa));
    eventos.publicar(List.of("mesas"));
    return guardada;
  }

  @Transactional
  public void eliminarMesa(String mesaId) {
    Mesa mesa = mesas.porId(mesaId).orElseThrow(() -> new NoEncontradoError("La mesa no existe"));
    if (mesa.getOrdenActivaId() != null) {
      throw new ReglaDeNegocioError("No se puede eliminar una mesa con cuenta abierta");
    }
    mesas.eliminar(mesaId);
    eventos.publicar(List.of("mesas"));
  }
}

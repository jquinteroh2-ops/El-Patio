package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.carta.CategoriaCarta;
import co.elpatio.dominio.carta.ItemCarta;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Repositorios;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La carta: categorias, productos y disponibilidad. */
@Service
public class ServicioCarta {

  private final Repositorios.DeCarta carta;
  private final GeneradorIds ids;
  private final PublicadorEventos eventos;
  private final AlmacenDeImagenes imagenes;

  public ServicioCarta(
      Repositorios.DeCarta carta,
      GeneradorIds ids,
      PublicadorEventos eventos,
      AlmacenDeImagenes imagenes) {
    this.carta = carta;
    this.ids = ids;
    this.eventos = eventos;
    this.imagenes = imagenes;
  }

  @Transactional(readOnly = true)
  public List<CategoriaCarta> listarCategorias() {
    return carta.listarCategorias();
  }

  @Transactional(readOnly = true)
  public List<ItemCarta> listarCarta() {
    return carta.listarItems();
  }

  /**
   * Carta agrupada por categoria.
   *
   * `soloDisponibles` lo usa el sitio publico: al cliente no se le muestra lo
   * que ya se agoto, pero el mesero si tiene que verlo para poder explicarlo.
   * Las categorias que quedan sin productos no se pintan.
   */
  @Transactional(readOnly = true)
  public List<Dtos.CategoriaConItems> cartaAgrupada(boolean soloDisponibles) {
    List<ItemCarta> items = carta.listarItems();
    return carta.listarCategorias().stream()
        .map(
            categoria ->
                Dtos.CategoriaConItems.de(
                    categoria,
                    items.stream()
                        .filter(i -> i.getCategoriaId().equals(categoria.getId()))
                        .filter(i -> !soloDisponibles || i.isDisponible())
                        .toList()))
        .filter(c -> !c.items().isEmpty())
        .toList();
  }

  @Transactional
  public void cambiarDisponibilidad(String itemId, boolean disponible) {
    ItemCarta item =
        carta.porId(itemId).orElseThrow(() -> new NoEncontradoError("El producto no existe"));
    item.setDisponible(disponible);
    carta.guardarItem(item);
    eventos.publicar(List.of("carta"));
  }

  @Transactional
  public ItemCarta guardarItemCarta(ItemCarta item) {
    if (item.getId() == null || item.getId().isBlank()) {
      item.setId(ids.nuevo("p"));
    } else {
      // Si le cambiaron la foto, la anterior deja de servirle a nadie. Se
      // borra aqui y no en un aseo posterior, porque este es el unico momento
      // en que se sabe con certeza que quedo huerfana.
      carta.porId(item.getId())
          .map(ItemCarta::getImagen)
          .filter(previa -> !previa.equals(item.getImagen()))
          .ifPresent(imagenes::borrar);
    }
    ItemCarta guardado = carta.guardarItem(item);
    eventos.publicar(List.of("carta"));
    return guardado;
  }

  @Transactional
  public void eliminarItemCarta(String itemId) {
    carta.porId(itemId).map(ItemCarta::getImagen).ifPresent(imagenes::borrar);
    carta.eliminarItem(itemId);
    eventos.publicar(List.of("carta"));
  }

  /** Sube la foto de un plato y devuelve el nombre con que quedo guardada. */
  public String guardarImagen(String nombreOriginal, byte[] contenido) {
    return imagenes.guardar(nombreOriginal, contenido);
  }

  public byte[] leerImagen(String nombre) {
    return imagenes.leer(nombre);
  }

  public String tipoDeContenido(String nombre) {
    return imagenes.tipoDeContenido(nombre);
  }

  @Transactional
  public CategoriaCarta guardarCategoria(CategoriaCarta categoria) {
    if (categoria.getId() == null || categoria.getId().isBlank()) categoria.setId(ids.nuevo("c"));
    CategoriaCarta guardada = carta.guardarCategoria(categoria);
    eventos.publicar(List.of("carta"));
    return guardada;
  }
}

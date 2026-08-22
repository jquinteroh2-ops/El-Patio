package co.elpatio.aplicacion;

import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.publicacion.Publicacion;
import co.elpatio.dominio.publicacion.TipoPublicacion;
import co.elpatio.dominio.puertos.AlmacenDeImagenes;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promociones, eventos y fotos del local.
 *
 * Todo lo escribe el dueno desde /admin. Lo que lee el sitio publico pasa por
 * `visibles`, que es la unica puerta por la que sale algo hacia la calle.
 */
@Service
public class ServicioPublicaciones {

  private final Repositorios.DePublicaciones publicaciones;
  private final AlmacenDeImagenes imagenes;
  private final GeneradorIds ids;
  private final Reloj reloj;

  public ServicioPublicaciones(
      Repositorios.DePublicaciones publicaciones,
      AlmacenDeImagenes imagenes,
      GeneradorIds ids,
      Reloj reloj) {
    this.publicaciones = publicaciones;
    this.imagenes = imagenes;
    this.ids = ids;
    this.reloj = reloj;
  }

  /** Todo, publicado o no. Es la pantalla del dueno. */
  @Transactional(readOnly = true)
  public List<Publicacion> listarTodas() {
    return publicaciones.listar();
  }

  /**
   * Lo que le corresponde ver al cliente hoy.
   *
   * El filtro se hace aqui y no en la pantalla publica: si viviera en el
   * navegador, una promocion vencida viajaria igual hasta el cliente y bastaria
   * con mirar la respuesta del servidor para verla. Lo que no esta vigente no
   * sale del servidor.
   */
  @Transactional(readOnly = true)
  public List<Publicacion> visibles(TipoPublicacion tipo) {
    var hoy = reloj.hoy();
    return publicaciones.listar().stream()
        .filter(p -> p.visibleEn(hoy))
        .filter(p -> tipo == null || p.getTipo() == tipo)
        .toList();
  }

  @Transactional
  public Publicacion guardar(Publicacion publicacion) {
    if (publicacion.getTitulo() == null || publicacion.getTitulo().isBlank()) {
      throw new ReglaDeNegocioError("La publicacion necesita un titulo");
    }
    if (publicacion.getTipo() == null) {
      throw new ReglaDeNegocioError("Falta decir si es promocion, evento o foto del local");
    }
    if (publicacion.getDesde() != null
        && publicacion.getHasta() != null
        && publicacion.getDesde().isAfter(publicacion.getHasta())) {
      throw new ReglaDeNegocioError("La promocion terminaria antes de empezar");
    }

    if (publicacion.getId() == null || publicacion.getId().isBlank()) {
      publicacion.setId(ids.nuevo("pub"));
      publicacion.setCreadaEn(reloj.ahora());
      return publicaciones.guardar(publicacion);
    }

    Publicacion previa =
        publicaciones
            .porId(publicacion.getId())
            .orElseThrow(() -> new NoEncontradoError("Esa publicacion ya no existe"));

    // La fecha de creacion es de la publicacion, no de la ultima vez que se
    // corrigio una coma: se conserva la original.
    publicacion.setCreadaEn(previa.getCreadaEn());

    // Si le cambiaron la foto, la anterior deja de servirle a nadie. Se borra
    // aqui y no en un aseo posterior, porque el unico momento en que se sabe
    // con certeza que quedo huerfana es este.
    String imagenPrevia = previa.getImagen();
    if (imagenPrevia != null && !imagenPrevia.equals(publicacion.getImagen())) {
      imagenes.borrar(imagenPrevia);
    }
    return publicaciones.guardar(publicacion);
  }

  @Transactional
  public void eliminar(String id) {
    publicaciones
        .porId(id)
        .ifPresent(
            p -> {
              if (p.getImagen() != null) {
                imagenes.borrar(p.getImagen());
              }
              publicaciones.eliminar(id);
            });
  }

  /** Sube una foto y devuelve el nombre con que quedo guardada. */
  public String guardarImagen(String nombreOriginal, byte[] contenido) {
    return imagenes.guardar(nombreOriginal, contenido);
  }

  public byte[] leerImagen(String nombre) {
    return imagenes.leer(nombre);
  }

  public String tipoDeContenido(String nombre) {
    return imagenes.tipoDeContenido(nombre);
  }
}

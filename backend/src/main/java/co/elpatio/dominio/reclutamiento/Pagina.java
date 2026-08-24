package co.elpatio.dominio.reclutamiento;

import java.util.List;

/**
 * Un trozo de una lista larga.
 *
 * Existe para que el dominio pueda hablar de paginacion sin conocer a Spring.
 * Es un record de cuatro campos y no vale la pena mas: convertir de `Page` a
 * esto ocurre en el adaptador, que es justo donde debe estar la traduccion.
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long total) {

  public int totalPaginas() {
    return tamano == 0 ? 0 : (int) Math.ceil((double) total / tamano);
  }

  public static <T> Pagina<T> vacia(int tamano) {
    return new Pagina<>(List.of(), 0, tamano, 0);
  }
}

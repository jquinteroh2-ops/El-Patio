package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaPostulacion;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoPostulaciones extends JpaRepository<FilaPostulacion, String> {

  /**
   * La bandeja, con sus filtros.
   *
   * Va paginada y no completa: la lista de aspirantes solo crece, y una
   * pantalla que traiga todo funciona el primer mes y deja de funcionar al año.
   *
   * <p><b>Los filtros vacíos van como cadena vacía y NUNCA como null.</b> Con
   * un null, PostgreSQL no tiene de dónde deducir el tipo del parámetro, lo
   * toma por `bytea` y revienta con «function lower(bytea) does not exist»: la
   * pantalla entera devuelve 500 aunque nadie esté buscando nada. Con la cadena
   * vacía el tipo queda claro y filtra igual, porque ningún valor real es una
   * cadena vacía.
   *
   * <p>La busqueda mira nombre y documento, que es como de verdad se busca a
   * alguien: «el muchacho que se llama Andres» o con la cedula en la mano.
   */
  @Query(
      """
      select p from FilaPostulacion p
      where (:estado = '' or p.estado = :estado)
        and (:cargo = '' or p.cargoInteres = :cargo)
        and (:desde is null or p.fechaPostulacion >= :desde)
        and (:hasta is null or p.fechaPostulacion < :hasta)
        and (:busqueda = ''
             or lower(p.nombreCompleto) like lower(concat('%', :busqueda, '%'))
             or p.numeroDocumento like concat('%', :busqueda, '%'))
      order by p.fechaPostulacion desc
      """)
  Page<FilaPostulacion> buscar(
      @Param("estado") String estado,
      @Param("cargo") String cargo,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("busqueda") String busqueda,
      Pageable pagina);

  /** Cuantas hay sin revisar. Es el contador que va en el menu. */
  long countByEstado(String estado);

  /**
   * Envios recientes del mismo documento.
   *
   * Sirve para no llenar la bandeja con la misma hoja de vida mandada cinco
   * veces —pasa sin mala intencion: la gente pulsa el boton otra vez cuando la
   * pagina tarda—.
   */
  List<FilaPostulacion> findByNumeroDocumentoAndFechaPostulacionAfter(
      String numeroDocumento, Instant desde);

  /** Las del periodo, para el reporte. */
  List<FilaPostulacion> findByFechaPostulacionBetweenOrderByFechaPostulacionDesc(
      Instant desde, Instant hasta);
}

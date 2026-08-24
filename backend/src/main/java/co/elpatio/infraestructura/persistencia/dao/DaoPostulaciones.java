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
   * <p><b>Todos los filtros de texto son patrones LIKE, y «sin filtro» es el
   * comodín `%`.</b> Es lo que permite a PostgreSQL deducir el tipo de cada
   * parámetro: suelto —contra null o contra una cadena vacía— no lo puede
   * inferir y la consulta muere antes de ejecutarse; a la derecha de un LIKE
   * sobre una columna de texto, el tipo lo da la columna. Ver la misma nota en
   * `DaoSolicitudesPqr`.
   *
   * <p>La busqueda mira nombre y documento, que es como de verdad se busca a
   * alguien: «el muchacho que se llama Andres» o con la cedula en la mano.
   */
  @Query(
      """
      select p from FilaPostulacion p
      where p.estado like :estado
        and p.cargoInteres like :cargo
        and p.fechaPostulacion >= :desde
        and p.fechaPostulacion < :hasta
        and (lower(p.nombreCompleto) like :busqueda escape '!'
             or lower(p.numeroDocumento) like :busqueda escape '!')
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

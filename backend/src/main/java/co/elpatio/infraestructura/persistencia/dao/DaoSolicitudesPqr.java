package co.elpatio.infraestructura.persistencia.dao;

import co.elpatio.infraestructura.persistencia.filas.FilaSolicitudPqr;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DaoSolicitudesPqr extends JpaRepository<FilaSolicitudPqr, String> {

  /**
   * La consulta publica: radicado Y correo, las dos cosas.
   *
   * Exigir el correo es lo que impide enumerar radicados. Con solo el numero,
   * cualquiera podria recorrer PQR-2026-00001, 00002, 00003 y leer las quejas
   * de todo el mundo con nombre y telefono incluidos.
   *
   * La comparacion del correo va en minusculas porque asi se guarda, y quien
   * consulta lo va a escribir como le salga.
   */
  @Query(
      """
      select s from FilaSolicitudPqr s
      where s.radicado = :radicado and lower(s.email) = lower(:email)
      """)
  Optional<FilaSolicitudPqr> porRadicadoYCorreo(
      @Param("radicado") String radicado, @Param("email") String email);

  /**
   * La bandeja del administrador.
   *
   * <p><b>Todos los filtros de texto son patrones LIKE, y «sin filtro» es el
   * comodín `%`.</b> No es un capricho de estilo: es lo único que hace que
   * PostgreSQL pueda deducir de qué tipo es cada parámetro.
   *
   * <p>Un parámetro suelto —comparado con null o con una cadena vacía— no le
   * dice nada al motor sobre su tipo, y la consulta muere antes de ejecutarse
   * («function lower(bytea) does not exist», «could not determine data type»).
   * En cambio, a la derecha de un LIKE cuyo lado izquierdo es una columna de
   * texto, el tipo queda determinado por la columna y no hay nada que adivinar.
   *
   * <p>Por eso el patrón de búsqueda se arma en Java, ya en minúsculas y con
   * sus comodines, en vez de construirlo aquí con `concat` y `lower`.
   *
   * El orden por defecto es por fecha limite ascendente: lo que primero vence,
   * primero. Ordenar por fecha de radicacion —lo natural— dejaria una queja a
   * punto de vencer enterrada bajo diez felicitaciones recientes.
   *
   * Los nulos van al final: las felicitaciones no tienen termino y no compiten
   * por atencion con lo que si vence.
   */
  @Query(
      """
      select s from FilaSolicitudPqr s
      where s.tipo like :tipo
        and s.estado like :estado
        and (:desde is null or s.fechaRadicacion >= :desde)
        and (:hasta is null or s.fechaRadicacion < :hasta)
        and (lower(s.nombreCompleto) like :busqueda escape '!'
             or lower(s.radicado) like :busqueda escape '!'
             or lower(s.asunto) like :busqueda escape '!')
      order by
        case when s.estado in ('RADICADA', 'EN_TRAMITE') then 0 else 1 end asc,
        s.fechaLimiteRespuesta asc nulls last,
        s.fechaRadicacion desc
      """)
  Page<FilaSolicitudPqr> buscar(
      @Param("tipo") String tipo,
      @Param("estado") String estado,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("busqueda") String busqueda,
      Pageable pagina);

  /** Cuantas siguen abiertas. Es el contador del menu. */
  @Query("select count(s) from FilaSolicitudPqr s where s.estado in ('RADICADA', 'EN_TRAMITE')")
  long abiertas();

  /** Las que vencen dentro del plazo de aviso y siguen sin responder. */
  @Query(
      """
      select s from FilaSolicitudPqr s
      where s.estado in ('RADICADA', 'EN_TRAMITE')
        and s.fechaLimiteRespuesta is not null
        and s.fechaLimiteRespuesta <= :limite
      order by s.fechaLimiteRespuesta asc
      """)
  List<FilaSolicitudPqr> porVencerHasta(@Param("limite") LocalDate limite);

  /** Las del periodo, para el reporte. */
  List<FilaSolicitudPqr> findByFechaRadicacionBetweenOrderByFechaRadicacionDesc(
      Instant desde, Instant hasta);
}

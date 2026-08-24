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
      where (:tipo is null or s.tipo = :tipo)
        and (:estado is null or s.estado = :estado)
        and (:desde is null or s.fechaRadicacion >= :desde)
        and (:hasta is null or s.fechaRadicacion < :hasta)
        and (:busqueda is null
             or lower(s.nombreCompleto) like lower(concat('%', :busqueda, '%'))
             or lower(s.radicado) like lower(concat('%', :busqueda, '%'))
             or lower(s.asunto) like lower(concat('%', :busqueda, '%')))
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

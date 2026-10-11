package co.elpatio.infraestructura.persistencia;

import co.elpatio.dominio.papelera.RegistroEliminado;
import co.elpatio.dominio.puertos.Repositorios;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * La papelera, en SQL directo y no con JPA, a proposito.
 *
 * La copia de la venta se toma fila por fila con `to_jsonb` y se restaura con
 * `jsonb_populate_record`: PostgreSQL escribe de vuelta cada columna tal como
 * estaba, incluidas las que el dominio no expone. Pasar por los objetos del
 * dominio perderia justo esas, y una venta restaurada a medias es peor que una
 * que no se restauro.
 */
@Repository
public class PapeleraJdbc implements Repositorios.DePapelera {

  private final JdbcTemplate jdbc;

  public PapeleraJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String copiaDeVenta(String pagoId, String ordenId) {
    return jdbc.queryForObject(
        """
        select jsonb_build_object(
          'orden',  (select to_jsonb(o) from ordenes o where o.id = ?),
          'items',  (select coalesce(jsonb_agg(to_jsonb(i)), '[]') from items_orden i where i.orden_id = ?),
          'cargos', (select coalesce(jsonb_agg(to_jsonb(c)), '[]') from cargos_adicionales c where c.orden_id = ?),
          'pago',   (select to_jsonb(p) from pagos p where p.id = ?),
          'envios_erp', (select coalesce(jsonb_agg(to_jsonb(e)), '[]') from erp_outbox e where e.pago_id = ?)
        )::text
        """,
        String.class,
        ordenId, ordenId, ordenId, pagoId, pagoId);
  }

  @Override
  public void borrarVenta(String pagoId, String ordenId) {
    // El orden importa: primero lo que apunta al pago, luego el pago, luego la
    // comanda (que arrastra platos y cargos con ON DELETE CASCADE).
    jdbc.update("delete from erp_outbox where pago_id = ?", pagoId);
    jdbc.update("delete from pagos where id = ?", pagoId);
    jdbc.update("update mesas set orden_activa_id = null where orden_activa_id = ?", ordenId);
    jdbc.update("delete from ordenes where id = ?", ordenId);
  }

  @Override
  public void restaurarVenta(String copia) {
    jdbc.update(
        "insert into ordenes select * from jsonb_populate_record(null::ordenes, (?::jsonb)->'orden')", copia);
    jdbc.update(
        "insert into items_orden select * from jsonb_populate_recordset(null::items_orden, (?::jsonb)->'items')",
        copia);
    jdbc.update(
        "insert into cargos_adicionales"
            + " select * from jsonb_populate_recordset(null::cargos_adicionales, (?::jsonb)->'cargos')",
        copia);
    jdbc.update(
        "insert into pagos select * from jsonb_populate_record(null::pagos, (?::jsonb)->'pago')", copia);
    jdbc.update(
        "insert into erp_outbox select * from jsonb_populate_recordset(null::erp_outbox, (?::jsonb)->'envios_erp')",
        copia);
  }

  @Override
  public Optional<RegistroEliminado> porId(String id) {
    return jdbc.query("select * from registros_eliminados where id = ?", PapeleraJdbc::leer, id).stream()
        .findFirst();
  }

  @Override
  public List<RegistroEliminado> listar() {
    return jdbc.query(
        "select * from registros_eliminados order by eliminado_en desc", PapeleraJdbc::leer);
  }

  @Override
  public RegistroEliminado guardar(RegistroEliminado r) {
    int actualizados =
        jdbc.update(
            "update registros_eliminados set restaurado_por = ?, restaurado_en = ? where id = ?",
            r.getRestauradoPor(),
            r.getRestauradoEn() == null ? null : Timestamp.from(r.getRestauradoEn()),
            r.getId());
    if (actualizados == 0) {
      jdbc.update(
          """
          insert into registros_eliminados
            (id, tipo, referencia, resumen, motivo, datos, eliminado_por, eliminado_en)
          values (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
          """,
          r.getId(), r.getTipo(), r.getReferencia(), r.getResumen(), r.getMotivo(), r.getDatos(),
          r.getEliminadoPor(), Timestamp.from(r.getEliminadoEn()));
    }
    return r;
  }

  private static RegistroEliminado leer(ResultSet rs, int fila) throws SQLException {
    RegistroEliminado r = new RegistroEliminado();
    r.setId(rs.getString("id"));
    r.setTipo(rs.getString("tipo"));
    r.setReferencia(rs.getString("referencia"));
    r.setResumen(rs.getString("resumen"));
    r.setMotivo(rs.getString("motivo"));
    r.setDatos(rs.getString("datos"));
    r.setEliminadoPor(rs.getString("eliminado_por"));
    r.setEliminadoEn(rs.getTimestamp("eliminado_en").toInstant());
    r.setRestauradoPor(rs.getString("restaurado_por"));
    Timestamp restaurado = rs.getTimestamp("restaurado_en");
    r.setRestauradoEn(restaurado == null ? null : restaurado.toInstant());
    return r;
  }
}

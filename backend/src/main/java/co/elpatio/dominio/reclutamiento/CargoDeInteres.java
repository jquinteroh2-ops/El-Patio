package co.elpatio.dominio.reclutamiento;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Para que puesto se postula. Son los cargos que de verdad tiene el local. */
public enum CargoDeInteres {
  MESERO("Mesero"),
  COCINA("Cocina"),
  AUXILIAR_COCINA("Auxiliar de cocina"),
  CAJA("Caja"),
  DOMICILIOS("Domicilios"),
  ASEO("Aseo"),
  ADMINISTRATIVO("Administrativo"),
  OTRO("Otro");

  private final String etiqueta;

  CargoDeInteres(String etiqueta) { this.etiqueta = etiqueta; }

  /** Como se lee en pantalla y en el reporte. */
  public String etiqueta() { return etiqueta; }

  @JsonValue
  public String codigo() { return name().toLowerCase(); }

  @JsonCreator
  public static CargoDeInteres de(String valor) { return valueOf(valor.toUpperCase()); }
}

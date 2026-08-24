package co.elpatio.dominio.reclutamiento;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Los documentos con que se identifica quien se postula.
 *
 * PEP y PPT estan por una razon concreta, no por completitud: son los permisos
 * con que trabaja la poblacion migrante venezolana, y en la Costa una parte
 * real de quien busca empleo en un restaurante se identifica asi. Dejarlos
 * fuera seria cerrarles el formulario.
 */
public enum TipoDocumento {
  /** Cedula de ciudadania. */
  CC,
  /** Cedula de extranjeria. */
  CE,
  /** Permiso Especial de Permanencia. */
  PEP,
  /** Permiso por Proteccion Temporal. */
  PPT,
  /** Tarjeta de identidad: un menor de edad solo puede trabajar con permiso. */
  TI;

  @JsonValue
  public String codigo() { return name(); }

  @JsonCreator
  public static TipoDocumento de(String valor) { return valueOf(valor.toUpperCase()); }
}

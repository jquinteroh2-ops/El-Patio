package co.elpatio.dominio.puertos;

import java.util.List;

/**
 * Aviso de que algo cambio, para que las demas pantallas se enteren solas.
 *
 * Reemplaza al BroadcastChannel del prototipo, que solo alcanzaba a las
 * pestanas del mismo navegador. La forma del evento se conservo igual a la de
 * almacen.ts para que suscribir() no tenga que cambiar de contrato.
 */
public interface PublicadorEventos {

  /**
   * @param cambios nombres de lo que cambio: "ordenes", "mesas", "cocina"...
   */
  void publicar(List<String> cambios);
}

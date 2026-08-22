package co.elpatio.infraestructura.imagenes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El unico trozo de la integracion con Cloudinary que se puede probar sin
 * cuenta ni red: sacar el identificador a partir de la direccion guardada.
 *
 * Se prueba porque es el que decide si una foto se borra de verdad. Si esto
 * falla, borrar una publicacion la quita del sitio pero deja la imagen ocupando
 * espacio en la cuenta para siempre, y nadie se entera hasta que se llena.
 */
class AlmacenCloudinaryTest {

  @Test
  @DisplayName("saca el identificador de una direccion con version")
  void conVersion() {
    String direccion =
        "https://res.cloudinary.com/elpatio/image/upload/v1712345678/elpatio/terraza.jpg";
    assertThat(AlmacenCloudinary.identificadorDe(direccion)).isEqualTo("elpatio/terraza");
  }

  @Test
  @DisplayName("saca el identificador cuando Cloudinary no puso version")
  void sinVersion() {
    String direccion = "https://res.cloudinary.com/elpatio/image/upload/elpatio/terraza.jpg";
    assertThat(AlmacenCloudinary.identificadorDe(direccion)).isEqualTo("elpatio/terraza");
  }

  @Test
  @DisplayName("no confunde una carpeta que empieza por v con una version")
  void carpetaQueEmpiezaPorV() {
    // «verano» empieza por v pero no es una version: lo que sigue no son
    // digitos. Sin esa comprobacion, la carpeta se perderia y el borrado
    // apuntaria a una imagen que no existe.
    String direccion = "https://res.cloudinary.com/elpatio/image/upload/verano/foto.jpg";
    assertThat(AlmacenCloudinary.identificadorDe(direccion)).isEqualTo("verano/foto");
  }

  @Test
  @DisplayName("una direccion sin extension tambien sirve")
  void sinExtension() {
    String direccion = "https://res.cloudinary.com/elpatio/image/upload/v1/elpatio/terraza";
    assertThat(AlmacenCloudinary.identificadorDe(direccion)).isEqualTo("elpatio/terraza");
  }

  @Test
  @DisplayName("lo que no es una direccion no se intenta borrar")
  void loQueNoEsDireccion() {
    // Un nombre del almacen en disco, o un nulo, no tienen nada que borrar en
    // Cloudinary. Devolver nulo evita mandarle a la cuenta una peticion sin
    // sentido cada vez que se elimina una publicacion antigua.
    assertThat(AlmacenCloudinary.identificadorDe("9f149bd4600e4d1f.jpg")).isNull();
    assertThat(AlmacenCloudinary.identificadorDe(null)).isNull();
    assertThat(AlmacenCloudinary.identificadorDe("https://ejemplo.com/foto.jpg")).isNull();
  }
}

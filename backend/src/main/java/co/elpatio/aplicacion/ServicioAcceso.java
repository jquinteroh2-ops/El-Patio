package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.infraestructura.persistencia.dao.DaoSesionesRefresh;
import co.elpatio.infraestructura.persistencia.filas.FilaSesionRefresh;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ingreso, renovacion de sesion y administracion del personal. */
@Service
public class ServicioAcceso {

  /**
   * Validacion del correo, deliberadamente floja: algo, una arroba, algo con
   * un punto. Las expresiones que persiguen el RFC al pie de la letra rechazan
   * correos que existen y aceptan otros que no, y lo unico que hace falta aqui
   * es atajar el dedo que escribio el nombre en la casilla equivocada. Quien
   * confirma que un correo existe es el correo que llega, no una expresion.
   */
  private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final Repositorios.DeUsuarios usuarios;
  private final DaoSesionesRefresh sesiones;
  private final ServicioTokens tokens;
  private final PasswordEncoder claves;
  private final Reloj reloj;
  private final GeneradorIds ids;
  private final PublicadorEventos eventos;

  public ServicioAcceso(
      Repositorios.DeUsuarios usuarios,
      DaoSesionesRefresh sesiones,
      ServicioTokens tokens,
      PasswordEncoder claves,
      Reloj reloj,
      GeneradorIds ids,
      PublicadorEventos eventos) {
    this.usuarios = usuarios;
    this.sesiones = sesiones;
    this.tokens = tokens;
    this.claves = claves;
    this.reloj = reloj;
    this.ids = ids;
    this.eventos = eventos;
  }

  /**
   * Ingreso con usuario y clave.
   *
   * El mensaje de error es el mismo cuando el usuario no existe y cuando la
   * clave esta mal: decir cual de los dos fallo le regala a quien tantea la
   * mitad del trabajo.
   */
  @Transactional
  public Dtos.RespuestaAcceso ingresar(String usuario, String clave) {
    Usuario encontrado =
        usuarios
            .porNombreDeUsuario(usuario == null ? "" : usuario.trim())
            .filter(u -> claves.matches(clave == null ? "" : clave, u.getClaveHash()))
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ReglaDeNegocioError("Usuario o contraseña incorrectos"));

    return emitir(encontrado);
  }

  /**
   * Renueva la sesion.
   *
   * El refresco es de un solo uso: al canjearlo se revoca y se entrega otro. Si
   * alguien copio el token de una tablet, en cuanto el dueno legitimo renueve,
   * la copia deja de servir.
   */
  @Transactional
  public Dtos.RespuestaAcceso refrescar(String refresco) {
    if (refresco == null || refresco.isBlank()) {
      throw new ReglaDeNegocioError("La sesión expiró: vuelva a ingresar");
    }
    FilaSesionRefresh fila =
        sesiones
            .findByTokenHash(tokens.hashDe(refresco))
            .orElseThrow(() -> new ReglaDeNegocioError("La sesión expiró: vuelva a ingresar"));

    if (fila.isRevocado() || fila.getExpiraEn().isBefore(reloj.ahora())) {
      throw new ReglaDeNegocioError("La sesión expiró: vuelva a ingresar");
    }

    Usuario usuario =
        usuarios
            .porId(fila.getUsuarioId())
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new ReglaDeNegocioError("Su usuario ya no está activo"));

    fila.revocar();
    sesiones.save(fila);
    return emitir(usuario);
  }

  /** Cierra la sesion de este dispositivo. */
  @Transactional
  public void salir(String refresco) {
    if (refresco == null || refresco.isBlank()) return;
    sesiones
        .findByTokenHash(tokens.hashDe(refresco))
        .ifPresent(
            fila -> {
              fila.revocar();
              sesiones.save(fila);
            });
  }

  private Dtos.RespuestaAcceso emitir(Usuario usuario) {
    Instant ahora = reloj.ahora();

    // Las vencidas se barren aqui y no en una tarea programada: el ingreso es
    // poco frecuente y basta para que la tabla no crezca sin control.
    sesiones.borrarVencidas(ahora);

    String refresco = tokens.nuevoRefresco();
    sesiones.save(
        new FilaSesionRefresh(
            tokens.nuevoIdSesion(),
            usuario.getId(),
            tokens.hashDe(refresco),
            ahora,
            ahora.plus(tokens.vidaRefresco())));

    Dtos.Sesion sesion =
        new Dtos.Sesion(
            usuario.getId(), usuario.getNombre(), usuario.getRol(), usuario.getUsuario(), ahora);

    return new Dtos.RespuestaAcceso(
        sesion, tokens.emitirAcceso(usuario), refresco, tokens.vidaAcceso().toSeconds());
  }

  // ---------------------------------------------------------------------------
  // Personal
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Dtos.UsuarioDto> listarUsuarios() {
    return usuarios.listar().stream().map(Dtos.UsuarioDto::de).toList();
  }

  /**
   * Crea o actualiza un usuario.
   *
   * Una clave vacia en una actualizacion significa "dejela como esta": la
   * pantalla nunca recibe el hash, asi que no puede reenviarlo, y sin esta
   * regla cada edicion de nombre borraria la clave de la persona.
   */
  @Transactional
  public Dtos.UsuarioDto guardarUsuario(Dtos.UsuarioDto entrada) {
    boolean esNuevo = entrada.id() == null || entrada.id().isBlank();
    Usuario usuario;

    if (esNuevo) {
      if (entrada.clave() == null || entrada.clave().isBlank()) {
        throw new ReglaDeNegocioError("Un usuario nuevo necesita una clave");
      }
      usuario = new Usuario();
      usuario.setId(ids.nuevo("u"));
      usuario.setClaveHash(claves.encode(entrada.clave()));
    } else {
      usuario =
          usuarios
              .porId(entrada.id())
              .orElseThrow(() -> new NoEncontradoError("El usuario no existe"));
      if (entrada.clave() != null && !entrada.clave().isBlank()) {
        usuario.setClaveHash(claves.encode(entrada.clave()));
        // Cambiar la clave tiene que echar de todas las sesiones abiertas: si no,
        // el token viejo seguiria sirviendo justo cuando se quiso cortar el acceso.
        sesiones.revocarTodasDe(usuario.getId());
      }
    }

    String nombreDeUsuario = entrada.usuario().trim();
    usuarios
        .porNombreDeUsuario(nombreDeUsuario)
        .filter(otro -> !otro.getId().equals(usuario.getId()))
        .ifPresent(
            otro -> {
              throw new ReglaDeNegocioError("Ya hay alguien con el usuario «" + nombreDeUsuario + "»");
            });

    String correo = entrada.correo() == null ? "" : entrada.correo().trim();
    if (!correo.isEmpty() && !CORREO.matcher(correo).matches()) {
      throw new ReglaDeNegocioError("«" + correo + "» no parece un correo");
    }

    usuario.setNombre(entrada.nombre().trim());
    usuario.setRol(entrada.rol());
    usuario.setUsuario(nombreDeUsuario);
    // Vacio se guarda como nulo: asi «sin correo» es un solo valor en la base y
    // no dos que hay que recordar comparar por separado.
    usuario.setCorreo(correo.isEmpty() ? null : correo);
    usuario.setActivo(entrada.activo());

    if (!usuario.isActivo()) sesiones.revocarTodasDe(usuario.getId());

    Dtos.UsuarioDto guardado = Dtos.UsuarioDto.de(usuarios.guardar(usuario));
    eventos.publicar(List.of("usuarios"));
    return guardado;
  }
}

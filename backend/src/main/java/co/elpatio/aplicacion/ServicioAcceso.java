package co.elpatio.aplicacion;

import co.elpatio.aplicacion.dto.Dtos;
import co.elpatio.dominio.error.NoEncontradoError;
import co.elpatio.dominio.error.ReglaDeNegocioError;
import co.elpatio.dominio.personal.Rol;
import co.elpatio.dominio.personal.Usuario;
import co.elpatio.dominio.puertos.GeneradorIds;
import co.elpatio.dominio.puertos.PublicadorEventos;
import co.elpatio.dominio.puertos.Reloj;
import co.elpatio.dominio.puertos.Repositorios;
import co.elpatio.infraestructura.persistencia.dao.DaoPasesDeCruce;
import co.elpatio.infraestructura.persistencia.dao.DaoSesionesRefresh;
import co.elpatio.infraestructura.persistencia.filas.FilaPaseDeCruce;
import co.elpatio.infraestructura.persistencia.filas.FilaSesionRefresh;
import co.elpatio.infraestructura.seguridad.ServicioEspejoDeCuenta;
import co.elpatio.infraestructura.seguridad.ServicioPaseDeCruce;
import co.elpatio.infraestructura.seguridad.ServicioTokens;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ingreso, renovacion de sesion y administracion del personal. */
@Service
public class ServicioAcceso {

  private static final Logger registro = LoggerFactory.getLogger(ServicioAcceso.class);

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
  private final DaoPasesDeCruce pases;
  private final ServicioTokens tokens;
  private final ServicioPaseDeCruce cruce;
  private final PasswordEncoder claves;
  private final Reloj reloj;
  private final GeneradorIds ids;
  private final PublicadorEventos eventos;

  public ServicioAcceso(
      Repositorios.DeUsuarios usuarios,
      DaoSesionesRefresh sesiones,
      DaoPasesDeCruce pases,
      ServicioTokens tokens,
      ServicioPaseDeCruce cruce,
      PasswordEncoder claves,
      Reloj reloj,
      GeneradorIds ids,
      PublicadorEventos eventos) {
    this.usuarios = usuarios;
    this.sesiones = sesiones;
    this.pases = pases;
    this.tokens = tokens;
    this.cruce = cruce;
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

  // ---------------------------------------------------------------------------
  // Cruce al otro restaurante
  // ---------------------------------------------------------------------------

  /**
   * Firma un pase para que quien ya entro aqui entre al otro restaurante sin
   * volver a escribir la clave.
   *
   * Solo el administrador. No es por esconder un boton: a un mesero o a un
   * cajero no le sirve —trabaja en un local— y cada rol que pudiera cruzar
   * seria una puerta mas que vigilar entre dos sistemas.
   *
   * Se relee el usuario de la base en vez de creerle al token. El token pudo
   * emitirse hace veinte minutos y en ese rato al dueno pudieron retirarle el
   * rol o desactivarle la cuenta; el pase que se firma aqui abre el panel del
   * otro restaurante, y no puede apoyarse en algo que quizas ya no es cierto.
   */
  @Transactional(readOnly = true)
  public Dtos.RespuestaPaseDeCruce emitirPaseDeCruce(String usuarioId) {
    if (!cruce.activo()) {
      throw new ReglaDeNegocioError("El cruce entre restaurantes no está configurado");
    }

    Usuario usuario =
        usuarios
            .porId(usuarioId)
            .filter(Usuario::isActivo)
            .filter(u -> u.getRol() == Rol.ADMINISTRADOR)
            .orElseThrow(() -> new ReglaDeNegocioError("Solo el administrador puede cambiar de restaurante"));

    return new Dtos.RespuestaPaseDeCruce(cruce.emitir(usuario));
  }

  /**
   * Canjea un pase del otro restaurante por una sesion de este.
   *
   * EL PASE NO ABRE NADA POR SI MISMO: dice quien es la persona, y quien decide
   * es esta casa. Hacen falta las tres cosas —firma valida, pase sin usar, y
   * una cuenta de administrador activa AQUI con ese mismo nombre de usuario—.
   * Si el dueno no tiene cuenta en este restaurante, o se la desactivaron, el
   * pase no sirve aunque venga perfectamente firmado.
   *
   * El mensaje de error es el mismo en todos los casos, por lo mismo que en el
   * ingreso: distinguir «ese pase ya se uso» de «ese usuario no existe aqui» le
   * regala informacion a quien este tanteando.
   */
  @Transactional
  public Dtos.RespuestaAcceso canjearPaseDeCruce(String pase) {
    if (!cruce.activo()) {
      throw new ReglaDeNegocioError("El cruce entre restaurantes no está configurado");
    }

    ServicioPaseDeCruce.Pase valido =
        cruce.verificar(pase).orElseThrow(() -> new ReglaDeNegocioError(RECHAZO_DEL_CRUCE));

    // Un solo uso. El insert es lo que lo garantiza: la clave primaria es el
    // identificador del pase, asi que un segundo canje del mismo papel choca
    // contra la base en vez de depender de que dos peticiones simultaneas se
    // vean la una a la otra.
    Instant ahora = reloj.ahora();
    pases.borrarVencidos(ahora);
    if (pases.existsById(valido.jti())) {
      throw new ReglaDeNegocioError(RECHAZO_DEL_CRUCE);
    }

    Usuario local =
        usuarios
            .porNombreDeUsuario(valido.usuario())
            .filter(Usuario::isActivo)
            .filter(u -> u.getRol() == Rol.ADMINISTRADOR)
            .orElseThrow(() -> new ReglaDeNegocioError(RECHAZO_DEL_CRUCE));

    pases.save(
        new FilaPaseDeCruce(
            valido.jti(), local.getUsuario(), valido.origen(), ahora, valido.expiraEn()));

    return emitir(local);
  }

  /**
   * Lo que se responde ante cualquier pase que no sirva.
   *
   * Uno solo para todos los motivos: firma mala, vencido, ya usado, o sin
   * cuenta de administrador en esta casa. Decir cual de los cuatro fallo le
   * ahorra la mitad del trabajo a quien tantea.
   */
  private static final String RECHAZO_DEL_CRUCE =
      "No se pudo entrar desde el otro restaurante: ingrese con su usuario y clave";

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
  public UsuarioGuardado guardarUsuario(Dtos.UsuarioDto entrada) {
    boolean esNuevo = entrada.id() == null || entrada.id().isBlank();
    Usuario usuario;
    boolean claveCambio = false;
    // Con el que hay que buscar la cuenta en el otro restaurante. Se anota
    // ANTES de tocar nada: si el dueno se esta cambiando el nombre de usuario,
    // el nuevo no existe alla y buscarlo por el no encontraria nada.
    String usuarioAnterior = null;

    if (esNuevo) {
      if (entrada.clave() == null || entrada.clave().isBlank()) {
        throw new ReglaDeNegocioError("Un usuario nuevo necesita una clave");
      }
      usuario = new Usuario();
      usuario.setId(ids.nuevo("u"));
      usuario.setClaveHash(claves.encode(entrada.clave()));
      claveCambio = true;
    } else {
      usuario =
          usuarios
              .porId(entrada.id())
              .orElseThrow(() -> new NoEncontradoError("El usuario no existe"));
      usuarioAnterior = usuario.getUsuario();
      if (entrada.clave() != null && !entrada.clave().isBlank()) {
        usuario.setClaveHash(claves.encode(entrada.clave()));
        claveCambio = true;
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

    Usuario fila = usuarios.guardar(usuario);
    eventos.publicar(List.of("usuarios"));
    return new UsuarioGuardado(
        Dtos.UsuarioDto.de(fila),
        usuarioAnterior == null ? fila.getUsuario() : usuarioAnterior,
        fila.getRol() == Rol.ADMINISTRADOR,
        claveCambio ? fila.getClaveHash() : null);
  }

  /**
   * Lo que el controlador necesita para replicar el cambio al otro restaurante.
   *
   * El hash NO sale de aqui hacia el navegador: lo usa el controlador para
   * meterlo en el sobre firmado y nada mas. Va nulo cuando la clave no cambio,
   * para que el destino sepa que no tiene que tocar la suya.
   */
  public record UsuarioGuardado(
      Dtos.UsuarioDto usuario, String usuarioAnterior, boolean esAdministrador, String claveHash) {}

  /**
   * Aplica el cambio que mando el otro restaurante sobre la cuenta del dueno.
   *
   * Se busca por el nombre de usuario ANTERIOR y se exige que la cuenta local
   * sea de administrador. Si no existe, no pasa nada y se responde que no se
   * aplico: ese administrador solo trabaja en el otro local.
   *
   * NO se tocan el rol ni si la cuenta esta activa. Los dos dicen que puede
   * hacer la persona AQUI, y eso lo decide cada restaurante: suspenderle el
   * acceso a un local no tiene por que cerrarle el otro.
   *
   * Es idempotente a proposito. Si la respuesta se pierde y el origen reintenta,
   * el segundo intento deja lo mismo que el primero, y las dos cuentas quedan
   * iguales sin que nadie tenga que arreglar nada a mano.
   */
  @Transactional
  public boolean aplicarEspejo(ServicioEspejoDeCuenta.Cambio cambio) {
    Usuario local =
        usuarios
            .porNombreDeUsuario(cambio.usuarioAnterior() == null ? "" : cambio.usuarioAnterior())
            .filter(u -> u.getRol() == Rol.ADMINISTRADOR)
            .orElse(null);
    if (local == null) return false;

    // El nombre de usuario tambien viaja, pero solo se acepta si no se lo esta
    // quitando a otra cuenta de esta casa.
    String nuevoUsuario = cambio.usuario() == null ? local.getUsuario() : cambio.usuario().trim();
    boolean chocaConOtro =
        usuarios.porNombreDeUsuario(nuevoUsuario).filter(o -> !o.getId().equals(local.getId())).isPresent();
    if (!nuevoUsuario.isBlank() && !chocaConOtro) local.setUsuario(nuevoUsuario);

    if (cambio.nombre() != null && !cambio.nombre().isBlank()) local.setNombre(cambio.nombre().trim());
    local.setCorreo(cambio.correo() == null || cambio.correo().isBlank() ? null : cambio.correo().trim());

    if (cambio.claveHash() != null && !cambio.claveHash().isBlank()) {
      local.setClaveHash(cambio.claveHash());
      // La clave cambio: las sesiones abiertas de este lado tienen que caerse
      // igual que si se hubiera cambiado aqui. Si no, el token viejo seguiria
      // sirviendo justo cuando se quiso cortar el acceso.
      sesiones.revocarTodasDe(local.getId());
    }

    usuarios.guardar(local);
    eventos.publicar(List.of("usuarios"));
    registro.info(
        "Cuenta «{}» actualizada desde {}", local.getUsuario(), cambio.origen());
    return true;
  }
}

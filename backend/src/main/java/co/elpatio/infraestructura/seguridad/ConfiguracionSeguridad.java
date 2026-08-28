package co.elpatio.infraestructura.seguridad;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Quien puede entrar y a que.
 *
 * Las reglas por area estan aqui y no repartidas en anotaciones porque asi se
 * leen de corrido: es el mapa de permisos del restaurante en una pantalla. Los
 * casos que dependen del rol dentro de un mismo endpoint se refuerzan ademas
 * con @PreAuthorize en el servicio.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

  private final String[] origenesPermitidos;

  public ConfiguracionSeguridad(@Value("${elpatio.cors.origenes}") String[] origenesPermitidos) {
    this.origenesPermitidos = origenesPermitidos;
  }

  /**
   * BCrypt con costo 12. El costo por defecto (10) se quedo corto frente al
   * hardware actual, y 12 sigue tardando pocos milisegundos en un ingreso, que
   * es una operacion que cada mesero hace una vez por turno.
   */
  @Bean
  PasswordEncoder codificadorDeClaves() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain cadenaDeFiltros(HttpSecurity http, FiltroJwt filtroJwt) throws Exception {
    http
        // No hay cookies de sesion: la credencial viaja en la cabecera
        // Authorization, asi que no hay superficie para CSRF.
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(fuenteCors()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            reglas ->
                reglas
                    // Acceso y salud quedan abiertos: son la puerta y el pulso.
                    .requestMatchers("/api/acceso/ingresar", "/api/acceso/refrescar").permitAll()
                    // La pantalla de acceso pregunta si hay cuentas de
                    // demostracion antes de tener sesion. Con el modo apagado
                    // la respuesta es una lista vacia.
                    .requestMatchers(HttpMethod.GET, "/api/acceso/demostracion").permitAll()
                    .requestMatchers("/salud", "/salud/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // Los webhooks de proveedores externos (Wompi, y mas
                    // adelante Meta) no tienen sesion de nadie: se autentican
                    // con su propia firma, verificada dentro del controlador.
                    .requestMatchers("/webhooks/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // La carta y las reservas las consulta el sitio publico sin
                    // sesion: es el menu que cualquiera puede leer desde la calle.
                    .requestMatchers(HttpMethod.GET, "/api/carta/**").permitAll()
                    // Las promociones, los eventos y las fotos del local son
                    // justamente lo que el restaurante quiere que vea quien
                    // todavia no es cliente. El resto de /api/publicaciones
                    // -incluida la lista con los borradores- exige sesion.
                    .requestMatchers(HttpMethod.GET, "/api/publicaciones/visibles").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/publicaciones/imagenes/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/reservas").permitAll()

                    // El texto institucional visible es contenido de la pagina
                    // de inicio: lo lee cualquiera que pase por el sitio. La
                    // edicion vive bajo /api/admin y exige administrador.
                    .requestMatchers(HttpMethod.GET, "/api/institucional").permitAll()

                    // «Trabaja con nosotros». Quien busca empleo no tiene ni va
                    // a crear una cuenta aqui, asi que el formulario es publico
                    // por diseño. Lo que lo defiende no es la sesion sino el
                    // limite por IP, el señuelo y el control de repetidos, que
                    // viven en el controlador. Todo lo que sea LEER hojas de
                    // vida vive bajo /api/admin y exige administrador.
                    .requestMatchers(HttpMethod.GET, "/api/public/postulaciones/cargos").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/public/postulaciones").permitAll()

                    // PQR. Un canal de quejas que exige crear una cuenta no es
                    // un canal de quejas. La consulta es publica pero exige
                    // radicado Y correo, que es lo que impide recorrer los
                    // numeros en orden y leer las quejas de todo el mundo.
                    .requestMatchers(HttpMethod.GET, "/api/public/pqr/tipos").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/pqr/consulta").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/public/pqr").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")

                    // El cliente pide desde su celular sin tener usuario. Son las
                    // dos unicas rutas del canal abiertas, y las dos escriben o
                    // leen solo lo que ese cliente necesita para pedir.
                    .requestMatchers(HttpMethod.GET, "/api/pedidos/canal").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/pedidos").permitAll()
                    // El repartidor entra al canal, pero solo a lo suyo: las dos
                    // rutas de /mios. El @PreAuthorize de cada metodo es el que
                    // separa mirar la calle de mover el tablero de recepcion.
                    .requestMatchers("/api/pedidos/**")
                        .hasAnyRole("RECEPCION", "REPARTIDOR", "CAJERO", "ADMINISTRADOR")

                    // Responder una reserva es trabajo de mostrador: recepcion
                    // ya atiende el telefono, asi que recibe las solicitudes del
                    // sitio publico igual que recibe los domicilios. Crearlas
                    // sigue abierto mas arriba, que es lo que usa el cliente.
                    .requestMatchers("/api/reservas/**")
                        .hasAnyRole("RECEPCION", "CAJERO", "ADMINISTRADOR")

                    // El cajero entra a la comandera para cobrar y para ver el
                    // salon desde el panel; lo que puede hacer una vez dentro lo
                    // decide el @PreAuthorize de cada metodo, que es donde la
                    // diferencia entre leer una cuenta y editarla tiene sentido.
                    // Recepcion pasa por aqui solo por el mapa de mesas, que es
                    // lo unico que el @PreAuthorize de la clase le deja abrir.
                    .requestMatchers("/api/comandera/**")
                        .hasAnyRole("MESERO", "RECEPCION", "CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/cocina/**").hasAnyRole("COCINA", "ADMINISTRADOR")
                    .requestMatchers("/api/cobro/**").hasAnyRole("MESERO", "CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/caja/**").hasAnyRole("CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/reportes/**").hasRole("ADMINISTRADOR")
                    // La conciliacion muestra la venta completa del periodo:
                    // es informacion de cierre contable, no de operacion.
                    .requestMatchers("/api/erp/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                    .requestMatchers(HttpMethod.PUT, "/api/ajustes").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/carta/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/publicaciones/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/salon/**").hasAnyRole("MESERO", "CAJERO", "ADMINISTRADOR")

                    .anyRequest().authenticated())
        .headers(
            cabeceras ->
                cabeceras
                    // HTTPS obligatorio. Railway termina el TLS por delante, asi
                    // que la aplicacion no redirige: le dice al navegador que
                    // para este dominio no vuelva a intentar en claro. Un ano,
                    // que es el minimo que exige la lista de precarga.
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    // El API responde JSON: nada de esto se embebe en un marco
                    // ni se abre como documento en otra pagina.
                    .frameOptions(marco -> marco.deny())
                    .contentTypeOptions(tipo -> {})
                    .referrerPolicy(
                        politica ->
                            politica.policy(
                                org.springframework.security.web.header.writers
                                        .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .SAME_ORIGIN)))
        .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            manejo ->
                manejo
                    .authenticationEntryPoint(new PuntoDeEntradaNoAutorizado())
                    .accessDeniedHandler(new ManejadorAccesoDenegado()));

    return http.build();
  }

  /**
   * CORS restringido a los dominios declarados. Nunca `*`: el navegador del
   * mesero envia el token, y un origen abierto dejaria que cualquier pagina lo
   * usara en su nombre.
   */
  @Bean
  CorsConfigurationSource fuenteCors() {
    CorsConfiguration configuracion = new CorsConfiguration();
    configuracion.setAllowedOriginPatterns(Arrays.asList(origenesPermitidos));
    configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuracion.setAllowCredentials(true);
    configuracion.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/**", configuracion);
    return fuente;
  }
}

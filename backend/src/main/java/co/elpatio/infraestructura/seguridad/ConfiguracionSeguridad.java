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
                    .requestMatchers("/salud", "/salud/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // La carta y las reservas las consulta el sitio publico sin
                    // sesion: es el menu que cualquiera puede leer desde la calle.
                    .requestMatchers(HttpMethod.GET, "/api/carta/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/reservas").permitAll()

                    // El cajero entra a la comandera para cobrar y para ver el
                    // salon desde el panel; lo que puede hacer una vez dentro lo
                    // decide el @PreAuthorize de cada metodo, que es donde la
                    // diferencia entre leer una cuenta y editarla tiene sentido.
                    .requestMatchers("/api/comandera/**").hasAnyRole("MESERO", "CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/cocina/**").hasAnyRole("COCINA", "ADMINISTRADOR")
                    .requestMatchers("/api/cobro/**").hasAnyRole("MESERO", "CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/caja/**").hasAnyRole("CAJERO", "ADMINISTRADOR")
                    .requestMatchers("/api/reportes/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                    .requestMatchers(HttpMethod.PUT, "/api/ajustes").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/carta/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/salon/**").hasAnyRole("MESERO", "CAJERO", "ADMINISTRADOR")

                    .anyRequest().authenticated())
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

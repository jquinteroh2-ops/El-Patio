package co.elpatio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Punto de entrada del backend del Restaurante El Patio. */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ElPatioAplicacion {

  public static void main(String[] argumentos) {
    SpringApplication.run(ElPatioAplicacion.class, argumentos);
  }
}

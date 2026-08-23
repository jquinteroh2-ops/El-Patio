package co.elpatio.infraestructura.ia;

import static org.assertj.core.api.Assertions.assertThat;

import co.elpatio.dominio.interprete.ResultadoInterpretacion;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * El parseo de la respuesta del modelo, aislado de la llamada de red: aqui es
 * donde viven las reglas de seguridad (nunca confiar un id que no este en el
 * menu, nunca inventar un producto si el JSON viene roto).
 */
class InterpreteClaudeTest {

  private static final Set<String> MENU = Set.of("p1", "p2");

  @Test
  void reconoceLosItemsCuyoIdEstaEnElMenu() {
    String respuesta = "{\"items\":[{\"id\":\"p1\",\"cantidad\":2}],\"no_reconocidos\":[]}";

    ResultadoInterpretacion resultado = InterpreteClaude.parsear(respuesta, MENU, "2 arepas");

    assertThat(resultado.reconocidos()).hasSize(1);
    assertThat(resultado.reconocidos().get(0).itemCartaId()).isEqualTo("p1");
    assertThat(resultado.reconocidos().get(0).cantidad()).isEqualTo(2);
    assertThat(resultado.noReconocidos()).isEmpty();
  }

  @Test
  void descartaUnIdQueNoEstaEnElMenuAunqueElModeloLoInvente() {
    String respuesta = "{\"items\":[{\"id\":\"p1\",\"cantidad\":1},{\"id\":\"inventado\",\"cantidad\":1}],\"no_reconocidos\":[]}";

    ResultadoInterpretacion resultado = InterpreteClaude.parsear(respuesta, MENU, "algo");

    assertThat(resultado.reconocidos()).hasSize(1);
    assertThat(resultado.reconocidos().get(0).itemCartaId()).isEqualTo("p1");
  }

  @Test
  void descartaUnaCantidadCeroONegativa() {
    String respuesta = "{\"items\":[{\"id\":\"p1\",\"cantidad\":0}],\"no_reconocidos\":[]}";

    ResultadoInterpretacion resultado = InterpreteClaude.parsear(respuesta, MENU, "algo");

    assertThat(resultado.reconocidos()).isEmpty();
  }

  @Test
  void propagaLosNoReconocidos() {
    String respuesta = "{\"items\":[],\"no_reconocidos\":[\"una pizza hawaiana\"]}";

    ResultadoInterpretacion resultado = InterpreteClaude.parsear(respuesta, MENU, "algo");

    assertThat(resultado.noReconocidos()).containsExactly("una pizza hawaiana");
  }

  @Test
  void unJsonInvalidoTrataTodoElTextoComoNoReconocido() {
    ResultadoInterpretacion resultado = InterpreteClaude.parsear("esto no es JSON", MENU, "2 arepas e huevo");

    assertThat(resultado.reconocidos()).isEmpty();
    assertThat(resultado.noReconocidos()).containsExactly("2 arepas e huevo");
  }

  @Test
  void aceptaElJsonEnvueltoEnCercaDeCodigo() {
    String respuesta = "```json\n{\"items\":[{\"id\":\"p2\",\"cantidad\":1}],\"no_reconocidos\":[]}\n```";

    ResultadoInterpretacion resultado = InterpreteClaude.parsear(respuesta, MENU, "algo");

    assertThat(resultado.reconocidos()).hasSize(1);
    assertThat(resultado.reconocidos().get(0).itemCartaId()).isEqualTo("p2");
  }
}

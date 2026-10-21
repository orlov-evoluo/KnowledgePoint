package knowledgepoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTest {

  private static final Path DIR = createDir();

  @Autowired
  private MockMvc mvc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    String database = DIR.resolve("session.sqlite").toAbsolutePath().toString().replace('\\', '/');
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database);
    registry.add("knowledgepoint.page", () -> DIR.resolve("index.html").toString());
    registry.add("TOCHKA_TOKEN", () -> "");
  }

  @BeforeEach
  void emptySession() throws Exception {
    mvc.perform(delete("/api/session")).andExpect(status().isOk());
  }

  @Test
  void sessionStartsEmptyAndNeverReturnsTheToken() throws Exception {
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(jsonPath("$.stored").value(false));

    mvc.perform(post("/api/session")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Вставьте токен кабинета"));

    mvc.perform(post("/api/session")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"dummy-token-value-not-real-123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.stored").value(true))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dummy-token"))));
  }

  @Test
  void missingTokenBlocksTheCabinet() throws Exception {
    mvc.perform(get("/api/profile"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Сохраните токен кабинета"));

    mvc.perform(post("/api/profile").content("{}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error").value("method"));

    mvc.perform(get("/api/secret"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found"));
  }

  @Test
  void pageAndUnknownPaths() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Успеваемость")));

    mvc.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(content().string("not found"));
  }

  private static Path createDir() {
    try {
      Path dir = Files.createTempDirectory("knowledge-point");
      Files.writeString(dir.resolve("index.html"), "<!doctype html><title>Успеваемость</title>");
      return dir;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}

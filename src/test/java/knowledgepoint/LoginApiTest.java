package knowledgepoint;

import knowledgepoint.auth.CabinetLogin;
import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginApiTest {

  private static final Path DIR = createDir();
  private static final String ACCESS = "dummy-access-token-value-123456";

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CabinetClient cabinet;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    String database = DIR.resolve("session.sqlite").toAbsolutePath().toString().replace('\\', '/');
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database);
    registry.add("knowledgepoint.page", () -> DIR.resolve("index.html").toString());
    registry.add("TOCHKA_TOKEN", () -> "");
  }

  @Test
  void loginStoresOnlyTheAccessToken() throws Exception {
    assertTrue(CabinetLogin.PATH.startsWith("/auth/login?"));
    assertTrue(CabinetLogin.PATH.contains("utm_ym_uid=1793292569766504724"));
    assertTrue(CabinetLogin.PATH.contains("utm_advcake_track_id=55efe78b-09c0-778d-e896-96a75f2d8389"));
    assertTrue(CabinetLogin.PATH.endsWith("&utm_last_source_cake=false"));
    when(cabinet.post(anyString(), anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
        .thenReturn(new CabinetResponse(200, """
            {"token":{"accessToken":"%s","accessExpires":86400,"refreshToken":"dummy-refresh-should-not-leak","refreshExpires":3240000}}
            """.formatted(ACCESS)));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"pupil\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(ACCESS))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dummy-refresh"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));

    verify(cabinet).post(
        eq(CabinetLogin.PATH),
        org.mockito.ArgumentMatchers.argThat(body ->
            body.contains("\"username\":\"pupil\"") && body.contains("\"password\":\"secret\"")),
        org.mockito.ArgumentMatchers.<Map<String, String>>argThat(headers ->
            CabinetLogin.SERVICE_VERSION.equals(headers.get("x-service-version"))));
  }

  @Test
  void rejectedPasswordDoesNotReturnTheCabinetBody() throws Exception {
    when(cabinet.post(anyString(), anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
        .thenReturn(new CabinetResponse(401, "{\"message\":\"hidden-cabinet-detail\",\"token\":{\"accessToken\":\"" + ACCESS + "\"}}"));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"pupil\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Неверный логин или пароль"))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hidden-cabinet"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(ACCESS))));
  }

  @Test
  void unknownPasswordIsRejectedWithoutTheCabinetMessage() throws Exception {
    when(cabinet.post(anyString(), anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
        .thenReturn(new CabinetResponse(400, "{\"message\":\"bad login or password\",\"humanMessage\":\"Логин или пароль введены неверно\"}"));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"pupil\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Неверный логин или пароль"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("humanMessage"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wrong"))));
  }

  @Test
  void missingPasswordDoesNotCallTheCabinet() throws Exception {
    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"pupil\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Введите логин и пароль"));

    verify(cabinet, never()).post(anyString(), anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any());
  }

  @Test
  void loginPageIsServed() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Пароль")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("utm_advcake"))));
  }

  private static Path createDir() {
    try {
      Path dir = Files.createTempDirectory("knowledge-point-login");
      Files.writeString(dir.resolve("index.html"), "<!doctype html><title>Успеваемость</title>");
      Files.writeString(dir.resolve("login.html"), "<!doctype html><label>Пароль</label><script>\"/api/auth/login\"</script>");
      return dir;
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }
}

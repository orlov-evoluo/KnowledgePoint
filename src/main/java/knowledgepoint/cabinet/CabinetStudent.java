package knowledgepoint.cabinet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import knowledgepoint.session.SessionStore;
import org.springframework.stereotype.Component;

@Component
public class CabinetStudent {

  private final CabinetClient cabinet;
  private final ObjectMapper json;

  public CabinetStudent(CabinetClient cabinet, ObjectMapper json) {
    this.cabinet = cabinet;
    this.json = json;
  }

  public StudentAccess identify(String authorization) {
    String token = SessionStore.normalize(authorization);
    if (token == null || token.length() < 20) {
      return new StudentAccess(503, 0);
    }
    try {
      CabinetResponse profile = cabinet.get("/profile", token);
      if (profile.status() != 200) {
        return new StudentAccess(profile.status() == 401 ? 401 : 502, 0);
      }
      JsonNode id = json.readTree(profile.body()).path("client").get("id");
      if (id == null || !id.canConvertToLong() || id.asLong() <= 0) {
        return new StudentAccess(502, 0);
      }
      return new StudentAccess(200, id.asLong());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new StudentAccess(502, 0);
    } catch (Exception exception) {
      return new StudentAccess(502, 0);
    }
  }

  public record StudentAccess(int status, long studentId) {
  }
}

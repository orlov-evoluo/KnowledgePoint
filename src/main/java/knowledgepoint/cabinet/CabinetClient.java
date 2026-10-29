package knowledgepoint.cabinet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class CabinetClient {

  private final String upstream;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public CabinetClient(@Value("${knowledgepoint.upstream}") String upstream) {
    this.upstream = upstream.endsWith("/") ? upstream.substring(0, upstream.length() - 1) : upstream;
  }

  public CabinetResponse get(String pathAndQuery, String token) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(upstream + pathAndQuery))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", token)
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    return new CabinetResponse(response.statusCode(), response.body());
  }

  public CabinetResponse post(String pathAndQuery, String body, Map<String, String> headers)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(upstream + pathAndQuery))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (headers != null) {
      headers.forEach(builder::header);
    }
    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return new CabinetResponse(response.statusCode(), response.body());
  }
}

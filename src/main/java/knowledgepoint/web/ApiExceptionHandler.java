package knowledgepoint.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler({NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
  public ResponseEntity<String> missing(HttpServletRequest request) {
    return notFound(request.getRequestURI());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<String> method(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/api/")) {
      return ResponseEntity.status(405)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"error\":\"method\"}");
    }
    return ResponseEntity.status(405)
        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
        .body("method");
  }

  private static ResponseEntity<String> notFound(String uri) {
    if (uri.startsWith("/api/")) {
      return ResponseEntity.status(404)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"error\":\"not found\"}");
    }
    return ResponseEntity.status(404)
        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
        .body("not found");
  }
}

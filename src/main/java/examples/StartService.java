package examples;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import skirout.service.AddUserRequest;
import skirout.service.AddUserResponse;
import skirout.service.GetUserRequest;
import skirout.service.GetUserResponse;
import skirout.service.Methods;
import skirout.user.User;

/**
 * Starts a Skir service on http://localhost:8787/myapi
 *
 * <p>Run with: ./gradlew run -PmainClass=examples.StartService
 *
 * <p>Use 'CallService.java' to call this service from another process.
 */
public class StartService {

  /** Custom request metadata that includes both request and response headers. */
  public static class RequestMetadata {
    final Map<String, String> requestHeaders;
    final Map<String, String> responseHeaders;

    RequestMetadata(Map<String, String> requestHeaders, Map<String, String> responseHeaders) {
      this.requestHeaders = requestHeaders;
      this.responseHeaders = responseHeaders;
    }
  }

  /** Implementation of the service methods. */
  public static class ServiceImpl {
    private final Map<Integer, User> idToUser = new HashMap<>();

    public GetUserResponse getUser(GetUserRequest request, RequestMetadata metadata) {
      final int userId = request.userId();
      final User user = idToUser.get(userId);
      return GetUserResponse.partialBuilder().setUser(Optional.ofNullable(user)).build();
    }

    public AddUserResponse addUser(AddUserRequest request, RequestMetadata metadata) {
      final User user = request.user();
      if (user.userId() == 0) {
        throw new IllegalArgumentException("invalid user id");
      }
      System.out.println("Adding user: " + user);
      idToUser.put(user.userId(), user);

      // Example of using request/response headers
      final String fooHeader = metadata.requestHeaders.getOrDefault("x-foo", "");
      metadata.responseHeaders.put("x-bar", fooHeader.toUpperCase());

      return AddUserResponse.DEFAULT;
    }
  }

  public static void main(String[] args) throws IOException {
    final ServiceImpl serviceImpl = new ServiceImpl();

    // Build the Skir service with custom metadata
    final var skirService =
        build.skir.service.Service.Companion.builder(
                (httpHeaders) -> {
                  Map<String, String> requestHeaders = new HashMap<>();
                  httpHeaders
                      .map()
                      .forEach(
                          (key, values) -> {
                            if (!values.isEmpty()) {
                              requestHeaders.put(key.toLowerCase(), values.get(0));
                            }
                          });
                  Map<String, String> responseHeaders = new HashMap<>();
                  return new RequestMetadata(requestHeaders, responseHeaders);
                })
            .addMethod(
                Methods.ADD_USER, (req, meta, continuation) -> serviceImpl.addUser(req, meta))
            .addMethod(
                Methods.GET_USER, (req, meta, continuation) -> serviceImpl.getUser(req, meta))
            .build();

    // Create HTTP server
    final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8787), 0);

    // Root handler
    server.createContext(
        "/",
        exchange -> {
          final String response = "Hello, World!";
          exchange.sendResponseHeaders(200, response.length());
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
          }
        });

    // API handler
    server.createContext(
        "/myapi",
        exchange -> {
          try {
            System.out.println(
                "Request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

            // Read request body
            final String requestBody;
            if ("POST".equals(exchange.getRequestMethod())) {
              requestBody =
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            } else {
              // For GET requests, use the query string
              final String query = exchange.getRequestURI().getQuery();
              requestBody = query != null ? URLDecoder.decode(query, StandardCharsets.UTF_8) : "";
            }

            // Convert headers to the format expected by Service
            final java.net.http.HttpHeaders httpHeaders =
                java.net.http.HttpHeaders.of(exchange.getRequestHeaders(), (name, value) -> true);

            // Handle the request (using runBlocking since handleRequest is a suspend function)
            final build.skir.service.Service.RawResponse rawResponse =
                kotlinx.coroutines.BuildersKt.runBlocking(
                    kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) ->
                        skirService.handleRequest(
                            requestBody,
                            httpHeaders,
                            build.skir.UnrecognizedValuesPolicy.KEEP,
                            continuation));

            // Send response
            exchange.getResponseHeaders().set("Content-Type", rawResponse.contentType());

            System.out.println("Raw response data: " + rawResponse.data());
            final byte[] responseBytes = rawResponse.data().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(rawResponse.statusCode(), responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(responseBytes);
            }
          } catch (IOException e) {
            throw e;
          } catch (InterruptedException e) {
            System.err.println("Request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            final String errorResponse = "Request interrupted";
            final byte[] errorBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(errorBytes);
            }
          } catch (RuntimeException e) {
            System.err.println("Error handling request: " + e.getMessage());
            final String errorResponse = "Server error: " + e.getMessage();
            final byte[] errorBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(errorBytes);
            }
          }
        });

    server.setExecutor(null); // creates a default executor
    server.start();
    System.out.println("Serving at http://localhost:8787");
    System.out.println("API endpoint: http://localhost:8787/myapi");
    System.out.println("Press Ctrl+C to stop the server");
  }
}

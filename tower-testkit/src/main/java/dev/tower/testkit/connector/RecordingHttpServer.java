package dev.tower.testkit.connector;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

/**
 * A real HTTP server on a loopback port that remembers what it was asked.
 *
 * <p>A real server rather than a mocked client, because what a Connector test is
 * mostly about is what Tower does with the answers a vendor actually gives — a
 * 404 that means two different things, a login page returned with HTTP 200, a
 * body that is not JSON. A mocked client can only return what the test already
 * assumed, and so can only confirm the assumption.
 *
 * <p>It records every request rather than only serving them, because the
 * read-only claim (ADR-001, CM-01, FR-036) is not something a Connector can be
 * asked about. The architecture tests prove statically that no write path is
 * named; this is the other half, observed from the far side of the socket.
 *
 * <p>Lives in {@code dev.tower.testkit} and deliberately not in
 * {@code dev.tower.connector}: {@code connectors_expose_no_write_operation}
 * forbids classes in that package from calling any method named {@code write},
 * and serving a response body calls {@link OutputStream#write}. The rule is
 * worth more than the naming symmetry.
 */
public final class RecordingHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

    private RecordingHttpServer(HttpServer server) {
        this.server = server;
    }

    /** Starts one on a port the operating system chooses. */
    public static RecordingHttpServer started() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RecordingHttpServer recording = new RecordingHttpServer(server);
            server.createContext("/", recording::handle);
            server.start();
            return recording;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the recording server.", e);
        }
    }

    private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("Authorization")));

        // Absent by default, and 404 rather than 500: a path nobody canned is
        // an issue the tracker does not have, which is the case most of these
        // tests are about.
        int status = statuses.getOrDefault(path, bodies.containsKey(path) ? 200 : 404);
        byte[] body = bodies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** Where a Connector should be pointed. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Answers this path with 200 and the given body. */
    public void answer(String path, String body) {
        answer(path, 200, body);
    }

    public void answer(String path, int status, String body) {
        bodies.put(path, body);
        statuses.put(path, status);
    }

    /** Answers this path with a status and an empty JSON object. */
    public void fail(String path, int status) {
        answer(path, status, "{}");
    }

    /** Every request, in the order they arrived. */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void forgetRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * @param authorization the header as presented, or null when none was — the
     *                      distinction matters, because sending an empty
     *                      credential turns an anonymous read into a refused one
     */
    public record Request(String method, String path, String query, String authorization) {
    }
}

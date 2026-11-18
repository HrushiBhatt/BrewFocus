package io.github.hrushibhatt.brewfocus.support;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A browser's {@code EventSource} on {@code /api/events}: reads "event:" and "data:" lines on a
 * background thread, so a test can wait for the next event with a timeout.
 */
public final class EventStream implements AutoCloseable {

    /** One server-sent event. {@code data} is parsed JSON, so a broken payload fails the test. */
    public record Event(String type, JsonNode data) {}

    private static final Event END = new Event("end", null);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LinkedBlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final InputStream body;
    private final int status;

    public EventStream(Browser browser) throws Exception {
        HttpRequest request = browser.request("/api/events").header("Accept", "text/event-stream").GET().build();
        HttpResponse<InputStream> response = browser.send(request, HttpResponse.BodyHandlers.ofInputStream());
        this.status = response.statusCode();
        this.body = response.body();
        Thread.ofVirtual().start(this::read);
    }

    public int status() {
        return status;
    }

    private void read() {
        try (var lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String type = null;
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.startsWith("event:")) {
                    type = line.substring(6).trim();
                } else if (line.startsWith("data:") && type != null) {
                    events.add(new Event(type, JSON.readTree(line.substring(5))));
                    type = null;
                }
            }
        } catch (Exception closed) {
            // the stream ended or was closed by the test
        } finally {
            events.add(END);
        }
    }

    /** The next event of {@code type}, skipping others. Fails if the stream ends or nothing comes in time. */
    public Event await(String type, Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (true) {
            Event e = events.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (e == null) {
                throw new AssertionError("no " + type + " event within " + within);
            }
            if (e == END) {
                throw new AssertionError("the stream ended before a " + type + " event");
            }
            if (e.type().equals(type)) {
                return e;
            }
        }
    }

    /** True once the server has closed the stream, waiting up to {@code within}. */
    public boolean endsWithin(Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        Event e;
        while ((e = events.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) != null) {
            if (e == END) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws Exception {
        body.close();
    }
}

package com.nada;

import com.fasterxml.jackson.databind.JsonNode;
import com.nada.service.PeopleService;
import com.nada.util.JsonUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Full HTTP tests. A fresh server (fresh in-memory state, free port) per test. */
class ApiIntegrationTest {

    private HttpServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = Application.createServer(0, new PeopleService());
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    // ---- helpers ----

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json; charset=UTF-8");
            b.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private int person(String name) throws Exception {
        String json = JsonUtil.mapper().createObjectNode().put("name", name).toString();
        HttpResponse<String> r = send("POST", "/people", json);
        assertEquals(201, r.statusCode());
        return JsonUtil.mapper().readTree(r.body()).get("id").asInt();
    }

    private HttpResponse<String> know(Object a, Object b) throws Exception {
        return send("POST", "/knows", "{\"a\":" + a + ",\"b\":" + b + "}");
    }

    private JsonNode json(HttpResponse<String> r) throws Exception {
        return JsonUtil.mapper().readTree(r.body());
    }

    /** Builds chain 1-2-...-n and returns nothing; ids are 1..n. */
    private void chain(int n) throws Exception {
        for (int i = 0; i < n; i++) person("P" + (i + 1));
        for (int i = 1; i < n; i++) assertEquals(204, know(i, i + 1).statusCode());
    }

    // ---- POST /people ----

    @Test
    void createPersonReturns201WithIdAndName() throws Exception {
        HttpResponse<String> r = send("POST", "/people", "{\"name\":\"Asha\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").get().startsWith("application/json"));
        JsonNode n = json(r);
        assertEquals(1, n.get("id").asInt());
        assertEquals("Asha", n.get("name").asText());
    }

    @Test
    void createPersonAssignsUniqueIds() throws Exception {
        assertNotEquals(person("A"), person("B"));
    }

    @Test
    void createPersonRejectsBadNames() throws Exception {
        for (String body : new String[]{
                "{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
                "{\"name\":123}", "{\"name\":[\"x\"]}", "[]", "\"Asha\"", ""}) {
            assertEquals(400, send("POST", "/people", body).statusCode(), body);
        }
    }

    @Test
    void createPersonRejectsMalformedJson() throws Exception {
        HttpResponse<String> r = send("POST", "/people", "{\"name\":");
        assertEquals(400, r.statusCode());
        assertNotNull(json(r).get("error"));
        assertEquals(400, send("POST", "/people", "not json").statusCode());
    }

    @Test
    void unicodeNameSurvivesRoundTrip() throws Exception {
        String name = "आशा 😀 Zoë";
        int id = person(name);
        int other = person("Other");
        know(id, other);
        // creation response
        HttpResponse<String> r = send("POST", "/people", JsonUtil.mapper().createObjectNode().put("name", name).toString());
        assertEquals(name, json(r).get("name").asText());
        // and as returned in contacts
        JsonNode contacts = json(send("GET", "/people/" + other + "/contacts", null));
        assertEquals(name, contacts.get(0).get("name").asText());
    }

    // ---- POST /knows ----

    @Test
    void knowsReturns204WithEmptyBodyAndIsMutual() throws Exception {
        int a = person("A"), b = person("B");
        HttpResponse<String> r = know(a, b);
        assertEquals(204, r.statusCode());
        assertEquals("", r.body());

        JsonNode ca = json(send("GET", "/people/" + a + "/contacts", null));
        JsonNode cb = json(send("GET", "/people/" + b + "/contacts", null));
        assertEquals(1, ca.size());
        assertEquals(b, ca.get(0).get("id").asInt());
        assertEquals(1, cb.size());
        assertEquals(a, cb.get(0).get("id").asInt());
    }

    @Test
    void repeatedKnowsDoesNotDuplicate() throws Exception {
        int a = person("A"), b = person("B");
        assertEquals(204, know(a, b).statusCode());
        assertEquals(204, know(a, b).statusCode());
        assertEquals(204, know(b, a).statusCode());
        assertEquals(1, json(send("GET", "/people/" + a + "/contacts", null)).size());
        assertEquals(1, json(send("GET", "/people/" + b + "/contacts", null)).size());
    }

    @Test
    void selfConnectionIs400() throws Exception {
        int a = person("A");
        assertEquals(400, know(a, a).statusCode());
    }

    @Test
    void unknownPeopleInKnowsAre404() throws Exception {
        int a = person("A");
        assertEquals(404, know(a, 99).statusCode());
        assertEquals(404, know(99, a).statusCode());
        assertEquals(404, know(98, 99).statusCode());
    }

    @Test
    void invalidKnowsBodiesAre400() throws Exception {
        person("A");
        person("B");
        for (String body : new String[]{
                "{}", "{\"a\":1}", "{\"b\":2}", "{\"a\":null,\"b\":2}", "{\"a\":\"1\",\"b\":2}",
                "{\"a\":1.5,\"b\":2}", "{\"a\":0,\"b\":2}", "{\"a\":-1,\"b\":2}",
                "{\"a\":99999999999,\"b\":2}", "{\"a\":1,", "", "[]"}) {
            assertEquals(400, send("POST", "/knows", body).statusCode(), body);
        }
    }

    // ---- GET /people/{id}/contacts ----

    @Test
    void personWithNoContactsGetsEmptyArray() throws Exception {
        int a = person("A");
        HttpResponse<String> r = send("GET", "/people/" + a + "/contacts", null);
        assertEquals(200, r.statusCode());
        assertTrue(json(r).isArray());
        assertEquals(0, json(r).size());
    }

    @Test
    void contactsMalformedIdIs400() throws Exception {
        for (String id : new String[]{"abc", "0", "-1", "1.5", "99999999999", "%20"}) {
            assertEquals(400, send("GET", "/people/" + id + "/contacts", null).statusCode(), id);
        }
    }

    @Test
    void contactsUnknownPersonIs404() throws Exception {
        assertEquals(404, send("GET", "/people/42/contacts", null).statusCode());
    }

    // ---- GET /path ----

    @Test
    void threeHopPathSucceeds() throws Exception {
        chain(4);
        HttpResponse<String> r = send("GET", "/path?from=1&to=4", null);
        assertEquals(200, r.statusCode());
        JsonNode n = json(r);
        assertTrue(n.get("connected").asBoolean());
        assertEquals(3, n.get("hops").asInt());
        assertEquals(
                JsonUtil.mapper().readTree("""
                        {
                          "connected": true,
                          "hops": 3,
                          "path": [
                            {"id": 1, "name": "P1"},
                            {"id": 2, "name": "P2"},
                            {"id": 3, "name": "P3"},
                            {"id": 4, "name": "P4"}
                          ]
                        }
                        """),
                n);
    }

    @Test
    void fourHopPathIsNotConnected() throws Exception {
        chain(5);
        HttpResponse<String> r = send("GET", "/path?from=1&to=5", null);
        assertEquals(200, r.statusCode());
        assertEquals("{\"connected\":false}", r.body());
    }

    @Test
    void shortestPathChosenWhenSeveralExist() throws Exception {
        person("Asha");
        person("Rohan");
        person("Meera");
        person("Tara");
        person("Kabir");
        know(1, 2); know(2, 3); know(3, 4); know(4, 5); // 4 hops
        know(1, 3); know(3, 5);                         // 2 hops
        JsonNode n = json(send("GET", "/path?from=1&to=5", null));
        assertEquals(
                JsonUtil.mapper().readTree("""
                        {
                          "connected": true,
                          "hops": 2,
                          "path": [
                            {"id": 1, "name": "Asha"},
                            {"id": 3, "name": "Meera"},
                            {"id": 5, "name": "Kabir"}
                          ]
                        }
                        """),
                n);
    }

    @Test
    void disconnectedPeopleAreNotConnected() throws Exception {
        person("A");
        person("B");
        HttpResponse<String> r = send("GET", "/path?from=1&to=2", null);
        assertEquals(200, r.statusCode());
        assertEquals("{\"connected\":false}", r.body());
    }

    @Test
    void pathToSelfHasZeroHops() throws Exception {
        int a = person("A");
        JsonNode n = json(send("GET", "/path?from=" + a + "&to=" + a, null));
        assertEquals(
                JsonUtil.mapper().readTree("""
                        {
                          "connected": true,
                          "hops": 0,
                          "path": [{"id": 1, "name": "A"}]
                        }
                        """),
                n);
    }

    @Test
    void invalidPathParametersAre400() throws Exception {
        person("A");
        person("B");
        for (String q : new String[]{
                "", "?from=1", "?to=1", "?from=&to=1", "?from=1&to=", "?from=%20&to=1",
                "?from=a&to=1", "?from=1&to=b", "?from=0&to=1", "?from=1&to=-2",
                "?from=1.5&to=2", "?from=99999999999&to=1"}) {
            assertEquals(400, send("GET", "/path" + q, null).statusCode(), q);
        }
    }

    @Test
    void unknownPathEndpointsAre404NotDisconnected() throws Exception {
        person("A");
        assertEquals(404, send("GET", "/path?from=1&to=99", null).statusCode());
        assertEquals(404, send("GET", "/path?from=99&to=1", null).statusCode());
        assertEquals(404, send("GET", "/path?from=98&to=99", null).statusCode());
    }

    // ---- routing ----

    @Test
    void unknownRouteIs404AndWrongMethodIs405() throws Exception {
        assertEquals(404, send("GET", "/nope", null).statusCode());
        assertEquals(405, send("GET", "/people", null).statusCode());
        assertEquals(405, send("GET", "/knows", null).statusCode());
        assertEquals(405, send("POST", "/path?from=1&to=2", "{}").statusCode());
        assertEquals(405, send("POST", "/people/1/contacts", "{}").statusCode());
    }

    @Test
    void stateIsIsolatedBetweenTests() throws Exception {
        // a fresh server per test means the first created person is always id 1
        assertEquals(1, person("Fresh"));
    }
}

package com.nada.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nada.model.Person;
import com.nada.service.PeopleService;
import com.nada.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * Single router for every endpoint. Registered on "/" so that unknown routes
 * get a JSON 404 instead of the JDK's default plain-text response.
 */
public class PeopleHandler implements HttpHandler {

    private static final int MAX_HOPS = 3;
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    private final PeopleService service;

    public PeopleHandler(PeopleService service) {
        this.service = service;
    }

    /** Thrown for any client error; carries the HTTP status. */
    private static final class HttpError extends RuntimeException {
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                route(exchange);
            } catch (HttpError e) {
                sendJson(exchange, e.status, error(e.getMessage()));
            } catch (NoSuchElementException e) {
                sendJson(exchange, 404, error(e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, error("internal server error"));
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] seg = path.split("/");   // "/people/1/contacts" -> ["", "people", "1", "contacts"]

        if (path.equals("/people")) {
            requireMethod(method, "POST");
            createPerson(exchange);
        } else if (path.equals("/knows")) {
            requireMethod(method, "POST");
            connect(exchange);
        } else if (seg.length == 4 && seg[1].equals("people") && seg[3].equals("contacts")) {
            requireMethod(method, "GET");
            contacts(exchange, seg[2]);
        } else if (path.equals("/path")) {
            requireMethod(method, "GET");
            shortestPath(exchange);
        } else {
            throw new HttpError(404, "not found");
        }
    }

    // ---- endpoints -------------------------------------------------------

    private void createPerson(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonObject(exchange);
        JsonNode name = body.get("name");
        if (name == null || !name.isTextual() || name.asText().isBlank()) {
            throw new HttpError(400, "name is required and must be a non-blank string");
        }
        Person person = service.createPerson(name.asText());
        sendJson(exchange, 201, JsonUtil.mapper().writeValueAsString(person));
    }

    private void connect(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonObject(exchange);
        int a = jsonId(body, "a");
        int b = jsonId(body, "b");
        if (a == b) {
            throw new HttpError(400, "a person cannot know themselves");
        }
        service.connect(a, b);                // NoSuchElementException -> 404
        sendNoContent(exchange);
    }

    private void contacts(HttpExchange exchange, String rawId) throws IOException {
        int id = parseId(rawId, "id");
        List<Person> contacts = service.getContacts(id);   // unknown -> 404
        sendJson(exchange, 200, JsonUtil.mapper().writeValueAsString(contacts));
    }

    private void shortestPath(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int from = parseId(query.get("from"), "from");
        int to = parseId(query.get("to"), "to");

        var result = service.findShortestPath(from, to, MAX_HOPS);   // unknown -> 404

        ObjectNode out = JsonUtil.mapper().createObjectNode();
        if (result.isEmpty()) {
            out.put("connected", false);
        } else {
            List<Person> people = result.get();
            out.put("connected", true);
            out.put("hops", people.size() - 1);
            ArrayNode ids = out.putArray("path");
            people.forEach(p -> ids.add(p.getId()));
        }
        sendJson(exchange, 200, JsonUtil.mapper().writeValueAsString(out));
    }

    // ---- parsing / validation -------------------------------------------

    private static void requireMethod(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new HttpError(405, "method not allowed");
        }
    }

    private JsonNode readJsonObject(HttpExchange exchange) throws IOException {
        String text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new HttpError(400, "request body is required");
        }
        try {
            JsonNode node = JsonUtil.mapper().readTree(text);
            if (node == null || !node.isObject()) {
                throw new HttpError(400, "request body must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new HttpError(400, "invalid JSON");
        }
    }

    /** Reads a positive integer field; rejects missing, null, strings, decimals. */
    private static int jsonId(JsonNode body, String field) {
        JsonNode n = body.get(field);
        if (n == null || !n.isIntegralNumber() || !n.canConvertToInt() || n.asInt() <= 0) {
            throw new HttpError(400, "'" + field + "' must be a positive integer");
        }
        return n.asInt();
    }

    /** Parses a positive integer from a path segment or query value. */
    private static int parseId(String raw, String field) {
        if (raw == null || !DIGITS.matcher(raw).matches()) {
            throw new HttpError(400, "'" + field + "' must be a positive integer");
        }
        try {
            int id = Integer.parseInt(raw);
            if (id <= 0) {
                throw new HttpError(400, "'" + field + "' must be a positive integer");
            }
            return id;
        } catch (NumberFormatException e) {   // too large for int
            throw new HttpError(400, "'" + field + "' must be a positive integer");
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.putIfAbsent(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8).trim());
        }
        return map;
    }

    // ---- responses -------------------------------------------------------

    private static String error(String message) {
        ObjectNode node = JsonUtil.mapper().createObjectNode();
        node.put("error", message);
        return node.toString();
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);   // -1 = no body
    }
}

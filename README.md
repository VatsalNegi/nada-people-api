# Nada People API

A small in-memory REST API for people and mutual connections, built with the JDK's built-in `com.sun.net.httpserver.HttpServer`, Jackson, and JUnit 5.

**Technology:** Java 17, Maven, Jackson, JUnit 5.

## Prerequisites

- JDK 17
- Maven 3.9 or later

On Windows, Maven can be run using its full path if it is not configured in `PATH`.

## Build, Test, and Run

Open PowerShell in the project directory.

```powershell
$mvn = "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16\bin\mvn.cmd"

# Compile and run automated tests
& $mvn clean test

# Start the API on the default port (8080)
& $mvn exec:java "-Dexec.mainClass=com.nada.Application"
```

Keep the server terminal open while making requests. Open a second terminal to test the API.

If port 8080 is busy, identify the process before stopping it:

```powershell
netstat -ano | findstr :8080
tasklist /FI "PID eq <pid>"
```

Replace `<pid>` with the actual process ID. Stop it only after confirming it belongs to your previous application run.

## API Endpoints

All JSON responses use `application/json; charset=UTF-8`. The `POST /knows` endpoint returns HTTP 204 with no response body.

Errors use a JSON response such as `{"error":"..."}`.

| Method and path | Request example | Success | Errors |
|---|---|---|---|
| `POST /people` | `{"name":"Asha"}` | `201 Created` with the created person's ID and name | `400 Bad Request` for invalid input or malformed JSON |
| `POST /knows` | `{"a":1,"b":2}` | `204 No Content` | `400 Bad Request` for invalid IDs or self-connections; `404 Not Found` for unknown people |
| `GET /people/{id}/contacts` | No body | `200 OK` with the contact list; `[]` if there are no contacts | `400 Bad Request` for an invalid ID; `404 Not Found` for an unknown person |
| `GET /path?from=1&to=5` | No body | `200 OK` with the path and hop count, or `{"connected":false}` if no path within the limit exists | `400 Bad Request` for invalid query parameters; `404 Not Found` for unknown people |

### Shortest-path behavior

- Uses breadth-first search (BFS) to find the shortest path.
- Maximum allowed path length is 3 hops.
- A path of 3 hops succeeds; a path requiring 4 hops returns `{"connected":false}`.
- A person queried against themselves has a path of zero hops, for example `{"connected":true,"hops":0,"path":[1]}`.
- Unknown people return `404 Not Found` rather than being treated as disconnected.

The exact `/path` response structure must match the original assignment specification.

## Example Requests

Create a person:

```powershell
curl.exe -i -X POST "http://localhost:8080/people" -H "Content-Type: application/json" -d '{\"name\":\"Asha\"}'
```

Find a path:

```powershell
curl.exe -i "http://localhost:8080/path?from=1&to=2"
```

## Design Decisions

- **HTTP routing:** A single handler registered at `/` routes requests to the appropriate endpoint and provides consistent error handling.
- **Service layer:** `PeopleService` manages the in-memory graph and connection operations. The HTTP handler handles request parsing, validation, and HTTP responses.
- **Graph representation:** Adjacency sets represent mutual connections and prevent duplicate contacts.
- **Shortest path:** BFS finds the shortest path. Nodes at depth 3 are not expanded further.
- **Validation:** Jackson's tree model is used to validate JSON fields and types. Names must be non-blank strings, and IDs must be valid positive integers.
- **Concurrency:** Service methods are synchronized to protect the in-memory graph from concurrent modifications.
- **Testing:** Unit tests cover service behavior. HTTP integration tests use an ephemeral port to avoid depending on port 8080.

## Limitations and Future Improvements

- Data is stored in memory and is lost when the application restarts.
- A single service lock limits concurrency.
- There is no authentication, pagination, person deletion, or request-size limit.
- BFS can be optimized with parent pointers if the graph or search depth grows.

## AI Disclosure

Claude was used to review the project, assist with the HTTP layer, and help write tests and this README. I reviewed the implementation and am responsible for understanding and explaining the submitted code.

# Nada People API

A small in-memory REST API for people and mutual connections, built with the JDK's
built-in `com.sun.net.httpserver.HttpServer`, Jackson, and JUnit 5. Java 17, Maven.

## Prerequisites
- JDK 17
- Maven 3.9+ (on Windows, e.g. `C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16\bin\mvn.cmd`)

## Build, test, run (Windows PowerShell)
```powershell
$mvn = "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16\bin\mvn.cmd"
& $mvn clean test
& $mvn exec:java "-Dexec.mainClass=com.nada.Application"      # listens on 8080
& $mvn exec:java "-Dexec.mainClass=com.nada.Application" "-Dexec.args=9090"   # custom port
```
If port 8080 is busy: `netstat -ano | findstr :8080`, then `tasklist /FI "PID eq <pid>"` to
confirm it is *your* old run before stopping it (`Stop-Process -Id <pid>`).

## Endpoints
All responses are `application/json; charset=UTF-8`, except 204 (no body). Errors look like `{"error":"..."}`.

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `POST /people` | `{"name":"Asha"}` | `201` `{"id":1,"name":"Asha"}` | `400` missing/null/blank/non-string name, malformed JSON |
| `POST /knows` | `{"a":1,"b":2}` | `204` no body (idempotent) | `400` missing/invalid ids or `a==b`; `404` unknown person |
| `GET /people/{id}/contacts` | – | `200` `[{"id":2,"name":"..."}]` (`[]` if none) | `400` bad id; `404` unknown |
| `GET /path?from=1&to=5` | – | `200` `{"connected":true,"hops":2,"path":[1,3,5]}` or `{"connected":false}` | `400` missing/blank/non-numeric/zero/negative ids; `404` unknown person |

Unknown routes return `404`; wrong method on a known route returns `405`.
Same-person path: `{"connected":true,"hops":0,"path":[1]}`. Max 3 hops (3 succeeds, 4 → `connected:false`).

```powershell
curl.exe -i -X POST http://localhost:8080/people -H "Content-Type: application/json" -d '{\"name\":\"Asha\"}'
curl.exe -i "http://localhost:8080/path?from=1&to=2"
```

## Design decisions
- **One router handler on `/`** so every unknown path/method gets a consistent JSON error.
- **`PeopleService`** owns the graph (adjacency sets) and BFS; the handler only parses, validates, and maps results to HTTP.
  The service throws `NoSuchElementException` for unknown people, which the handler maps to 404 — so unknown people are never reported as "disconnected".
- **BFS** with a visited set returns the first (hence shortest) path; nodes at depth 3 are not expanded.
- **Validation** uses Jackson's tree model so ids must be real JSON integers (`"1"` and `1.5` are rejected), names must be non-blank strings.
- **Concurrency**: all service methods are `synchronized`; simple and correct for this scale.
- **Tests**: service unit tests plus HTTP integration tests that start a fresh server on an ephemeral port per test (no shared state, no port 8080).
- The assignment PDF was not available when this was written; the success response shape for `/path` (`connected`, `hops`, `path` as ids) is an assumption — adjust if the PDF differs.

## Limitations / future work
In-memory only (data lost on restart); single global lock; no pagination, auth, request-size limit, or person deletion;
BFS builds a path per queue entry (fine for 3 hops; use parent pointers for larger graphs).

## AI disclosure
An AI assistant (Claude) was used to review the project, rewrite the HTTP layer, and write tests and this README.
I reviewed and can explain the code.

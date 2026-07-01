# TaskRunner

A small, honest background-job service written in Java 17 + Spring Boot, backed by MySQL, and shipped with a `docker-compose.yml` so you can bring the whole thing up with one command. Jobs come in over HTTP, get queued, picked up by a pool of worker threads, retried with exponential backoff if they fail, and you can check their status at any point.

Base URL when running locally: **http://localhost:8080**

---

## Problem

Most "I just need to run something in the background" problems don't need Kafka, Airflow, or a six-service microservice setup. But `new Thread(() -> ...).start()` inside your request handler is also not a real answer — it has no retries, no visibility, no backpressure, and the work dies when the JVM does.

This project sits in that middle ground. Real scenarios it actually solves:

- Kicking off a long export from an HTTP request without making the client wait
- Calling a flaky third-party API that needs to be retried a few times before you give up
- Generating reports, PDFs, thumbnails — anything CPU-heavy you don't want running in the request thread
- Anywhere you'd reach for "a queue" but don't want to operate Redis/RabbitMQ/Kafka yet

You POST a job, you get an id back, you poll the status endpoint until it's `SUCCEEDED` or `FAILED`. That's the whole mental model.

---

## Design Decisions

**Java threads + `BlockingQueue`, not Kafka.**
The brief was explicitly "show raw Java concurrency", and honestly for the throughput most apps actually have, `ExecutorService` + `LinkedBlockingQueue` is the right tool. No broker to operate, no extra network hop, no consumer group rebalancing. The whole worker pool is ~120 lines of code that any Java developer can read in one sitting.

**MySQL for job state, in-memory queue for ordering.**
Two different concerns, two different stores. MySQL is the source of truth — every status transition is written there, so a crash doesn't lose work. The `LinkedBlockingQueue` only holds *ids* of jobs ready to run; on boot we re-seed it from the database, picking up anything that was `QUEUED`, `RETRY_PENDING`, or stuck `RUNNING` from a previous crash. This is way simpler than treating MySQL as a queue (no `SELECT ... FOR UPDATE SKIP LOCKED` gymnastics) while still being durable.

**Exponential backoff over fixed-delay retries.**
If a downstream API is down, hammering it every 500ms makes things worse. Backoff doubles on each attempt (500ms → 1s → 2s → 4s) which gives the dependency room to recover. Retries are scheduled on a separate `ScheduledExecutorService` so a sleeping retry doesn't burn a worker thread.

**Handler registry pattern.**
Adding a new job type = write a class that implements `JobHandler`, annotate `@Component`, done. Spring auto-discovers it, the registry wires it up by `type()`, no central switch statement to maintain. Makes unit testing each handler trivial.

**Spring Boot over a hand-rolled HTTP server.**
Boring on purpose. Every Java dev can read it, dependency injection makes the worker/registry/repo wiring obvious, and JPA + the MySQL driver is one dependency line. The "magic" is contained to standard Spring stuff; the queue/worker logic is plain Java.

---

## What I'd Do Differently at Scale

This is what would change once a single box stops being enough:

- **Pull the queue out of the JVM.** The in-memory `BlockingQueue` works great inside one process. The moment you want to run two or more app instances, they each have their own queue and can't share work. At that point I'd either (a) move to MySQL-as-a-queue with `SELECT ... FOR UPDATE SKIP LOCKED` (works to a few hundred jobs/sec) or (b) put Redis or RabbitMQ in front for higher throughput and proper features like delayed queues and dead-letter channels.
- **Split API from workers.** Right now a CPU-heavy job in a worker thread can spike GC pauses and hurt API latency. In production I'd run them as two deployments sharing the same DB so workers can scale horizontally without touching the API tier.
- **Real observability.** Micrometer + Prometheus for queue depth, retry rate per job type, average duration, and worker utilization. Structured JSON logs with a correlation id per job so you can grep one job's full lifecycle.
- **Dead-letter handling.** Today after `maxAttempts` fails the job just sits in `FAILED`. A real system needs a DLQ table + an alert + a "replay this job" admin endpoint.
- **Priorities and rate limits.** A "send password reset email" should jump the line ahead of a batch of nightly report jobs. And some external APIs need per-job-type rate limiting.
- **Versioned payload schemas.** Right now payloads are opaque strings. In production I'd validate them per-type (Jackson + JSR-303) and version the schema so handler upgrades don't break in-flight jobs.

---

## Known Limitations

Being upfront about what this isn't:

- **The queue is not shared across instances.** Each process has its own `LinkedBlockingQueue`. Running two copies behind a load balancer means each instance only sees jobs *it* enqueued — not a real scale-out story until the queue moves to Redis or the DB.
- **No authentication on the HTTP endpoints.** Anyone who can reach `:8080` can submit jobs. Fine for an internal service behind a VPC, not fine on the public internet. Add an API key filter before exposing it.
- **No cancellation.** Once a job is `RUNNING`, there's no way to stop it. You'd need cooperative cancellation (a `volatile boolean` the handler polls) to do this properly.
- **FIFO only.** No priority queue, no per-tenant fairness. A flood of low-priority jobs can starve a high-priority one.
- **Retry policy is global.** `taskrunner.max-retries` applies to every job type. A real system would let each handler declare its own retry policy (max attempts, backoff curve, retriable exception types).
- **Payloads are opaque strings.** No validation. A malformed payload is only caught when a worker tries to use it.
- **No web UI.** Status checks are raw JSON via curl. Building a tiny admin page would be a nice next step.

---

## Quick Start

### Option A — Docker (easiest)

```bash
docker compose up --build
```

That starts MySQL on `localhost:3306` and the app on `http://localhost:8080`. The schema is created automatically by Hibernate on first boot.

### Option B — Local Maven + your own MySQL

```bash
mvn spring-boot:run
```

Make sure MySQL is reachable at `localhost:3306` with user `root` / password `root`, or override via env vars:

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/taskrunner \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=root \
mvn spring-boot:run
```

### Option C — No database at all (H2 in-memory)

Handy if you just want to poke at the API:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

---

## API

### Submit a job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"echo","payload":"hello world"}'
```

Response:
```json
{
  "id": "8f2c...",
  "status": "QUEUED",
  "statusUrl": "http://localhost:8080/api/jobs/8f2c..."
}
```

### Check status

```bash
curl http://localhost:8080/api/jobs/8f2c...
```

### List recent jobs + queue depth

```bash
curl "http://localhost:8080/api/jobs?limit=25"
```

### Health check

```bash
curl http://localhost:8080/health
```

### Built-in job types (for demo)

| type    | what it does                                         |
| ------- | ---------------------------------------------------- |
| `echo`  | returns payload unchanged                            |
| `sleep` | sleeps `payload` milliseconds, returns "slept Xms"   |
| `flaky` | fails ~70% of the time — good for watching retries   |

Try the flaky one a few times and watch the `attempts` counter climb in the status response:

```bash
ID=$(curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"flaky","payload":"x","maxRetries":5}' | jq -r .id)

sleep 3
curl -s http://localhost:8080/api/jobs/$ID | jq
```

---

## Project Layout

```
src/main/java/com/jobrunner/app
├── TaskrunnerApplication.java     # boot entry point
├── api/                            # HTTP layer (JobController, RootController)
├── core/                           # JobHandler interface + HandlerRegistry
├── handlers/                       # EchoHandler, SleepHandler, FlakyHandler
├── model/                          # Job entity + JobStatus enum
├── repo/                           # Spring Data JPA repository
└── worker/                         # JobQueue + WorkerPool (the actual concurrency)
```

If you want to add a job type, drop a new class into `handlers/`, implement `JobHandler`, annotate `@Component`. That's it.

---

## Screenshots

*(Add screenshots here once you've run it locally — e.g. a Postman screenshot of submitting a job, the status response showing retries, and the docker compose terminal output.)*

```
[ screenshot: POST /api/jobs returning 200 + statusUrl ]
[ screenshot: GET /api/jobs/{id} showing attempts=2, status=RETRY_PENDING ]
[ screenshot: docker compose up output with mysql + app healthy ]
```

import { execFileSync } from "node:child_process";
import { expect, test } from "@playwright/test";
import { BACKEND_URL, startBackend, startDatabase, stopAll, stopDatabase } from "../scripts/servers";

/**
 * D-4 targets (spec/requirements.md §2): with 100 000 tickets on PostgreSQL, list/search/filter p95 < 500 ms and
 * ticket details p95 < 200 ms. Latency is measured from a single sequential client against the backend itself
 * (no network hops, no frontend), after a warm-up, so it is the server's own response time on this machine.
 */
const TICKETS = 100_000;
const WARMUP = 30;
const SAMPLES = 200;
const LIST_P95_MS = 500;
const DETAILS_P95_MS = 200;

const SEED_SQL = `
INSERT INTO ticket (title, description, priority, status, assignee, created_at, updated_at,
                    resolved_at, closed_at, cancelled_at, version)
SELECT 'Ticket ' || g || ': ' || (ARRAY['printer','vpn','laptop','email','password','network','monitor','access'])[1 + g % 8] || ' problem',
       'Customer reports that the ' || (ARRAY['printer','vpn','laptop','email','password','network','monitor','access'])[1 + g % 8]
         || ' stopped working after the latest update. ' || repeat('Steps to reproduce and details follow. ', 8) || md5(g::text),
       (ARRAY['LOW','MEDIUM','HIGH','URGENT'])[1 + g % 4],
       s.status,
       CASE WHEN g % 3 = 0 THEN NULL ELSE 'agent' || (g % 25) END,
       t.at, t.at,
       CASE WHEN s.status IN ('RESOLVED','CLOSED') THEN t.at END,
       CASE WHEN s.status = 'CLOSED' THEN t.at END,
       CASE WHEN s.status = 'CANCELLED' THEN t.at END,
       0
FROM generate_series(1, ${TICKETS}) AS g
CROSS JOIN LATERAL (SELECT (ARRAY['OPEN','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED'])[1 + g % 5] AS status) s
CROSS JOIN LATERAL (SELECT now() - make_interval(mins => g) AS at) t;
INSERT INTO ticket_comment (ticket_id, author, body, created_at)
SELECT 1, 'agent' || c, 'Update number ' || c, now() - make_interval(mins => 100 - c) FROM generate_series(1, 60) AS c;
ANALYZE;`;

interface Result {
  scenario: string;
  p50: number;
  p95: number;
  max: number;
  target: number;
}

function psql(sql: string): string {
  return execFileSync("docker", ["exec", "-i", "supportdesk-e2e-postgres", "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1", "-q", "-t"], {
    input: sql,
    encoding: "utf8",
  }).trim();
}

async function measure(scenario: string, path: string, target: number): Promise<Result> {
  const timings: number[] = [];
  for (let i = 0; i < WARMUP + SAMPLES; i++) {
    const start = performance.now();
    const response = await fetch(BACKEND_URL + path);
    await response.arrayBuffer();
    const elapsed = performance.now() - start;
    expect(response.status, `${scenario}: ${path}`).toBe(200);
    if (i >= WARMUP) timings.push(elapsed);
  }
  timings.sort((a, b) => a - b);
  const at = (q: number) => Math.round(timings[Math.min(timings.length - 1, Math.ceil(q * timings.length) - 1)] * 10) / 10;
  return { scenario, p50: at(0.5), p95: at(0.95), max: Math.round(timings.at(-1)! * 10) / 10, target };
}

test.beforeAll(async () => {
  await stopAll();
  await startDatabase();
  await startBackend(); // Flyway creates the schema
  psql(SEED_SQL);
});

test.afterAll(async () => {
  await stopAll();
  stopDatabase();
});

test(`API latency with ${TICKETS.toLocaleString("en")} tickets on PostgreSQL`, async () => {
  expect(Number(psql("SELECT count(*) FROM ticket"))).toBe(TICKETS);
  const middleId = TICKETS / 2;
  const results = [
    await measure("list: first page, newest first", "/api/v1/tickets", LIST_P95_MS),
    await measure("list: last page", `/api/v1/tickets?page=${TICKETS / 20 - 1}`, LIST_P95_MS),
    await measure("list: sort by priority", "/api/v1/tickets?sort=priority,desc", LIST_P95_MS),
    await measure("filter: two statuses", "/api/v1/tickets?status=OPEN&status=IN_PROGRESS", LIST_P95_MS),
    await measure("search: common word", "/api/v1/tickets?q=printer", LIST_P95_MS),
    await measure("search: no match (full scan)", "/api/v1/tickets?q=zzzzzz", LIST_P95_MS),
    await measure("search + filter + sort", "/api/v1/tickets?q=vpn&status=OPEN&sort=updatedAt,desc", LIST_P95_MS),
    await measure("details", `/api/v1/tickets/${middleId}`, DETAILS_P95_MS),
    await measure("comments of a ticket (60)", "/api/v1/tickets/1/comments", DETAILS_P95_MS),
  ];
  console.log(`\n${"scenario".padEnd(34)} ${"p50 ms".padStart(8)} ${"p95 ms".padStart(8)} ${"max ms".padStart(8)}  target p95`);
  for (const r of results) {
    console.log(`${r.scenario.padEnd(34)} ${String(r.p50).padStart(8)} ${String(r.p95).padStart(8)} ${String(r.max).padStart(8)}  < ${r.target} ${r.p95 < r.target ? "PASS" : "FAIL"}`);
  }
  for (const r of results) {
    expect(r.p95, `${r.scenario} p95`).toBeLessThan(r.target);
  }
});

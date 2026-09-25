import { execFileSync, spawn } from "node:child_process";
import { existsSync, mkdirSync, openSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Starts, stops and restarts the system under test as real processes:
 *   browser → Next.js (production build, port 13000) → /api proxy → backend (port 8080).
 *
 * E2E_BACKEND=real (default) runs the Spring Boot jar; E2E_BACKEND=stub runs e2e/stub/contract-stub.mjs, an
 * in-memory implementation of spec/api-contract.md used to validate the tests themselves while the backend
 * is not implemented. Process ids live in .state/ so test workers can restart the backend (journey 13).
 */

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const E2E_DIR = path.resolve(HERE, "..");
export const ROOT = path.resolve(E2E_DIR, "..");
const STATE_DIR = path.join(E2E_DIR, ".state");
const PIDS_FILE = path.join(STATE_DIR, "pids.json");
export const STUB_DATA_FILE = path.join(STATE_DIR, "stub-data.json");

// Must be 8080: the frontend's /api proxy destination is fixed at `next build` time (next.config.ts rewrites read
// BACKEND_URL during the build), so a different port at `next start` is ignored. See the E2E report, finding F-1.
export const BACKEND_PORT = 8080;
export const FRONTEND_PORT = 13000;
export const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`;
export const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;

export const backendKind = (): "real" | "stub" => (process.env.E2E_BACKEND === "stub" ? "stub" : "real");

interface Pids {
  backend?: number;
  frontend?: number;
}

function readPids(): Pids {
  return existsSync(PIDS_FILE) ? (JSON.parse(readFileSync(PIDS_FILE, "utf8")) as Pids) : {};
}

function writePids(pids: Pids) {
  mkdirSync(STATE_DIR, { recursive: true });
  writeFileSync(PIDS_FILE, JSON.stringify(pids));
}

function logFile(name: string): number {
  mkdirSync(STATE_DIR, { recursive: true });
  return openSync(path.join(STATE_DIR, `${name}.log`), "a");
}

function java21(): string {
  if (process.env.JAVA21_HOME) {
    return path.join(process.env.JAVA21_HOME, "bin", "java");
  }
  try {
    const home = execFileSync("/usr/libexec/java_home", ["-v", "21"], { encoding: "utf8" }).trim();
    return path.join(home, "bin", "java");
  } catch {
    return "java";
  }
}

async function waitForHttp(url: string, timeoutMs: number, what: string): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      await fetch(url);
      return;
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
  }
  throw new Error(`[environment] ${what} did not start listening on ${url} within ${timeoutMs} ms`);
}

async function waitForPortClosed(url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      await fetch(url);
      await new Promise((resolve) => setTimeout(resolve, 200));
    } catch {
      return;
    }
  }
  throw new Error(`[environment] ${url} still answering after ${timeoutMs} ms`);
}

function kill(pid: number | undefined) {
  if (!pid) return;
  try {
    process.kill(pid, "SIGTERM");
  } catch {
    // already gone
  }
}

export async function startBackend(): Promise<void> {
  const out = logFile("backend");
  let child;
  if (backendKind() === "stub") {
    child = spawn(process.execPath, [path.join(E2E_DIR, "stub", "contract-stub.mjs")], {
      env: { ...process.env, PORT: String(BACKEND_PORT), STUB_DATA_FILE },
      stdio: ["ignore", out, out],
      detached: true,
    });
  } else {
    const jar = path.join(ROOT, "backend", "build", "libs", "support-desk-backend-0.0.1-SNAPSHOT.jar");
    if (!existsSync(jar)) {
      throw new Error(`[environment] backend jar missing: run ./gradlew bootJar in backend/ (${jar})`);
    }
    child = spawn(java21(), ["-jar", jar, `--server.port=${BACKEND_PORT}`], {
      stdio: ["ignore", out, out],
      detached: true,
    });
  }
  child.unref();
  writePids({ ...readPids(), backend: child.pid });
  await waitForHttp(`${BACKEND_URL}/`, 60_000, `backend (${backendKind()})`);
}

export async function stopBackend(): Promise<void> {
  kill(readPids().backend);
  await waitForPortClosed(`${BACKEND_URL}/`, 20_000);
  writePids({ ...readPids(), backend: undefined });
}

export async function restartBackend(): Promise<void> {
  await stopBackend();
  await startBackend();
}

export async function startFrontend(): Promise<void> {
  const frontendDir = path.join(ROOT, "frontend");
  if (!existsSync(path.join(frontendDir, ".next", "BUILD_ID"))) {
    throw new Error("[environment] frontend production build missing: run npm run build in frontend/");
  }
  const out = logFile("frontend");
  const child = spawn(
    process.execPath,
    [path.join(frontendDir, "node_modules", "next", "dist", "bin", "next"), "start", "-p", String(FRONTEND_PORT)],
    { cwd: frontendDir, env: { ...process.env, BACKEND_URL }, stdio: ["ignore", out, out], detached: true },
  );
  child.unref();
  writePids({ ...readPids(), frontend: child.pid });
  await waitForHttp(`${FRONTEND_URL}/tickets`, 60_000, "frontend");
}

export async function stopAll(): Promise<void> {
  const pids = readPids();
  kill(pids.backend);
  kill(pids.frontend);
  writePids({});
}

export function resetStubData() {
  rmSync(STUB_DATA_FILE, { force: true });
}

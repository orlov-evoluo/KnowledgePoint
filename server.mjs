import { createServer } from "node:http";
import { createRequire } from "node:module";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

const require = createRequire(import.meta.url);
const initSqlJs = require("sql.js");

const PORT = Number(process.env.PORT || 4173);
const UPSTREAM = "https://app.tochka-school.ru/api/v1/lk";
const PAGE = join(import.meta.dirname, "public", "index.html");
const DATA_DIR = join(import.meta.dirname, ".data");
const DB_PATH = join(DATA_DIR, "session.sqlite");

const ALLOWED = [
  /^\/profile$/,
  /^\/children$/,
  /^\/journal\/\d+\/(active|allfinished|finished|archived|cancelled)$/,
  /^\/journal\/\d+\/detail\/\d+$/,
];

mkdirSync(DATA_DIR, { recursive: true });
const SQL = await initSqlJs({
  wasmBinary: readFileSync(require.resolve("sql.js/dist/sql-wasm.wasm")),
});
const db = existsSync(DB_PATH)
  ? new SQL.Database(readFileSync(DB_PATH))
  : new SQL.Database();
db.run(`CREATE TABLE IF NOT EXISTS session (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  token TEXT NOT NULL,
  updated_at TEXT NOT NULL
)`);

function persist() {
  writeFileSync(DB_PATH, Buffer.from(db.export()));
}

function storedToken() {
  const stmt = db.prepare("SELECT token FROM session WHERE id = 1");
  try {
    if (!stmt.step()) return null;
    const value = stmt.getAsObject().token;
    return typeof value === "string" && value.trim() ? value.trim() : null;
  } finally {
    stmt.free();
  }
}

function saveToken(token) {
  const stmt = db.prepare(`INSERT INTO session (id, token, updated_at)
    VALUES (1, ?, ?)
    ON CONFLICT(id) DO UPDATE SET token = excluded.token, updated_at = excluded.updated_at`);
  try {
    stmt.run([token, new Date().toISOString()]);
  } finally {
    stmt.free();
  }
  persist();
}

function clearToken() {
  db.run("DELETE FROM session WHERE id = 1");
  persist();
}

function normalize(raw) {
  const value = String(raw ?? "").trim();
  if (!value) return null;
  return value.toLowerCase().startsWith("bearer ") ? value : `Bearer ${value}`;
}

function authHeader() {
  return normalize(storedToken()) || normalize(process.env.TOCHKA_TOKEN || "");
}

function sessionState() {
  const stored = Boolean(storedToken());
  const ready = Boolean(authHeader());
  return { ready, stored };
}

function send(res, status, body, type = "application/json; charset=utf-8") {
  res.writeHead(status, { "content-type": type, "cache-control": "no-store" });
  res.end(body);
}

function readBody(req, limit = 8192) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error("too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://localhost");

  if (url.pathname === "/api/session") {
    if (req.method === "GET") {
      send(res, 200, JSON.stringify(sessionState()));
      return;
    }
    if (req.method === "DELETE") {
      clearToken();
      send(res, 200, JSON.stringify(sessionState()));
      return;
    }
    if (req.method !== "POST") {
      send(res, 405, JSON.stringify({ error: "method" }));
      return;
    }
    try {
      const body = JSON.parse(await readBody(req) || "{}");
      const token = normalize(body.token);
      if (!token || token.length < 20) {
        send(res, 400, JSON.stringify({ error: "Вставьте токен кабинета" }));
        return;
      }
      saveToken(token);
      send(res, 200, JSON.stringify(sessionState()));
    } catch {
      send(res, 400, JSON.stringify({ error: "Не удалось сохранить токен" }));
    }
    return;
  }

  if (url.pathname.startsWith("/api/")) {
    if (req.method !== "GET") {
      send(res, 405, JSON.stringify({ error: "method" }));
      return;
    }

    const path = url.pathname.slice(4);
    if (!ALLOWED.some((pattern) => pattern.test(path))) {
      send(res, 404, JSON.stringify({ error: "not found" }));
      return;
    }

    const token = authHeader();
    if (!token) {
      send(res, 503, JSON.stringify({ error: "Сохраните токен кабинета" }));
      return;
    }

    const upstream = new URL(UPSTREAM + path);
    for (const key of ["startDate", "endDate"]) {
      const value = url.searchParams.get(key);
      if (value) upstream.searchParams.set(key, value);
    }

    try {
      const response = await fetch(upstream, {
        headers: { Authorization: token, Accept: "application/json" },
      });
      send(res, response.status, await response.text());
    } catch {
      send(res, 502, JSON.stringify({ error: "Кабинет не ответил" }));
    }
    return;
  }

  if (url.pathname !== "/") {
    send(res, 404, "not found", "text/plain; charset=utf-8");
    return;
  }

  send(res, 200, await readFile(PAGE), "text/html; charset=utf-8");
});

server.listen(PORT, () => {
  console.log(`http://localhost:${PORT}`);
});

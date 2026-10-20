import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

const PORT = Number(process.env.PORT || 4173);
const UPSTREAM = "https://app.tochka-school.ru/api/v1/lk";
const PAGE = join(import.meta.dirname, "public", "index.html");

const ALLOWED = [
  /^\/profile$/,
  /^\/children$/,
  /^\/journal\/\d+\/(active|allfinished|finished|archived|cancelled)$/,
  /^\/journal\/\d+\/detail\/\d+$/,
];

function authHeader() {
  const raw = (process.env.TOCHKA_TOKEN || "").trim();
  if (!raw) return null;
  return raw.toLowerCase().startsWith("bearer ") ? raw : `Bearer ${raw}`;
}

function send(res, status, body, type = "application/json; charset=utf-8") {
  res.writeHead(status, { "content-type": type, "cache-control": "no-store" });
  res.end(body);
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", "http://localhost");

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
      send(res, 503, JSON.stringify({ error: "Задайте TOCHKA_TOKEN" }));
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

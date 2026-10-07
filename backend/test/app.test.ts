import assert from "node:assert/strict";
import { test } from "node:test";
import { createApp } from "../src/app.ts";
import { InMemoryDailyQuota, noSubscriptions } from "../src/quota.ts";
import { buildUserContent, type Solution, type SolveOutcome, type Solver } from "../src/solve.ts";

const solution: Solution = {
  status: "solved",
  subject: "Maths",
  question: "2 + 3 = ?",
  steps: [{ title: "Add", explanation: "2 + 3 = 5" }],
  finalAnswer: "5",
  tip: null,
};

function fakeSolver(outcome: SolveOutcome): Solver & { calls: number } {
  return { calls: 0, async solve() { this.calls++; return outcome; } };
}

const body = { image: "aGVsbG8=", mediaType: "image/jpeg", language: "hi" };
const post = (app: ReturnType<typeof createApp>, json: unknown, deviceId = "device-1234") =>
  app.request("/v1/solve", {
    method: "POST",
    headers: { "content-type": "application/json", "x-device-id": deviceId },
    body: JSON.stringify(json),
  });

test("returns the solution", async () => {
  const app = createApp({ solver: fakeSolver({ kind: "solved", solution }), quota: new InMemoryDailyQuota(5), entitlements: noSubscriptions });
  const res = await post(app, body);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), solution);
});

test("rejects requests without a device id or with a bad body", async () => {
  const app = createApp({ solver: fakeSolver({ kind: "solved", solution }), quota: new InMemoryDailyQuota(5), entitlements: noSubscriptions });
  assert.equal((await post(app, body, "")).status, 400);
  assert.equal((await post(app, { ...body, language: "fr" })).status, 400);
});

test("enforces the free daily limit per device", async () => {
  const solver = fakeSolver({ kind: "solved", solution });
  const app = createApp({ solver, quota: new InMemoryDailyQuota(2), entitlements: noSubscriptions });
  assert.equal((await post(app, body)).status, 200);
  assert.equal((await post(app, body)).status, 200);
  assert.equal((await post(app, body)).status, 429);
  assert.equal((await post(app, body, "other-device")).status, 200);
  assert.equal(solver.calls, 3);
});

test("subscribers skip the limit", async () => {
  const app = createApp({ solver: fakeSolver({ kind: "solved", solution }), quota: new InMemoryDailyQuota(0), entitlements: { isSubscribed: async () => true } });
  assert.equal((await post(app, body)).status, 200);
});

test("a refused or failed solve does not use up a free solve", async () => {
  const quota = new InMemoryDailyQuota(1);
  const refusing = createApp({ solver: fakeSolver({ kind: "refused" }), quota, entitlements: noSubscriptions });
  assert.equal((await post(refusing, body)).status, 422);
  const ok = createApp({ solver: fakeSolver({ kind: "solved", solution }), quota, entitlements: noSubscriptions });
  assert.equal((await post(ok, body)).status, 200);
});

test("quota resets on a new UTC day", () => {
  let now = new Date("2026-10-07T23:59:00Z");
  const quota = new InMemoryDailyQuota(1, () => now);
  assert.ok(quota.tryConsume("d"));
  assert.ok(!quota.tryConsume("d"));
  now = new Date("2026-10-08T00:01:00Z");
  assert.ok(quota.tryConsume("d"));
});

test("prompt carries language, mode, grade and OCR hint", () => {
  const [image, text] = buildUserContent({ ...body, mediaType: "image/jpeg", language: "hi", mode: "simpler", grade: 7, ocrText: "2+3" });
  assert.equal(image.type, "image");
  assert.equal(text.type, "text");
  const t = (text as { text: string }).text;
  assert.match(t, /Hindi/);
  assert.match(t, /more simply/);
  assert.match(t, /class 7/);
  assert.match(t, /<ocr>\n2\+3\n<\/ocr>/);
});

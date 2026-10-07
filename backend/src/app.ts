import Anthropic from "@anthropic-ai/sdk";
import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { type Entitlements, type Quota } from "./quota.js";
import { SolveRequest, type Solver } from "./solve.js";

export interface AppDeps {
  solver: Solver;
  quota: Quota;
  entitlements: Entitlements;
}

const DEVICE_ID = /^[A-Za-z0-9-]{8,64}$/;

export function createApp({ solver, quota, entitlements }: AppDeps) {
  const app = new Hono();

  app.get("/health", (c) => c.json({ ok: true }));

  app.post("/v1/solve", bodyLimit({ maxSize: 8 * 1024 * 1024 }), async (c) => {
    // TODO: replace the bare device id with Play Integrity attestation before launch.
    const deviceId = c.req.header("x-device-id") ?? "";
    if (!DEVICE_ID.test(deviceId)) return c.json({ error: "missing_device_id" }, 400);

    const parsed = SolveRequest.safeParse(await c.req.json().catch(() => null));
    if (!parsed.success) return c.json({ error: "invalid_request", issues: parsed.error.issues }, 400);

    const subscribed = await entitlements.isSubscribed(deviceId);
    if (!subscribed && !quota.tryConsume(deviceId)) {
      return c.json({ error: "daily_limit_reached" }, 429);
    }

    try {
      const outcome = await solver.solve(parsed.data);
      if (outcome.kind === "solved") return c.json(outcome.solution);
      if (!subscribed) quota.refund(deviceId);
      if (outcome.kind === "refused") return c.json({ error: "cannot_help_with_this" }, 422);
      return c.json({ error: outcome.reason }, 502);
    } catch (err) {
      if (!subscribed) quota.refund(deviceId);
      if (err instanceof Anthropic.RateLimitError) return c.json({ error: "busy_try_again" }, 503);
      if (err instanceof Anthropic.APIConnectionError) return c.json({ error: "upstream_unreachable" }, 502);
      if (err instanceof Anthropic.APIError) {
        console.error("Claude API error", err.status, err.message);
        return c.json({ error: "upstream_error" }, 502);
      }
      throw err;
    }
  });

  return app;
}

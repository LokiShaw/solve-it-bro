import Anthropic from "@anthropic-ai/sdk";
import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { InMemoryDailyQuota, noSubscriptions } from "./quota.js";
import { ClaudeSolver, type ClaudeSolverOptions } from "./solve.js";

const port = Number(process.env.PORT ?? 8787);
const app = createApp({
  // Reads ANTHROPIC_API_KEY from the environment; it never leaves the server.
  solver: new ClaudeSolver(new Anthropic(), {
    model: process.env.CLAUDE_MODEL ?? "claude-opus-5-5",
    effort: (process.env.CLAUDE_EFFORT as ClaudeSolverOptions["effort"]) ?? "medium",
  }),
  quota: new InMemoryDailyQuota(Number(process.env.FREE_DAILY_SOLVES ?? 5)),
  entitlements: noSubscriptions,
});

serve({ fetch: app.fetch, port }, ({ port }) => console.log(`Solve It Bro backend on :${port}`));

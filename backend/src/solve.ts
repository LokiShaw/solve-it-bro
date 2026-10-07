import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod";

export const Language = z.enum(["en", "hi"]);
export const Mode = z.enum(["standard", "simpler"]);
export const MediaType = z.enum(["image/jpeg", "image/png", "image/webp"]);

export const SolveRequest = z.object({
  /** Base64 photo of the question, already cropped on the device. */
  image: z.string().min(1).max(7_000_000),
  mediaType: MediaType,
  /** On-device ML Kit OCR text; a hint, since handwriting and maths often read badly. */
  ocrText: z.string().max(4_000).optional(),
  language: Language.default("en"),
  mode: Mode.default("standard"),
  /** School class 1 to 12, when the student has set it. */
  grade: z.number().int().min(1).max(12).optional(),
});
export type SolveRequest = z.infer<typeof SolveRequest>;

export const Solution = z.object({
  status: z.enum(["solved", "unreadable", "not_a_question"]),
  subject: z.string().describe("e.g. Maths, Physics, Hindi grammar, History"),
  question: z.string().describe("The question as read from the photo"),
  steps: z.array(
    z.object({
      title: z.string(),
      explanation: z.string().describe("Plain text shown as-is in the app"),
    }),
  ),
  finalAnswer: z.string(),
  tip: z.string().nullable().describe("One short tip for similar questions, or null"),
});
export type Solution = z.infer<typeof Solution>;

export type SolveOutcome =
  | { kind: "solved"; solution: Solution }
  | { kind: "refused" }
  | { kind: "failed"; reason: string };

export interface Solver {
  solve(request: SolveRequest): Promise<SolveOutcome>;
}

const SYSTEM_PROMPT = `You are Solve It Bro, a patient tutor for Indian school students (classes 1 to 12) across every subject.
A student has photographed one homework question. Read it from the image; OCR text, when given, is only a hint and may be wrong.
Teach the method step by step so the student could solve a similar question alone, then give the final answer.
Match the level of the student's class when it is given. Keep each step short and concrete.
The app shows plain text: no Markdown, no LaTeX. Write maths with Unicode symbols such as ×, ÷, −, ², √, π, ≤ and fractions like 3/4.
If the image is unreadable or not a question, set status accordingly, explain briefly in finalAnswer and leave steps empty.`;

const LANGUAGE_NOTE: Record<z.infer<typeof Language>, string> = {
  en: "Write every explanation in simple English.",
  hi: "Write every explanation in simple Hindi (Devanagari script). Keep numbers, formulas and units in standard notation, and keep common technical terms in English in brackets when that helps.",
};

const MODE_NOTE: Record<z.infer<typeof Mode>, string> = {
  standard: "",
  simpler:
    "The student found the previous explanation hard. Explain again more simply: smaller steps, everyday examples, no jargon.",
};

export function buildUserContent(req: SolveRequest): Anthropic.Beta.BetaContentBlockParam[] {
  const notes = [
    LANGUAGE_NOTE[req.language],
    MODE_NOTE[req.mode],
    req.grade ? `The student is in class ${req.grade}.` : "",
    req.ocrText ? `OCR text from the device:\n<ocr>\n${req.ocrText}\n</ocr>` : "",
  ].filter(Boolean);
  return [
    { type: "image", source: { type: "base64", media_type: req.mediaType, data: req.image } },
    { type: "text", text: notes.join("\n\n") },
  ];
}

export interface ClaudeSolverOptions {
  model: string;
  effort: "low" | "medium" | "high" | "xhigh" | "max";
}

export class ClaudeSolver implements Solver {
  constructor(
    private readonly client: Anthropic,
    private readonly options: ClaudeSolverOptions,
  ) {}

  async solve(req: SolveRequest): Promise<SolveOutcome> {
    const response = await this.client.beta.messages.parse({
      model: this.options.model,
      max_tokens: 16000,
      system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
      messages: [{ role: "user", content: buildUserContent(req) }],
      output_config: { effort: this.options.effort, format: betaZodOutputFormat(Solution) },
      // If a safety classifier declines, the API retries on Anthropic's recommended fallback model.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
    });

    if (response.stop_reason === "refusal") return { kind: "refused" };
    if (response.stop_reason === "max_tokens") return { kind: "failed", reason: "answer_too_long" };
    if (!response.parsed_output) return { kind: "failed", reason: "unparseable_answer" };
    return { kind: "solved", solution: response.parsed_output };
  }
}

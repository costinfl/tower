import type { ImportOutcome } from "../api/client";

// Presentation metadata for import outcomes. Like Stage (domain/stage.ts) and
// Iteration state (domain/iteration.ts), each outcome is conveyed by colour, a
// glyph and a text label together — colour is never the only signal.
export interface ImportOutcomeMeta {
  outcome: ImportOutcome;
  label: string;
  glyph: string;
  className: string;
  /** What actually happened, in the words a developer would use. */
  meaning: string;
}

export const IMPORT_OUTCOME_META: Record<ImportOutcome, ImportOutcomeMeta> = {
  CREATED: {
    outcome: "CREATED",
    label: "New",
    glyph: "+",
    className: "created",
    meaning: "Not present here; will be added.",
  },
  REPLACED: {
    outcome: "REPLACED",
    label: "Replaced",
    glyph: "⇄",
    className: "replaced",
    meaning: "Already present; the local record will be overwritten.",
  },
  SKIPPED: {
    outcome: "SKIPPED",
    label: "Skipped",
    glyph: "–",
    className: "skipped",
    meaning: "Already present; the local record will be kept.",
  },
  DUPLICATED: {
    outcome: "DUPLICATED",
    label: "Duplicated",
    glyph: "⧉",
    className: "duplicated",
    meaning: "Already present; both will be kept under distinguishable names.",
  },
  UNCHANGED: {
    outcome: "UNCHANGED",
    label: "Unchanged",
    glyph: "=",
    className: "unchanged",
    meaning: "The same immutable record is already here; nothing to do.",
  },
};

export function importOutcomeMeta(outcome: ImportOutcome): ImportOutcomeMeta {
  return IMPORT_OUTCOME_META[outcome];
}

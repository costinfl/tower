import type { Stage } from "../api/client";

// Presentation metadata for each Stage. Stage is conveyed through three
// independent channels — colour, a short text label, and a glyph/shape —
// so the Lane view remains legible for colour-blind users and in
// greyscale/print contexts (colour alone is never load-bearing).
export interface StageMeta {
  stage: Stage;
  /** Short abbreviation shown directly on every node, e.g. "DEV". */
  label: string;
  /** Full name shown in the legend and tooltips. */
  name: string;
  /** Unicode glyph giving each stage a distinct shape. */
  glyph: string;
  /** CSS class suffix used by index.css (stage--development, etc). */
  className: string;
}

export const STAGE_META: Record<Stage, StageMeta> = {
  DEVELOPMENT: {
    stage: "DEVELOPMENT",
    label: "DEV",
    name: "Development",
    glyph: "▲",
    className: "development",
  },
  VALIDATION: {
    stage: "VALIDATION",
    label: "VAL",
    name: "Validation",
    glyph: "◆",
    className: "validation",
  },
  PRE_PRODUCTION: {
    stage: "PRE_PRODUCTION",
    label: "PRE",
    name: "Pre-Production",
    glyph: "⬟",
    className: "pre-production",
  },
  PRODUCTION: {
    stage: "PRODUCTION",
    label: "PROD",
    name: "Production",
    glyph: "●",
    className: "production",
  },
};

export function stageMeta(stage: Stage): StageMeta {
  return STAGE_META[stage];
}

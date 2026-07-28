import type { ReleasePackState } from "../api/client";

// Presentation metadata for Release Pack state (ADR-008): the highest
// Stage at which any content version of the pack has been observed, or
// PLANNED if it has never been observed. Like Stage (domain/stage.ts) and
// Iteration state (domain/iteration.ts), state is conveyed through
// colour, a glyph and a short text label together — never colour alone.
//
// This is entirely separate from the pack's `archived` lifecycle flag
// (ADR-008): archived is a human decision, this is a derived fact. A pack
// can be archived and have been observed in Production at the same time
// — the two are always rendered as independent indicators, never merged.
export interface ReleasePackStateMeta {
  state: ReleasePackState;
  /** Short abbreviation, e.g. "DEV". */
  label: string;
  /** Full name shown in detail views. */
  name: string;
  /** Unicode glyph giving each state a distinct shape. */
  glyph: string;
  /** CSS class suffix used by index.css (pack-state--development, etc). */
  className: string;
}

export const RELEASE_PACK_STATES: ReleasePackState[] = [
  "PLANNED",
  "DEVELOPMENT",
  "VALIDATION",
  "PRE_PRODUCTION",
  "PRODUCTION",
];

export const RELEASE_PACK_STATE_META: Record<ReleasePackState, ReleasePackStateMeta> = {
  PLANNED: {
    state: "PLANNED",
    label: "PLANNED",
    name: "Planned",
    glyph: "○",
    className: "planned",
  },
  DEVELOPMENT: {
    state: "DEVELOPMENT",
    label: "DEV",
    name: "Development",
    glyph: "▲",
    className: "development",
  },
  VALIDATION: {
    state: "VALIDATION",
    label: "VAL",
    name: "Validation",
    glyph: "◆",
    className: "validation",
  },
  PRE_PRODUCTION: {
    state: "PRE_PRODUCTION",
    label: "PRE",
    name: "Pre-Production",
    glyph: "⬟",
    className: "pre-production",
  },
  PRODUCTION: {
    state: "PRODUCTION",
    label: "PROD",
    name: "Production",
    glyph: "●",
    className: "production",
  },
};

export function releasePackStateMeta(state: ReleasePackState): ReleasePackStateMeta {
  return RELEASE_PACK_STATE_META[state];
}

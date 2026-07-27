import type { ReleasePackIteration } from "../api/client";

// Presentation metadata for validation Iteration state. Like Stage
// (domain/stage.ts), state is conveyed through colour, a glyph and a short
// text label together, never colour alone. Iteration only has two states,
// derived from whether completedAt is set — there is no separate stored
// status field to get out of sync.
export type IterationState = "OPEN" | "COMPLETE";

export interface IterationStateMeta {
  state: IterationState;
  label: string;
  glyph: string;
  className: string;
}

export const ITERATION_STATE_META: Record<IterationState, IterationStateMeta> = {
  OPEN: {
    state: "OPEN",
    label: "Open",
    glyph: "◐",
    className: "open",
  },
  COMPLETE: {
    state: "COMPLETE",
    label: "Complete",
    glyph: "✓",
    className: "complete",
  },
};

export function iterationState(iteration: Pick<ReleasePackIteration, "completedAt">): IterationState {
  return iteration.completedAt ? "COMPLETE" : "OPEN";
}

export function iterationStateMeta(iteration: Pick<ReleasePackIteration, "completedAt">): IterationStateMeta {
  return ITERATION_STATE_META[iterationState(iteration)];
}

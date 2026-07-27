import type { Environment } from "../api/client";
import { stageMeta } from "../domain/stage";

interface EnvironmentNodeProps {
  env: Environment;
  /** True when this Environment also appears in another currently-visible Lane (ADR-005 convergence). */
  shared: boolean;
  /** True when the same Environment id is hovered/focused elsewhere on screen. */
  linked: boolean;
  onHover: (id: string | null) => void;
}

// A single node within a Lane. Stage is conveyed through colour, a glyph
// shape and a short text label together — never colour alone. Shared
// Environments (ADR-005: an Environment may participate in several
// Promotion Paths) carry a small "linked" marker, and hovering/focusing
// any occurrence highlights every other occurrence across all Lanes on
// screen via the `linked` flag passed down from the parent page.
export default function EnvironmentNode({ env, shared, linked, onHover }: EnvironmentNodeProps) {
  const meta = stageMeta(env.stage);
  const classes = [
    "env-node",
    `stage--${meta.className}`,
    shared ? "env-node--shared" : "",
    linked ? "env-node--linked" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={classes}
      tabIndex={0}
      role="group"
      aria-label={`${env.name}, stage ${meta.name}${shared ? ", shared with another promotion path" : ""}`}
      onMouseEnter={() => onHover(env.id)}
      onMouseLeave={() => onHover(null)}
      onFocus={() => onHover(env.id)}
      onBlur={() => onHover(null)}
    >
      {shared && (
        <span className="env-node__shared-badge" title="This Environment also appears in another Promotion Path">
          ⇄
        </span>
      )}
      <span className="env-node__glyph" aria-hidden="true">
        {meta.glyph}
      </span>
      <span className="env-node__name">{env.name}</span>
      <span className="env-node__stage">{meta.label}</span>
    </div>
  );
}

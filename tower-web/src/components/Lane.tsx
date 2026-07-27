import { Fragment } from "react";
import type { PathVersion } from "../api/client";
import EnvironmentNode from "./EnvironmentNode";

interface LaneProps {
  version: PathVersion;
  sharedEnvironmentIds: ReadonlySet<string>;
  hoveredEnvironmentId: string | null;
  onHoverEnvironment: (id: string | null) => void;
}

// The visual representation of a Promotion Path (Glossary: "Promotion
// Paths define structure. Lanes define presentation."). A Lane carries no
// business rules of its own — it just renders the ordered Environments of
// one Promotion Path version, left to right, with connectors between them.
export default function Lane({ version, sharedEnvironmentIds, hoveredEnvironmentId, onHoverEnvironment }: LaneProps) {
  if (version.environments.length === 0) {
    return <p className="lane lane--empty">This version has no Environments.</p>;
  }

  return (
    <div className="lane" role="list">
      {version.environments.map((env, index) => (
        <Fragment key={`${env.id}-${index}`}>
          {index > 0 && (
            <span className="lane__connector" aria-hidden="true">
              →
            </span>
          )}
          <EnvironmentNode
            env={env}
            shared={sharedEnvironmentIds.has(env.id)}
            linked={hoveredEnvironmentId === env.id}
            onHover={onHoverEnvironment}
          />
        </Fragment>
      ))}
    </div>
  );
}

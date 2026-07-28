import type { ObservationSource } from "../api/client";

// Shows where an Observation came from (FR-012/FR-013, ADR-006). A
// developer must always be able to tell a fact a colleague typed in
// yesterday from one an automated collector reported a minute ago, so the
// source is always spelled out — never abbreviated to a colour or icon.
//
// Milestone 1 has only the Manual Collector, so this always reads
// "manual · <name>" today. The shape (collector name, actor, optional
// origin instance) is ready for an automated collector to slot in later
// without any change to this component.
export default function ObservationSourceTag({ source }: { source: ObservationSource }) {
  return (
    <span className="source-tag">
      <span
        className={
          source.manual ? "source-tag__collector source-tag__collector--manual" : "source-tag__collector"
        }
      >
        {source.manual ? "manual" : source.collector}
      </span>
      {source.actor && <span className="source-tag__actor">{source.actor}</span>}
      {source.originInstance && <span className="source-tag__origin">{source.originInstance}</span>}
    </span>
  );
}

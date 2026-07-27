import { STAGES } from "../api/client";
import { stageMeta } from "../domain/stage";

// Legend explaining how Stage is encoded on Environment nodes. Every
// stage is distinguished by three independent channels (colour, glyph
// shape, short text label) so the legend — and the nodes it explains —
// remain legible without colour.
export default function StageLegend() {
  return (
    <div className="stage-legend" aria-label="Stage legend">
      <span className="stage-legend__title">Stage:</span>
      {STAGES.map((stage) => {
        const meta = stageMeta(stage);
        return (
          <span className={`stage-legend__item stage--${meta.className}`} key={stage}>
            <span className="stage-legend__glyph" aria-hidden="true">
              {meta.glyph}
            </span>
            <span className="stage-legend__label">{meta.name}</span>
          </span>
        );
      })}
    </div>
  );
}

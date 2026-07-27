import { STAGES, type Stage } from "../api/client";
import { stageMeta } from "../domain/stage";

interface StageSelectProps {
  id?: string;
  value: Stage;
  onChange: (stage: Stage) => void;
}

export default function StageSelect({ id, value, onChange }: StageSelectProps) {
  return (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value as Stage)}>
      {STAGES.map((stage) => (
        <option value={stage} key={stage}>
          {stageMeta(stage).name}
        </option>
      ))}
    </select>
  );
}

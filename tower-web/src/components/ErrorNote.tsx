import { ApiError } from "../api/client";

// Surfaces a server error message plainly, as written for a developer to
// read (tower-api error bodies are {timestamp, status, message, path}).
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

export default function ErrorNote({ error }: { error: unknown }) {
  return <p className="error-note">{describeError(error)}</p>;
}

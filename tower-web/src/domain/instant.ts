// A `datetime-local` input yields "2026-08-04T09:00" — a wall-clock reading
// with no zone. The API takes instants, so the two are not interchangeable
// and passing the raw value through would be rejected as unparseable at
// best, and silently misread at worst.
//
// The browser's own zone is the right one to assume: somebody typing "09:00"
// into Tower means nine o'clock where they are, not nine UTC.
export function toInstant(localValue: string): string | undefined {
  if (!localValue) return undefined;
  const parsed = new Date(localValue);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

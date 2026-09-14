/** Loose JSON helpers for BFF-proxied Identity / MobiStack payloads. */

export function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

export function asRecords(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) {
    return value.map(asRecord);
  }
  const obj = asRecord(value);
  if (Array.isArray(obj.items)) return obj.items.map(asRecord);
  if (Array.isArray(obj.content)) return obj.content.map(asRecord);
  return [];
}

export function field(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value == null) continue;
    const text = String(value);
    if (text.length > 0 && text !== "null" && text !== "undefined") return text;
  }
  return "";
}

export function boolField(row: Record<string, unknown>, ...keys: string[]): boolean {
  for (const key of keys) {
    const value = row[key];
    if (value === true || value === "true") return true;
    if (value === false || value === "false") return false;
  }
  return false;
}

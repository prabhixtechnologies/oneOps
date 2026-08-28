/** Parse rupee string to integer paise without floating-point drift. */
export function rupeesToPaise(input: string): number {
  const trimmed = input.trim().replace(/,/g, "");
  if (!trimmed) return 0;
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(trimmed);
  if (!match) throw new Error("Invalid amount");
  const rupees = BigInt(match[1]!);
  const frac = (match[2] ?? "0").padEnd(2, "0").slice(0, 2);
  return Number(rupees * BigInt(100) + BigInt(frac));
}

export function paiseToRupeesString(minor: number): string {
  const whole = Math.trunc(minor / 100);
  const frac = Math.abs(minor % 100);
  return `${whole}.${String(frac).padStart(2, "0")}`;
}

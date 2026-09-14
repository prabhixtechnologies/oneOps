/** Format integer minor units (paise) for display only — never use floats for money math. */
export function formatMoney(minor: number, currency = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(minor / 100);
}

/** Parse rupee string to integer paise without floating-point drift. */
export function rupeesToPaise(input: string): number {
  const trimmed = input.trim().replace(/,/g, "");
  if (!trimmed) return 0;
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(trimmed);
  if (!match) {
    throw new Error("Enter a valid amount (e.g. 99 or 99.50)");
  }
  const rupees = BigInt(match[1]!);
  const frac = (match[2] ?? "0").padEnd(2, "0").slice(0, 2);
  const paise = rupees * BigInt(100) + BigInt(frac);
  if (paise > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error("Amount is too large");
  }
  return Number(paise);
}

export function paiseToRupeesString(minor: number): string {
  const whole = Math.trunc(minor / 100);
  const frac = Math.abs(minor % 100);
  return `${whole}.${String(frac).padStart(2, "0")}`;
}

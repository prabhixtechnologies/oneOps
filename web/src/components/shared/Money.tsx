interface MoneyProps {
  amount: number;
  currency?: string;
  className?: string;
}

export function Money({ amount, currency = "INR", className }: MoneyProps) {
  const formatted = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    minimumFractionDigits: currency === "INR" ? 0 : 2,
  }).format(amount / 100);

  return <span className={className}>{formatted}</span>;
}

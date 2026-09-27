/*
  Small inline charts.

  Both of these used `var(--primary)`, which is not a token that exists — the Tailwind name is
  `--color-primary` and the underlying custom property is `--px-accent`. An undefined custom
  property is invalid at computed-value time, so `stroke` fell back to its initial value of
  `none` and the dashboard sparklines drew nothing at all, while `fill` fell back to `black`
  and the bar charts rendered as black bars in both themes.

  Series colour now comes from the generated categorical palette (`--px-cat-1` … `--px-cat-10`),
  which is ordered so the first four stay distinguishable under the common forms of colour
  blindness and is re-lightened rather than re-hued for the dark theme.
*/

/** The categorical series colours, by 1-based index, wrapping past ten. */
export function seriesColor(index: number): string {
  return `var(--px-cat-${(index % 10) + 1})`;
}

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  className?: string;
  /** Any CSS colour. Defaults to the first categorical series. */
  color?: string;
  /**
   * Fills the area under the line with a fade of the same hue. Reads as a trend rather than a
   * wire, which is what these are used for on the dashboard.
   */
  fill?: boolean;
  /**
   * What the line represents, for screen readers. Without it the chart is `aria-hidden`, which
   * is correct only when an adjacent number already carries the value.
   */
  label?: string;
}

export function Sparkline({
  data,
  width = 120,
  height = 32,
  className,
  color = "var(--px-cat-1)",
  fill = true,
  label,
}: SparklineProps) {
  if (data.length === 0) return null;
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min || 1;
  const at = (v: number, i: number) => {
    const x = (i / (data.length - 1 || 1)) * width;
    const y = height - ((v - min) / range) * (height - 4) - 2;
    return [x, y] as const;
  };
  const points = data.map((v, i) => at(v, i).join(",")).join(" ");
  const gradientId = `spark-${Math.round(width)}-${data.length}`;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={className}
      preserveAspectRatio="none"
      {...(label
        ? { role: "img", "aria-label": `${label}: trend over ${data.length} points` }
        : { "aria-hidden": "true" })}
    >
      {fill && (
        <>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity="0.28" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <polygon fill={`url(#${gradientId})`} points={`0,${height} ${points} ${width},${height}`} />
        </>
      )}
      <polyline
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        points={points}
      />
    </svg>
  );
}

interface BarChartProps {
  data: { label: string; value: number }[];
  width?: number;
  height?: number;
  className?: string;
  /**
   * Gives every bar its own categorical colour. Use it when the bars are *categories* — event
   * levels, channels, statuses. Leave it off when they are one measure over time, where a
   * single hue is correct and ten would imply a distinction that is not there.
   */
  categorical?: boolean;
  label?: string;
}

export function MiniBarChart({
  data,
  width = 200,
  height = 48,
  className,
  categorical = false,
  label = "Bar chart",
}: BarChartProps) {
  const max = Math.max(...data.map((d) => d.value), 1);
  const barWidth = width / data.length - 2;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={className}
      role="img"
      aria-label={label}
      preserveAspectRatio="none"
    >
      {data.map((d, i) => {
        const barHeight = (d.value / max) * (height - 8);
        return (
          <rect
            key={d.label}
            x={i * (barWidth + 2)}
            y={height - barHeight - 4}
            width={barWidth}
            height={barHeight}
            rx={2}
            fill={categorical ? seriesColor(i) : "var(--px-accent)"}
            // A single-hue chart still needs the bars told apart, and opacity is the one
            // channel available without implying a category. A categorical chart already has
            // hue doing that job, so it stays fully opaque.
            opacity={categorical ? 1 : 0.7 + (i / data.length) * 0.3}
          >
            <title>{`${d.label}: ${d.value}`}</title>
          </rect>
        );
      })}
    </svg>
  );
}

interface DonutProps {
  data: { label: string; value: number }[];
  size?: number;
  thickness?: number;
  className?: string;
  label?: string;
}

/**
 * A proportional breakdown, in categorical colour. Built because every "share of total" on the
 * dashboards was previously a row of tinted bars, which cannot show a whole.
 */
export function MiniDonut({ data, size = 96, thickness = 12, className, label }: DonutProps) {
  const total = data.reduce((sum, d) => sum + d.value, 0);
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  return (
    <svg
      viewBox={`0 0 ${size} ${size}`}
      width={size}
      height={size}
      className={className}
      role="img"
      aria-label={label ?? `Breakdown of ${total}`}
    >
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="var(--px-surface-sunken)"
        strokeWidth={thickness}
      />
      {total > 0 &&
        data.map((d, i) => {
          const length = (d.value / total) * circumference;
          const segment = (
            <circle
              key={d.label}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={seriesColor(i)}
              strokeWidth={thickness}
              strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset}
              // Rotates the ring so the first segment starts at twelve o'clock instead of
              // three, which is where a reader expects a breakdown to begin.
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
            >
              <title>{`${d.label}: ${d.value}`}</title>
            </circle>
          );
          offset += length;
          return segment;
        })}
    </svg>
  );
}

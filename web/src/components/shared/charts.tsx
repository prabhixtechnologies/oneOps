interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  className?: string;
  color?: string;
}

export function Sparkline({ data, width = 120, height = 32, className, color = "var(--primary)" }: SparklineProps) {
  if (data.length === 0) return null;
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min || 1;
  const points = data
    .map((v, i) => {
      const x = (i / (data.length - 1 || 1)) * width;
      const y = height - ((v - min) / range) * (height - 4) - 2;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={className}
      aria-hidden="true"
      preserveAspectRatio="none"
    >
      <polyline fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" points={points} />
    </svg>
  );
}

interface BarChartProps {
  data: { label: string; value: number }[];
  width?: number;
  height?: number;
  className?: string;
}

export function MiniBarChart({ data, width = 200, height = 48, className }: BarChartProps) {
  const max = Math.max(...data.map((d) => d.value), 1);
  const barWidth = width / data.length - 2;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={className}
      role="img"
      aria-label="Bar chart"
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
            fill="var(--primary)"
            opacity={0.7 + (i / data.length) * 0.3}
          >
            <title>{`${d.label}: ${d.value}`}</title>
          </rect>
        );
      })}
    </svg>
  );
}

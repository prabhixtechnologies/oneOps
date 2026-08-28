import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const ALL = "all";

/**
 * Status dropdown for the ops lists.
 *
 * <p>Radix Select cannot hold an empty string as a value, so "no filter" travels as the sentinel
 * {@code all} and is translated to `undefined` here rather than in every caller.
 */
export function StatusFilter({
  label,
  statuses,
  value,
  onChange,
}: {
  label: string;
  statuses: readonly string[];
  value: string | undefined;
  onChange: (status: string | undefined) => void;
}) {
  return (
    <Select
      value={value ?? ALL}
      onValueChange={(next) => onChange(next === ALL ? undefined : next)}
    >
      <SelectTrigger className="w-full sm:w-48">
        <SelectValue placeholder={label} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>{label}</SelectItem>
        {statuses.map((status) => (
          <SelectItem key={status} value={status}>
            {status.replace(/_/g, " ")}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

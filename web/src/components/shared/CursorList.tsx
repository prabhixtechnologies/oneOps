import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useRef, type ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { cn } from "@/lib/utils";

interface CursorListProps<T> {
  items: T[];
  hasMore: boolean;
  isLoading: boolean;
  isError: boolean;
  errorMessage?: string;
  onLoadMore: () => void;
  isFetchingNextPage?: boolean;
  estimateSize?: number;
  emptyTitle?: string;
  emptyDescription?: string;
  getKey: (item: T) => string;
  renderItem: (item: T, index: number) => ReactNode;
  className?: string;
}

export function CursorList<T>({
  items,
  hasMore,
  isLoading,
  isError,
  errorMessage,
  onLoadMore,
  isFetchingNextPage,
  estimateSize = 64,
  emptyTitle = "No items",
  emptyDescription,
  getKey,
  renderItem,
  className,
}: CursorListProps<T>) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: items.length + (hasMore ? 1 : 0),
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateSize,
    overscan: 8,
  });

  useEffect(() => {
    const virtualItems = virtualizer.getVirtualItems();
    const last = virtualItems[virtualItems.length - 1];
    if (last && last.index >= items.length - 3 && hasMore && !isFetchingNextPage) {
      onLoadMore();
    }
  }, [virtualizer, items.length, hasMore, isFetchingNextPage, onLoadMore]);

  if (isLoading && items.length === 0) {
    return (
      <div className={cn("space-y-2 p-2", className)}>
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    );
  }

  if (isError) {
    return <ErrorState message={errorMessage ?? "Failed to load items"} onRetry={onLoadMore} />;
  }

  if (items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <div ref={parentRef} className={cn("h-full overflow-auto scrollbar-thin", className)}>
      <div style={{ height: `${virtualizer.getTotalSize()}px`, width: "100%", position: "relative" }}>
        {virtualizer.getVirtualItems().map((virtualRow) => {
          const isLoaderRow = virtualRow.index >= items.length;
          const item = items[virtualRow.index];
          return (
            <div
              key={isLoaderRow ? "loader" : getKey(item)}
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                height: `${virtualRow.size}px`,
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              {isLoaderRow ? (
                <div className="flex items-center justify-center p-4">
                  <Skeleton className="h-10 w-full" />
                </div>
              ) : (
                renderItem(item, virtualRow.index)
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

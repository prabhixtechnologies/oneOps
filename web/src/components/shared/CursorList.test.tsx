import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CursorList } from "@/components/shared/CursorList";

vi.mock("@tanstack/react-virtual", () => ({
  useVirtualizer: ({ count }: { count: number }) => ({
    getVirtualItems: () =>
      Array.from({ length: count }, (_, index) => ({
        index,
        start: index * 64,
        size: 64,
        key: index,
      })),
    getTotalSize: () => count * 64,
  }),
}));

type Item = { id: string; label: string };

function renderList(items: Item[], opts?: { hasMore?: boolean; onLoadMore?: () => void }) {
  const onLoadMore = opts?.onLoadMore ?? vi.fn();
  render(
    <div style={{ height: 400 }}>
      <CursorList
        items={items}
        hasMore={opts?.hasMore ?? false}
        isLoading={false}
        isError={false}
        onLoadMore={onLoadMore}
        getKey={(item) => item.id}
        renderItem={(item) => <div data-testid="row">{item.label}</div>}
      />
    </div>,
  );
  return { onLoadMore };
}

describe("CursorList", () => {
  it("renders every item without dropping rows across pages", () => {
    const pageOne: Item[] = [
      { id: "a", label: "Alpha" },
      { id: "b", label: "Bravo" },
      { id: "c", label: "Charlie" },
    ];
    const { rerender } = render(
      <div style={{ height: 400 }}>
        <CursorList
          items={pageOne}
          hasMore
          isLoading={false}
          isError={false}
          onLoadMore={vi.fn()}
          getKey={(item) => item.id}
          renderItem={(item) => <div data-testid="row">{item.label}</div>}
        />
      </div>,
    );

    expect(screen.getAllByTestId("row")).toHaveLength(3);

    const pageTwo: Item[] = [
      ...pageOne,
      { id: "d", label: "Delta" },
      { id: "e", label: "Echo" },
    ];

    rerender(
      <div style={{ height: 400 }}>
        <CursorList
          items={pageTwo}
          hasMore={false}
          isLoading={false}
          isError={false}
          onLoadMore={vi.fn()}
          getKey={(item) => item.id}
          renderItem={(item) => <div data-testid="row">{item.label}</div>}
        />
      </div>,
    );

    const labels = screen.getAllByTestId("row").map((node) => node.textContent);
    expect(labels).toEqual(["Alpha", "Bravo", "Charlie", "Delta", "Echo"]);
    expect(new Set(pageTwo.map((i) => i.id)).size).toBe(pageTwo.length);
  });

  it("does not duplicate item keys when the same page is appended twice", () => {
    const items: Item[] = [
      { id: "1", label: "One" },
      { id: "2", label: "Two" },
    ];
    renderList(items);
    expect(screen.getAllByTestId("row")).toHaveLength(2);
    expect(screen.getByText("One")).toBeInTheDocument();
    expect(screen.getByText("Two")).toBeInTheDocument();
  });
});

import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorState } from "@/components/shared/states";

interface Props {
  children: ReactNode;
  fallbackTitle?: string;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("ErrorBoundary caught:", error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-[50vh] items-center justify-center p-6">
          <ErrorState
            title={this.props.fallbackTitle ?? "Something went wrong"}
            message={this.state.error.message}
            onRetry={() => this.setState({ error: null })}
          />
        </div>
      );
    }
    return this.props.children;
  }
}

export function RouteErrorState({ error }: { error: Error }) {
  return (
    <ErrorState
      title="Page failed to load"
      message={error.message}
      onRetry={() => window.location.reload()}
    />
  );
}

/**
 * Toast notification system.
 * Can be driven by the HostBridge NotificationBridge or used standalone.
 */
import { useState, useCallback, createContext, useContext, type ReactNode } from "react";
import { cva } from "class-variance-authority";
import { cn } from "../../lib/utils.js";
import { X } from "lucide-react";

/* ── Toast Variant Styles ──────────────────────────────────────────── */

const toastVariants = cva(
  "pointer-events-auto flex items-center gap-3 rounded-default border px-4 py-3 text-sm shadow-lg transition-all",
  {
    variants: {
      type: {
        success: "border-success/30 bg-success/10 text-success",
        info: "border-primary/30 bg-primary/10 text-primary",
        warning: "border-warning/30 bg-warning/10 text-warning",
        error: "border-danger/30 bg-danger/10 text-danger",
      },
    },
    defaultVariants: { type: "info" },
  },
);

/* ── Types ─────────────────────────────────────────────────────────── */

interface ToastItem {
  id: string;
  type: "success" | "info" | "warning" | "error";
  message: string;
}

interface ToastContextValue {
  showToast(options: {
    type: "success" | "info" | "warning" | "error";
    message: string;
    durationMs?: number;
  }): void;
}

/* ── Context ───────────────────────────────────────────────────────── */

const ToastContext = createContext<ToastContextValue | null>(null);

function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within <ToastProvider>");
  return ctx;
}

/* ── Provider ──────────────────────────────────────────────────────── */

let toastCounter = 0;

function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const showToast = useCallback(
    (options: {
      type: "success" | "info" | "warning" | "error";
      message: string;
      durationMs?: number;
    }) => {
      const id = `toast-${++toastCounter}`;
      setToasts((prev) => [...prev, { id, type: options.type, message: options.message }]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, options.durationMs ?? 4000);
    },
    [],
  );

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {/* Toast container */}
      <div
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 right-4 z-[100] flex flex-col gap-2"
      >
        {toasts.map((t) => (
          <div key={t.id} className={cn(toastVariants({ type: t.type }))}>
            <span className="flex-1">{t.message}</span>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              className="pointer-events-auto rounded-sm p-0.5 opacity-70 hover:opacity-100"
              aria-label="Dismiss"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export { ToastProvider, useToast, toastVariants };

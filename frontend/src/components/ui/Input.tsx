import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "../../lib/utils.js";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  /** Label text rendered above the input. */
  label?: string;
  /** Error message shown below the input. */
  error?: string | undefined;
  /** Optional data-testid for the error message element. */
  errorTestId?: string;
  /** Optional icon or element rendered inside the input (left side). */
  icon?: ReactNode;
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, errorTestId, icon, id, ...props }, ref) => {
    const inputId = id ?? (label ? `input-${label.toLowerCase().replace(/\s+/g, "-")}` : undefined);

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-foreground"
          >
            {label}
          </label>
        )}
        <div className="relative">
          {icon && (
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted">
              {icon}
            </span>
          )}
          <input
            ref={ref}
            id={inputId}
            className={cn(
              "flex h-9 w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm text-foreground",
              "placeholder:text-muted/60",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary",
              "disabled:cursor-not-allowed disabled:opacity-50",
              icon && "pl-9",
              error && "border-danger focus-visible:ring-danger/50",
              className,
            )}
            aria-invalid={error ? true : undefined}
            aria-describedby={error && inputId ? `${inputId}-error` : undefined}
            {...props}
          />
        </div>
        {error && (
          <p
            id={inputId ? `${inputId}-error` : undefined}
            data-testid={errorTestId}
            className="text-xs text-danger"
            role="alert"
          >
            {error}
          </p>
        )}
      </div>
    );
  },
);
Input.displayName = "Input";

export { Input };

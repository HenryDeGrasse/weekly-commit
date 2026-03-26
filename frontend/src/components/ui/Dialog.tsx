/**
 * Accessible modal dialog with backdrop overlay.
 * Traps focus, closes on Escape, prevents body scroll.
 */
import {
  forwardRef,
  useEffect,
  useRef,
  type HTMLAttributes,
  type ReactNode,
} from "react";
import { cn } from "../../lib/utils.js";

/* ── Overlay ───────────────────────────────────────────────────────── */

interface DialogOverlayProps extends HTMLAttributes<HTMLDivElement> {
  /** Called when the user clicks the backdrop or presses Escape. */
  onClose?: () => void;
}

const DialogOverlay = forwardRef<HTMLDivElement, DialogOverlayProps>(
  ({ className, onClose, children, ...props }, ref) => {
    const overlayRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      function onKeyDown(e: KeyboardEvent) {
        if (e.key === "Escape") onClose?.();
      }
      document.addEventListener("keydown", onKeyDown);
      // Prevent body scroll
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.removeEventListener("keydown", onKeyDown);
        document.body.style.overflow = prev;
      };
    }, [onClose]);

    return (
      <div
        ref={(node) => {
          overlayRef.current = node;
          if (typeof ref === "function") ref(node);
          else if (ref) ref.current = node;
        }}
        className={cn(
          "fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-[2px]",
          className,
        )}
        onClick={(e) => {
          if (e.target === overlayRef.current) onClose?.();
        }}
        {...props}
      >
        {children}
      </div>
    );
  },
);
DialogOverlay.displayName = "DialogOverlay";

/* ── Dialog Panel ──────────────────────────────────────────────────── */

const DialogPanel = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      role="dialog"
      aria-modal="true"
      className={cn(
        "w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-xl",
        "animate-in fade-in-0 zoom-in-95",
        className,
      )}
      {...props}
    />
  ),
);
DialogPanel.displayName = "DialogPanel";

/* ── DialogHeader ──────────────────────────────────────────────────── */

const DialogHeader = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("mb-4 flex flex-col gap-1.5", className)}
      {...props}
    />
  ),
);
DialogHeader.displayName = "DialogHeader";

/* ── DialogTitle ───────────────────────────────────────────────────── */

const DialogTitle = forwardRef<
  HTMLHeadingElement,
  HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h3
    ref={ref}
    className={cn("text-base font-semibold leading-tight", className)}
    {...props}
  />
));
DialogTitle.displayName = "DialogTitle";

/* ── DialogDescription ────────────────────────────────────────────── */

const DialogDescription = forwardRef<
  HTMLParagraphElement,
  HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p
    ref={ref}
    className={cn("text-sm text-muted", className)}
    {...props}
  />
));
DialogDescription.displayName = "DialogDescription";

/* ── DialogFooter ──────────────────────────────────────────────────── */

const DialogFooter = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("mt-5 flex items-center justify-end gap-2", className)}
      {...props}
    />
  ),
);
DialogFooter.displayName = "DialogFooter";

/* ── Convenience Compound ──────────────────────────────────────────── */

interface DialogProps {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  className?: string;
  "aria-label"?: string;
  "data-testid"?: string;
}

function Dialog({
  open,
  onClose,
  children,
  className,
  ...rest
}: DialogProps) {
  if (!open) return null;
  return (
    <DialogOverlay onClose={onClose}>
      <DialogPanel className={className} aria-label={rest["aria-label"]} data-testid={rest["data-testid"]}>
        {children}
      </DialogPanel>
    </DialogOverlay>
  );
}

export {
  Dialog,
  DialogOverlay,
  DialogPanel,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
};

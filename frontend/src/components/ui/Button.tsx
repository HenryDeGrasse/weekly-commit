import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../lib/utils.js";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        primary:
          "bg-primary text-white shadow-sm hover:bg-primary/90 active:bg-primary/80",
        secondary:
          "border border-border bg-surface text-foreground shadow-sm hover:bg-muted/10 active:bg-muted/20",
        ghost:
          "text-foreground hover:bg-muted/10 active:bg-muted/20",
        danger:
          "bg-danger text-white shadow-sm hover:bg-danger/90 active:bg-danger/80",
        success:
          "bg-success text-white shadow-sm hover:bg-success/90 active:bg-success/80",
        link:
          "text-primary underline-offset-4 hover:underline p-0 h-auto",
      },
      size: {
        sm: "h-8 rounded-sm px-3 text-xs",
        md: "h-9 rounded-default px-4 text-sm",
        lg: "h-10 rounded-md px-5 text-sm",
        icon: "h-9 w-9 rounded-default",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  },
);

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  ),
);
Button.displayName = "Button";

export { Button, buttonVariants };

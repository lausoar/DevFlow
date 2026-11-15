import { mergeProps } from "@base-ui/react/merge-props"
import { useRender } from "@base-ui/react/use-render"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * 徽章 / 状态标签。
 *
 * 取自 designs/linear.app.md 的 status-badge：
 *   surface-2 底 / ink-muted 文字 / pill 圆角 / padding 2px 8px / caption 字号
 *
 * 语义变体（success/warning/danger/info）用「淡底 + 同色文字」，
 * 不是实心色块 —— Linear 在深色画布上从不用大块饱和色，
 * 状态靠文字色传达，底色只做极淡的分区。
 */
const badgeVariants = cva(
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2 py-0.5 text-[11px] font-medium whitespace-nowrap transition-devflow outline-none focus-visible:border-ring focus-visible:shadow-[var(--focus-ring)] [&>svg]:pointer-events-none [&>svg]:size-3!",
  {
    variants: {
      variant: {
        default: "bg-brand-muted text-brand [a]:hover:bg-brand/25",
        secondary: "bg-surface-2 text-ink-muted [a]:hover:bg-surface-3",
        destructive: "bg-danger-muted text-danger [a]:hover:bg-danger/20",
        success: "bg-success-muted text-success [a]:hover:bg-success/20",
        warning: "bg-warning-muted text-warning [a]:hover:bg-warning/20",
        info: "bg-info-muted text-info [a]:hover:bg-info/20",
        outline: "border-hairline text-ink-muted [a]:hover:bg-surface-2",
        ghost: "text-ink-subtle [a]:hover:bg-surface-2 [a]:hover:text-ink",
        link: "text-brand underline-offset-4 hover:underline",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  render,
  ...props
}: useRender.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: "span",
    props: mergeProps<"span">(
      {
        className: cn(badgeVariants({ variant }), className),
      },
      props
    ),
    render,
    state: {
      slot: "badge",
      variant,
    },
  })
}

export { Badge, badgeVariants }

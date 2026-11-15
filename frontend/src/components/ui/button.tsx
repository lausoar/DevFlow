import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * 按钮。
 *
 * 取值来自 designs/linear.app.md 的 components 段：
 *   button-primary         → primary 底 / on-primary 文字 / rounded.md(8) / padding 8px 14px
 *   button-primary-hover   → primary-hover（更亮的 lavender）
 *   button-primary-pressed → primary-focus
 *   button-secondary       → surface-1 底 + ink 文字
 *   button-tertiary        → canvas 底（视觉上"无底"，靠 hover 提亮）
 *
 * 与改造前的差别：
 * - 圆角从 12px 改为 8px —— Linear 的按钮统一 md，12px 属于卡片
 * - hover 换色而不是降透明度：`bg-primary/80` 在深色底上会发灰
 * - 焦点环改成 1px 描边 + 淡外扩，不是 3px 实心环
 * - 高度 32px，保持密集而不局促
 */
const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center gap-1.5 rounded-md border border-transparent font-medium whitespace-nowrap transition-devflow outline-none select-none focus-visible:border-ring focus-visible:shadow-[var(--focus-ring)] disabled:pointer-events-none disabled:opacity-40 aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-brand-hover active:bg-ring",
        secondary:
          "border-hairline bg-surface-1 text-ink hover:bg-surface-2 active:bg-surface-3",
        outline:
          "border-hairline bg-transparent text-ink hover:bg-surface-2 active:bg-surface-3",
        ghost:
          "bg-transparent text-ink-muted hover:bg-surface-2 hover:text-ink active:bg-surface-3",
        destructive:
          "bg-danger-muted text-danger hover:bg-danger/20 active:bg-danger/25",
        link:
          "text-brand underline-offset-4 hover:text-brand-hover hover:underline",
      },
      size: {
        default: "h-8 px-3.5 text-sm",
        xs: "h-6 gap-1 rounded-sm px-2 text-xs [&_svg:not([class*='size-'])]:size-3",
        sm: "h-7 gap-1 rounded-sm px-2.5 text-[13px] [&_svg:not([class*='size-'])]:size-3.5",
        lg: "h-9 gap-2 px-4 text-sm",
        icon: "size-8",
        "icon-xs": "size-6 rounded-sm [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-7 rounded-sm",
        "icon-lg": "size-9",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  ...props
}: ButtonPrimitive.Props & VariantProps<typeof buttonVariants>) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }

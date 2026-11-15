import * as React from "react"
import { cn } from "@/lib/utils"

/**
 * 多行输入。与 Input 同一套取值（surface-1 底 + hairline 边框 + 8px 圆角），
 * 只是没有固定高度，用 min-height 撑开。
 */
function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex min-h-[72px] w-full rounded-md border border-hairline bg-surface-1 px-3 py-2 text-sm leading-relaxed outline-none transition-devflow",
        "placeholder:text-ink-tertiary",
        "hover:border-hairline-strong",
        "focus-visible:border-ring focus-visible:shadow-[var(--focus-ring)]",
        "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50",
        "aria-invalid:border-destructive",
        "resize-y",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }

import * as React from "react"
import { Input as InputPrimitive } from "@base-ui/react/input"

import { cn } from "@/lib/utils"

/**
 * 输入框。取自 designs/linear.app.md 的 text-input：
 *   surface-1 底 / ink 文字 / rounded.md(8) / padding 8px 12px
 *   聚焦时边框转 hairline-strong + 品牌色淡外扩
 *
 * 与改造前的差别：原来 `bg-transparent` + `dark:bg-input/30`，
 * 深浅两套主题下表现不一致。Linear 的做法是输入框始终有 surface 底色，
 * 靠边框表达"可编辑"，这样两套主题只需换 token。
 */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-8 w-full min-w-0 rounded-md border border-hairline bg-surface-1 px-3 py-1 text-base outline-none transition-devflow",
        "file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground",
        "placeholder:text-ink-tertiary",
        "hover:border-hairline-strong",
        "focus-visible:border-ring focus-visible:shadow-[var(--focus-ring)]",
        "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50",
        "aria-invalid:border-destructive",
        "md:text-sm",
        className
      )}
      {...props}
    />
  )
}

export { Input }

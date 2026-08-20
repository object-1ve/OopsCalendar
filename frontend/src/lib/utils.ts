import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** shadcn/ui 类名合并工具(条件类名 + 去重冲突)。 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

import { Toaster as Sonner, type ToasterProps } from 'sonner'

/**
 * 全局消息提示(shadcn/ui 生态的 sonner toast,适配本项目深色主题)。
 * 在 App 根部挂载一次,任意组件通过 `import { toast } from 'sonner'` 调用:
 *   toast.success('已创建组别「医药」')
 *   toast.error('删除失败,请稍后重试')
 */
const Toaster = ({ theme = 'dark', ...props }: ToasterProps) => {
  return (
    <Sonner
      theme={theme}
      position="top-center"
      toastOptions={{
        style: {
          background: 'var(--bg-2)',
          border: '1px solid var(--border)',
          color: 'var(--text)',
        },
        classNames: {
          success: 'toast-success',
          error: 'toast-error',
        },
        actionButtonStyle: {
          background: 'var(--red)',
          color: '#fff',
          border: 'none',
          borderRadius: 8,
          fontSize: 12,
          padding: '4px 12px',
        },
        cancelButtonStyle: {
          background: 'var(--bg)',
          color: 'var(--text-dim)',
          border: '1px solid var(--border)',
          borderRadius: 8,
          fontSize: 12,
          padding: '4px 12px',
        },
      }}
      {...props}
    />
  )
}

export { Toaster }

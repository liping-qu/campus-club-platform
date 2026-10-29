/**
 * uni-ui 组件类型声明。
 *
 * uni-ui 以 .vue 源码形式分发，没有随包提供 .d.ts，
 * 在 strict 模式下直接 import 会报 TS7016。
 * 这里只声明本项目实际用到的组件与属性，避免为了通过类型检查引入一堆 any。
 */
declare module '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue' {
  import type { DefineComponent } from 'vue'

  const UniIcons: DefineComponent<{
    /** 图标名，例如 left / calendar / location */
    type?: string
    size?: number | string
    color?: string
    customPrefix?: string
  }>

  export default UniIcons
}

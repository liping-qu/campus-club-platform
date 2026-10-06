<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('校园社团通启动')
})
</script>

<style lang="scss">
@import './styles/theme.scss';

page {
  min-height: 100%;
  background: var(--app-bg);
  color: var(--app-text);
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', sans-serif;
}

view, text, button, input, scroll-view {
  box-sizing: border-box;
}
</style>

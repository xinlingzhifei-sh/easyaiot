import { computed, unref } from 'vue'

import { useRouter } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { resolveFullContent } from './fullContent'

/**
 * @description: Full screen display content
 */
export function useFullContent() {
  const appStore = useAppStore()
  const router = useRouter()
  const { currentRoute } = router

  // Whether to display the content in full screen without displaying the menu
  const getFullContent = computed(() => {
    const route = unref(currentRoute)
    return resolveFullContent(route, appStore.getProjectConfig.fullContent)
  })

  return { getFullContent }
}

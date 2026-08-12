import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { ChartEditStoreEnum } from '@/store/modules/chartEditStore/chartEditStore'
import type { ChartEditStorage as ChartEditStorageType } from '@/store/modules/chartEditStore/chartEditStore.d'

// store 相关
export const useStore = (localStorageInfo: ChartEditStorageType) => {
  const chartEditStore = useChartEditStore()
  chartEditStore.requestGlobalConfig = localStorageInfo[ChartEditStoreEnum.REQUEST_GLOBAL_CONFIG]
}

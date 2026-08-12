import countButton from './src/CountButton.vue'
import countdownInput from './src/CountdownInput.vue'
import { withInstall } from '@/utils/withInstall'

export const CountdownInput = withInstall(countdownInput)
export const CountButton = withInstall(countButton)

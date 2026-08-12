import type { ExtractPropTypes } from 'vue'
import { buttonProps } from './props'

type ButtonProps = Partial<ExtractPropTypes<typeof buttonProps>>

const clickWithPointerEvent: ButtonProps['onClick'] = (_event: MouseEvent) => undefined
const clickHandlersWithPointerEvent: ButtonProps['onClick'] = [
  (_event: MouseEvent) => undefined,
]

void clickWithPointerEvent
void clickHandlersWithPointerEvent

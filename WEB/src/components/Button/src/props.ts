import type { PropType } from 'vue'

const validColors = ['primary', 'error', 'warning', 'success', ''] as const
type ButtonColorType = (typeof validColors)[number]
type ButtonClickHandler = (event: MouseEvent) => any

export const buttonProps = {
  color: {
    type: String as PropType<ButtonColorType>,
    validator: (v: ButtonColorType) => validColors.includes(v),
    default: '',
  },
  loading: { type: Boolean },
  disabled: { type: Boolean },
  /**
   * Text before icon.
   */
  preIcon: { type: String },
  /**
   * Text after icon.
   */
  postIcon: { type: String },
  /**
   * preIcon and postIcon icon size.
   * @default: 14
   */
  iconSize: { type: Number, default: 14 },
  onClick: { type: [Function, Array] as PropType<ButtonClickHandler | ButtonClickHandler[]>, default: null },
}

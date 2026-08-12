import { onMounted, ref } from 'vue'
import axios from 'axios'

export interface CaseData {
  name: string
  image: string
  description?: string
}

const data = ref<CaseData[]>([])

export function useCaseData() {
  const data = [
    {
      name: '调剂宝',
      description: '一个专业的调剂助手，帮助你找到最适合自己的调剂方案。',
      image:
        'https://private-user-images.githubusercontent.com/45726436/455309534-d6ae0ca0-9b42-4c01-8024-90f3d9690765.jpg',
    },
  ]

  return {
    data,
  }
}

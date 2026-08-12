/**
 * 获取时间，带格式。
 */
type DateFormatParams = {
  timestamp?: string | number | Date | null
  format?: string
}

export function getDate({ timestamp = null, format = 'yyyy-MM-dd HH:mm:ss' }: DateFormatParams = {}) {
  const addZero = (num: number, len = 2) => `0${num}`.slice(-len)
  try {
    let formatDate = ''
    const date = timestamp ? new Date(timestamp) : new Date()
    const objData: Record<string, string | number> = {
      yyyy: date.getFullYear(),
      MM: addZero(date.getMonth() + 1),
      dd: addZero(date.getDate()),
      HH: addZero(date.getHours()),
      mm: addZero(date.getMinutes()),
      ss: addZero(date.getSeconds()),
    }

    format.split(' ').forEach((time) => {
      formatDate = formatDate.length ? `${formatDate} ` : formatDate
      const other = time.match(/[^A-Za-z]+/g) ?? []
      const parts = time.match(/[A-Za-z]+/g) ?? []
      parts.forEach((str, key) => {
        formatDate += `${objData[str]}${other[key] || ''}`
      })
    })
    return formatDate
  } catch (e) {
    return ''
  }
}

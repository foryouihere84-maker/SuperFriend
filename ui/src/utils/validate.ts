export const validateEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

export const validateUrl = (url: string): boolean => {
  try {
    new URL(url)
    return true
  } catch {
    return false
  }
}

export const validateRequired = (value: unknown): boolean => {
  if (value === null || value === undefined) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  return true
}

export const validateLength = (value: string, min: number, max: number): boolean => {
  const length = value.trim().length
  return length >= min && length <= max
}

export const validateNumber = (value: unknown): boolean => {
  return typeof value === 'number' && !isNaN(value as number)
}

export const validateRange = (value: number, min: number, max: number): boolean => {
  return value >= min && value <= max
}

import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

let seq = 0
export function uid(prefix = 'id') {
  seq += 1
  return `${prefix}_${Date.now().toString(36)}${seq.toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

export const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

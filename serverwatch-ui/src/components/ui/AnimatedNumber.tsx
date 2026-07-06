import { useRef, useEffect, useState } from 'react'

interface AnimatedNumberProps {
  value: number
  format: (n: number) => string
  duration?: number
}

export default function AnimatedNumber({ value, format, duration = 500 }: AnimatedNumberProps) {
  const [display, setDisplay] = useState(value)
  const fromRef = useRef(value)
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    const from = fromRef.current
    const startTs = performance.now()

    const tick = (now: number) => {
      const progress = Math.min((now - startTs) / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3) // cubic ease-out
      setDisplay(from + (value - from) * eased)
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick)
      } else {
        fromRef.current = value
      }
    }

    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current)
    rafRef.current = requestAnimationFrame(tick)

    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current)
    }
  }, [value, duration])

  return <>{format(display)}</>
}

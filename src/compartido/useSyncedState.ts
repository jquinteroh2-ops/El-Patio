import { useCallback, useEffect, useRef, useState } from 'react'
import { suscribir, type EventoSync } from './almacen'

interface Resultado<T> {
  datos: T
  /** Solo en la primera carga: los refrescos posteriores no vacian la pantalla. */
  cargando: boolean
  error: string | null
  /** Vuelve a consultar de inmediato. */
  refrescar: () => void
  /** Sube cada vez que llega un cambio de otra pestana. Sirve para animar. */
  sello: number
}

/**
 * Mantiene un dato de mockApi al dia con lo que pasa en cualquier pestana.
 *
 * Consulta al montar y vuelve a consultar cada vez que alguien escribe en la
 * base, sin importar si el cambio lo hizo esta pestana o la del mesero al otro
 * lado del salon. Eso es lo que hace que un pedido enviado desde el celular
 * aparezca solo en la pantalla de cocina.
 *
 *   const { datos: mesas } = useSyncedState(() => api.listarMesas(), [], [])
 */
export function useSyncedState<T>(
  consultar: () => Promise<T>,
  inicial: T,
  deps: unknown[] = [],
  /** Si se indica, solo refresca cuando el cambio toca alguna de estas claves. */
  observar?: string[],
): Resultado<T> {
  const [datos, setDatos] = useState<T>(inicial)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [sello, setSello] = useState(0)

  // Evita que una respuesta lenta pise a una mas reciente.
  const peticion = useRef(0)
  const montado = useRef(true)
  const consultarRef = useRef(consultar)
  consultarRef.current = consultar

  const ejecutar = useCallback(async () => {
    const id = ++peticion.current
    try {
      const resultado = await consultarRef.current()
      if (!montado.current || id !== peticion.current) return
      setDatos(resultado)
      setError(null)
    } catch (e) {
      if (!montado.current || id !== peticion.current) return
      setError(e instanceof Error ? e.message : 'No se pudo cargar la información')
    } finally {
      if (montado.current && id === peticion.current) setCargando(false)
    }
  }, [])

  useEffect(() => {
    montado.current = true
    void ejecutar()
    return () => {
      montado.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    const cancelar = suscribir((evento: EventoSync) => {
      const interesa =
        !observar ||
        observar.length === 0 ||
        evento.cambios.length === 0 ||
        evento.cambios.some((c) => observar.includes(c))
      if (!interesa) return
      setSello((s) => s + 1)
      void ejecutar()
    })
    return cancelar
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ejecutar, observar ? observar.join('|') : ''])

  return { datos, cargando, error, refrescar: ejecutar, sello }
}

/**
 * Reloj compartido para los cronometros de mesas y comandas.
 * Un solo intervalo por componente en vez de uno por tarjeta.
 */
export function useReloj(intervaloMs = 30000): number {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setAhora(Date.now()), intervaloMs)
    return () => window.clearInterval(id)
  }, [intervaloMs])
  return ahora
}

import { useCallback, useEffect, useRef, useState } from 'react'
import { canalConectado, suscribir, type EventoSync } from './almacen'

/**
 * Red de seguridad: cada cuanto se vuelve a preguntar por si acaso.
 *
 * El canal de tiempo real es quien avisa, y mientras esta vivo esto casi nunca
 * dispara nada. Existe porque un socket puede morirse sin avisar —un punto de
 * acceso que se reinicia, un proxy que corta lo que lleva rato callado, un
 * celular que suspende la pestana al bloquear la pantalla— y hasta ahora eso
 * dejaba la pantalla congelada en la foto que tenia al abrirse, sin ninguna
 * senal de que ya no era la verdad. La unica salida era recargar a mano.
 */
const REVISION_CON_CANAL_MS = 60000
const REVISION_SIN_CANAL_MS = 12000

/** Margen para que despertar la pantalla no dispare tres consultas seguidas. */
const MARGEN_DESPERTAR_MS = 3000

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
  /** Cuando se consulto por ultima vez, para no repetir de mas al despertar. */
  const ultimaConsulta = useRef(0)
  const montado = useRef(true)
  const consultarRef = useRef(consultar)
  consultarRef.current = consultar

  const ejecutar = useCallback(async () => {
    const id = ++peticion.current
    ultimaConsulta.current = Date.now()
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

  // La red de seguridad. Todo lo de aqui abajo es lo que hace que la pantalla
  // este al dia sin que nadie la recargue, tambien cuando el canal fallo.
  useEffect(() => {
    const revisar = () => {
      const espera = canalConectado() ? REVISION_CON_CANAL_MS : REVISION_SIN_CANAL_MS
      if (Date.now() - ultimaConsulta.current >= espera) void ejecutar()
    }

    // Se mira seguido y se consulta poco: el reloj corre cada pocos segundos,
    // pero solo dispara cuando de verdad ya paso el tiempo que toca.
    const reloj = window.setInterval(revisar, REVISION_SIN_CANAL_MS)

    /**
     * Volver a la pantalla es el momento en que los datos viejos se notan.
     *
     * Un celular bloqueado suspende la pestana y con ella el socket: al
     * desbloquearlo, la comandera ensena el salon de hace media hora. Esto lo
     * corrige antes de que al mesero le de tiempo de leerlo mal.
     */
    const alDespertar = () => {
      if (document.visibilityState !== 'visible') return
      if (Date.now() - ultimaConsulta.current < MARGEN_DESPERTAR_MS) return
      void ejecutar()
    }

    document.addEventListener('visibilitychange', alDespertar)
    window.addEventListener('focus', alDespertar)
    window.addEventListener('online', alDespertar)

    return () => {
      window.clearInterval(reloj)
      document.removeEventListener('visibilitychange', alDespertar)
      window.removeEventListener('focus', alDespertar)
      window.removeEventListener('online', alDespertar)
    }
  }, [ejecutar])

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

import { useEffect, useMemo, useState } from 'react'
import { Check } from 'lucide-react'
import { NOTAS_RAPIDAS } from '@/compartido/config'
import { formatoCOP } from '@/compartido/formato'
import type { ItemCarta, ModificadorSeleccionado } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { Contador } from '@/componentes/ui/Contador'
import { HojaInferior } from '@/componentes/ui/HojaInferior'

export interface SeleccionProducto {
  cantidad: number
  modificadores: ModificadorSeleccionado[]
  nota?: string
}

interface Props {
  item: ItemCarta | null
  /** Valores actuales cuando se esta editando una linea ya agregada. */
  inicial?: SeleccionProducto
  onCerrar: () => void
  onConfirmar: (seleccion: SeleccionProducto) => void
}

/**
 * Eleccion de termino, guarnicion y notas. Se abre sola cuando el producto
 * tiene modificadores obligatorios: en un restaurante de mantel no puede salir
 * una carne a la parrilla sin termino.
 */
export function HojaModificadores({ item, inicial, onCerrar, onConfirmar }: Props) {
  const [cantidad, setCantidad] = useState(1)
  const [unicos, setUnicos] = useState<Record<string, string>>({})
  const [multiples, setMultiples] = useState<Record<string, string[]>>({})
  const [textos, setTextos] = useState<Record<string, string>>({})
  const [nota, setNota] = useState('')
  const [intentado, setIntentado] = useState(false)

  // Cada vez que se abre para otro producto, el formulario arranca limpio.
  useEffect(() => {
    if (!item) return
    setIntentado(false)
    setCantidad(inicial?.cantidad ?? 1)
    setNota(inicial?.nota ?? '')

    const porNombre = new Map((inicial?.modificadores ?? []).map((m) => [m.nombre, m]))
    const u: Record<string, string> = {}
    const m: Record<string, string[]> = {}
    const t: Record<string, string> = {}

    for (const modificador of item.modificadores ?? []) {
      const previos = (inicial?.modificadores ?? []).filter((s) => s.nombre === modificador.nombre)
      if (modificador.tipo === 'seleccion_unica') u[modificador.nombre] = porNombre.get(modificador.nombre)?.valor ?? ''
      if (modificador.tipo === 'seleccion_multiple') m[modificador.nombre] = previos.map((p) => p.valor)
      if (modificador.tipo === 'texto_libre') t[modificador.nombre] = porNombre.get(modificador.nombre)?.valor ?? ''
    }

    setUnicos(u)
    setMultiples(m)
    setTextos(t)
  }, [item, inicial])

  const seleccionados = useMemo<ModificadorSeleccionado[]>(() => {
    if (!item?.modificadores) return []
    const salida: ModificadorSeleccionado[] = []

    for (const modificador of item.modificadores) {
      const precioDe = (valor: string) =>
        modificador.opciones?.find((o) => o.nombre === valor)?.precioAdicional ?? 0

      if (modificador.tipo === 'seleccion_unica') {
        const valor = unicos[modificador.nombre]
        if (valor) salida.push({ nombre: modificador.nombre, valor, precioAdicional: precioDe(valor) })
      } else if (modificador.tipo === 'seleccion_multiple') {
        for (const valor of multiples[modificador.nombre] ?? []) {
          salida.push({ nombre: modificador.nombre, valor, precioAdicional: precioDe(valor) })
        }
      } else {
        const valor = textos[modificador.nombre]?.trim()
        if (valor) salida.push({ nombre: modificador.nombre, valor, precioAdicional: 0 })
      }
    }
    return salida
  }, [item, unicos, multiples, textos])

  const faltantes = useMemo(
    () =>
      (item?.modificadores ?? [])
        .filter((m) => m.obligatorio && m.tipo !== 'texto_libre')
        .filter((m) =>
          m.tipo === 'seleccion_unica'
            ? !unicos[m.nombre]
            : (multiples[m.nombre] ?? []).length === 0,
        )
        .map((m) => m.nombre),
    [item, unicos, multiples],
  )

  if (!item) return null

  const adicionales = seleccionados.reduce((s, m) => s + m.precioAdicional, 0)
  const total = (item.precio + adicionales) * cantidad

  const confirmar = () => {
    setIntentado(true)
    if (faltantes.length > 0) return
    onConfirmar({ cantidad, modificadores: seleccionados, nota: nota.trim() || undefined })
  }

  const alternarMultiple = (nombre: string, valor: string) => {
    setMultiples((actuales) => {
      const previos = actuales[nombre] ?? []
      return {
        ...actuales,
        [nombre]: previos.includes(valor) ? previos.filter((v) => v !== valor) : [...previos, valor],
      }
    })
  }

  const alternarNota = (texto: string) => {
    setNota((actual) => {
      const partes = actual.split(' · ').filter(Boolean)
      return partes.includes(texto)
        ? partes.filter((p) => p !== texto).join(' · ')
        : [...partes, texto].join(' · ')
    })
  }

  const notasActivas = nota.split(' · ').filter(Boolean)

  return (
    <HojaInferior
      abierta
      titulo={item.nombre}
      descripcion={formatoCOP(item.precio)}
      onCerrar={onCerrar}
      pie={
        <div className="flex items-center gap-3">
          <Contador valor={cantidad} onCambiar={setCantidad} minimo={1} />
          <Boton variante="principal" tamano="grande" bloque onClick={confirmar}>
            {inicial ? 'Guardar' : 'Agregar'} · {formatoCOP(total)}
          </Boton>
        </div>
      }
    >
      <div className="space-y-5">
        {(item.modificadores ?? []).map((modificador) => (
          <div key={modificador.id}>
            <p className="mb-2 flex items-center gap-2 text-sm font-medium text-crema-100">
              {modificador.nombre}
              {modificador.obligatorio ? (
                <span className="rounded-md bg-oro-500/15 px-1.5 py-0.5 text-xs text-oro-300">
                  Obligatorio
                </span>
              ) : (
                <span className="text-xs text-noche-500">Opcional</span>
              )}
            </p>

            {modificador.tipo === 'texto_libre' ? (
              <input
                value={textos[modificador.nombre] ?? ''}
                onChange={(e) => setTextos((t) => ({ ...t, [modificador.nombre]: e.target.value }))}
                placeholder="Escribe aquí"
                className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
              />
            ) : (
              <div className="grid grid-cols-2 gap-2">
                {(modificador.opciones ?? []).map((opcion) => {
                  const activo =
                    modificador.tipo === 'seleccion_unica'
                      ? unicos[modificador.nombre] === opcion.nombre
                      : (multiples[modificador.nombre] ?? []).includes(opcion.nombre)

                  return (
                    <button
                      key={opcion.nombre}
                      type="button"
                      onClick={() =>
                        modificador.tipo === 'seleccion_unica'
                          ? setUnicos((u) => ({ ...u, [modificador.nombre]: opcion.nombre }))
                          : alternarMultiple(modificador.nombre, opcion.nombre)
                      }
                      className={`flex min-h-toque items-center justify-between gap-2 rounded-xl border px-3 text-left text-sm transition active:scale-[0.98] ${
                        activo
                          ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                          : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                      }`}
                    >
                      <span className="min-w-0">
                        <span className="block truncate">{opcion.nombre}</span>
                        {opcion.precioAdicional > 0 && (
                          <span className="block text-xs text-oro-300">
                            +{formatoCOP(opcion.precioAdicional)}
                          </span>
                        )}
                      </span>
                      {activo && <Check className="h-4 w-4 shrink-0 text-oro-400" aria-hidden />}
                    </button>
                  )
                })}
              </div>
            )}

            {intentado && faltantes.includes(modificador.nombre) && (
              <p className="mt-1.5 text-sm text-estado-demorado">Elige una opción para continuar</p>
            )}
          </div>
        ))}

        <div>
          <p className="mb-2 text-sm font-medium text-crema-100">Nota para cocina</p>
          <div className="mb-2 flex flex-wrap gap-2">
            {NOTAS_RAPIDAS.map((texto) => {
              const activo = notasActivas.includes(texto)
              return (
                <button
                  key={texto}
                  type="button"
                  onClick={() => alternarNota(texto)}
                  className={`min-h-[40px] rounded-xl border px-3 text-sm transition active:scale-95 ${
                    activo
                      ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                      : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                  }`}
                >
                  {texto}
                </button>
              )
            })}
          </div>
          <input
            value={nota}
            onChange={(e) => setNota(e.target.value)}
            placeholder="Otra indicación"
            className="min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none"
          />
        </div>
      </div>
    </HojaInferior>
  )
}

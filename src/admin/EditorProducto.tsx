import { useEffect, useRef, useState } from 'react'
import { ImagePlus, Plus, Trash2, X } from 'lucide-react'
import * as api from '@/compartido/mockApi'
import { conFotosDePlato, fotosDePlato } from '@/compartido/fotosDePlato'
import type { CategoriaCarta, ItemCarta, Modificador, TipoModificador } from '@/compartido/tipos'
import { Boton } from '@/componentes/ui/Boton'
import { HojaInferior } from '@/componentes/ui/HojaInferior'
import { Interruptor } from '@/componentes/ui/Interruptor'

interface Props {
  abierto: boolean
  /** null cuando se esta creando un producto nuevo. */
  producto: ItemCarta | null
  categorias: CategoriaCarta[]
  guardando: boolean
  onCerrar: () => void
  onGuardar: (producto: ItemCarta) => void
  onEliminar?: (id: string) => void
}

const TIPOS: { id: TipoModificador; etiqueta: string }[] = [
  { id: 'seleccion_unica', etiqueta: 'Elegir una' },
  { id: 'seleccion_multiple', etiqueta: 'Elegir varias' },
  { id: 'texto_libre', etiqueta: 'Texto libre' },
]

const CAMPO =
  'min-h-toque w-full rounded-xl border border-noche-700 bg-noche-850 px-3.5 text-crema-100 placeholder:text-noche-500 focus:border-oro-500 focus:outline-none'

const vacio = (categoriaId: string): ItemCarta => ({
  id: '',
  categoriaId,
  nombre: '',
  descripcion: '',
  precio: 0,
  precioPromocional: null,
  promocionDesde: null,
  promocionHasta: null,
  disponible: true,
  tiempoPreparacionMin: 15,
  destino: 'cocina',
  modificadores: [],
  imagen: null,
  galeria: [],
})

export function EditorProducto({
  abierto,
  producto,
  categorias,
  guardando,
  onCerrar,
  onGuardar,
  onEliminar,
}: Props) {
  const [borrador, setBorrador] = useState<ItemCarta>(vacio(categorias[0]?.id ?? ''))
  const [error, setError] = useState<string | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const archivo = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!abierto) return
    setError(null)
    setBorrador(producto ? structuredClone(producto) : vacio(categorias[0]?.id ?? ''))
  }, [abierto, producto, categorias])

  const cambiar = (cambios: Partial<ItemCarta>) => setBorrador((b) => ({ ...b, ...cambios }))

  /** Las fotos del borrador como una lista, con la portada de primera. */
  const fotos = fotosDePlato(borrador)

  const ponerFotos = (nuevas: string[]) => setBorrador((b) => conFotosDePlato(b, nuevas))

  const elegirFotos = async (archivos: FileList | null) => {
    const elegidos = Array.from(archivos ?? [])
    if (elegidos.length === 0) return
    setError(null)
    setSubiendo(true)
    try {
      /*
       * Suben ya, antes de guardar el plato. Así el administrador las ve en
       * pantalla y decide, y si después cambia el precio no tiene que volver a
       * subir los megas desde el celular.
       *
       * Una detrás de otra y no todas a la vez: el almacén las reduce al
       * recibirlas, y ocho fotos de doce megas saliendo en paralelo desde un
       * celular en datos es justo lo que hace que a la mitad se caiga una y no
       * se sepa cuáles llegaron. Y cada una se añade EN CUANTO llega, no al
       * final: si la quinta falla, las cuatro primeras ya están puestas y no
       * hay que volver a subirlas.
       */
      for (const elegido of elegidos) {
        const nombre = await api.subirImagenCarta(elegido)
        setBorrador((b) => conFotosDePlato(b, [...fotosDePlato(b), nombre]))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo subir la foto')
    } finally {
      setSubiendo(false)
      if (archivo.current) archivo.current.value = ''
    }
  }

  /** La lleva al primer puesto sin perder el orden de las demás. */
  const hacerPortada = (foto: string) => ponerFotos([foto, ...fotos.filter((f) => f !== foto)])

  const cambiarModificador = (indice: number, cambios: Partial<Modificador>) =>
    setBorrador((b) => ({
      ...b,
      modificadores: (b.modificadores ?? []).map((m, i) => (i === indice ? { ...m, ...cambios } : m)),
    }))

  const agregarModificador = () =>
    setBorrador((b) => ({
      ...b,
      modificadores: [
        ...(b.modificadores ?? []),
        {
          id: `mod_${Date.now().toString(36)}`,
          nombre: '',
          tipo: 'seleccion_unica',
          obligatorio: false,
          opciones: [{ nombre: '', precioAdicional: 0 }],
        },
      ],
    }))

  const quitarModificador = (indice: number) =>
    setBorrador((b) => ({
      ...b,
      modificadores: (b.modificadores ?? []).filter((_, i) => i !== indice),
    }))

  const guardar = () => {
    if (!borrador.nombre.trim()) {
      setError('El producto necesita un nombre')
      return
    }
    if (borrador.precio <= 0) {
      setError('El precio debe ser mayor que cero')
      return
    }
    // La base rechaza esto igual, pero el mesero merece enterarse aquí y no
    // después de guardar: es el error de teclado que sube el precio creyendo
    // que lo baja.
    if (borrador.precioPromocional != null && borrador.precioPromocional >= borrador.precio) {
      setError('El precio de promoción tiene que ser menor que el normal')
      return
    }
    if (
      borrador.promocionDesde &&
      borrador.promocionHasta &&
      borrador.promocionDesde > borrador.promocionHasta
    ) {
      setError('La promoción terminaría antes de empezar')
      return
    }
    onGuardar({
      ...borrador,
      nombre: borrador.nombre.trim(),
      descripcion: borrador.descripcion.trim(),
      modificadores: (borrador.modificadores ?? [])
        .filter((m) => m.nombre.trim())
        .map((m) => ({
          ...m,
          nombre: m.nombre.trim(),
          opciones:
            m.tipo === 'texto_libre'
              ? undefined
              : (m.opciones ?? []).filter((o) => o.nombre.trim()),
        })),
    })
  }

  return (
    <HojaInferior
      abierta={abierto}
      titulo={producto ? 'Editar producto' : 'Nuevo producto'}
      descripcion={producto ? producto.nombre : 'Se agrega a la carta y a la comandera'}
      onCerrar={onCerrar}
      onEnviar={guardar}
      pie={
        <div className="flex gap-2">
          {producto && onEliminar && (
            <Boton
              variante="peligro"
              tamano="grande"
              icono={<Trash2 className="h-4 w-4" />}
              onClick={() => onEliminar(producto.id)}
            >
              Eliminar
            </Boton>
          )}
          <Boton variante="principal" tamano="grande" bloque cargando={guardando} type="submit">
            Guardar
          </Boton>
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <p className="rounded-xl border border-estado-demorado/40 bg-estado-demorado-suave px-3 py-2 text-sm text-estado-demorado">
            {error}
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">Nombre</span>
          <input
            value={borrador.nombre}
            onChange={(e) => cambiar({ nombre: e.target.value })}
            placeholder="Róbalo al bijao"
            className={CAMPO}
          />
        </label>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Descripción
          </span>
          <textarea
            value={borrador.descripcion}
            onChange={(e) => cambiar({ descripcion: e.target.value })}
            rows={3}
            placeholder="Lo que lee el cliente en la carta"
            className={`${CAMPO} py-3`}
          />
        </label>

        {/* ---------- Las fotos ---------- */}
        <div>
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Fotos <span className="text-noche-500">· la primera es la que sale en la carta</span>
          </span>

          {/*
            Una sola lista, y la primera manda.

            Por dentro el plato guarda la portada aparte de las demás, pero
            partir eso en dos cajones en pantalla —«foto principal» y «más
            fotos»— obliga a explicar la diferencia y a moverlas de un cajón a
            otro. Aquí se arrastra la que se quiera al primer puesto con un
            botón y ya está.
          */}
          {fotos.length > 0 && (
            <ul className="mb-2 grid grid-cols-3 gap-2">
              {fotos.map((foto, i) => (
                <li
                  key={foto}
                  className="relative overflow-hidden rounded-xl border border-noche-700"
                >
                  <img
                    src={api.urlImagenCarta(foto, 400)}
                    alt=""
                    className="aspect-square w-full object-cover"
                  />

                  <button
                    type="button"
                    aria-label={`Quitar la foto ${i + 1}`}
                    onClick={() => ponerFotos(fotos.filter((f) => f !== foto))}
                    className="absolute right-1 top-1 rounded-full bg-noche-950/85 p-1.5 text-crema-100"
                  >
                    <X className="h-3.5 w-3.5" aria-hidden />
                  </button>

                  {i === 0 ? (
                    <span className="absolute inset-x-0 bottom-0 bg-noche-950/85 py-1 text-center text-[0.65rem] uppercase tracking-wide text-oro-300">
                      Portada
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => hacerPortada(foto)}
                      className="absolute inset-x-0 bottom-0 bg-noche-950/85 py-1 text-center text-[0.65rem] uppercase tracking-wide text-noche-300 hover:text-crema-100"
                    >
                      Hacer portada
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            onClick={() => archivo.current?.click()}
            disabled={subiendo}
            className="flex min-h-[64px] w-full flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed border-noche-700 text-noche-300"
          >
            <ImagePlus className="h-5 w-5" aria-hidden />
            <span className="text-sm">
              {subiendo
                ? 'Subiendo las fotos…'
                : fotos.length === 0
                  ? 'Tocar para elegir fotos'
                  : 'Agregar más fotos'}
            </span>
          </button>

          {/* `multiple`: se eligen todas las del plato de una vez desde el
              carrete, que es como están guardadas después de la sesión. */}
          <input
            ref={archivo}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => void elegirFotos(e.target.files)}
          />
          <p className="mt-1.5 text-xs text-noche-400">
            Se reducen solas al subirlas. El cliente las ve todas al tocar el plato en la carta. Sin
            fotos, el plato igual sale, solo que sin imagen.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
              Precio
            </span>
            <input
              inputMode="numeric"
              value={borrador.precio || ''}
              onChange={(e) => cambiar({ precio: Number(e.target.value.replace(/\D/g, '')) || 0 })}
              className={CAMPO}
            />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
              Preparación (min)
            </span>
            <input
              inputMode="numeric"
              value={borrador.tiempoPreparacionMin || ''}
              onChange={(e) =>
                cambiar({ tiempoPreparacionMin: Number(e.target.value.replace(/\D/g, '')) || 0 })
              }
              className={CAMPO}
            />
          </label>
        </div>

        {/* ---------- Precio promocional ----------
            El descuento se pone como PRECIO, no como rebaja sobre la cuenta.
            Así la venta ocurre a este valor y el impuesto al consumo, la
            propina y lo que se le reporta al ERP se calculan sobre él sin
            ninguna regla aparte. */}
        <div className="rounded-xl border border-noche-800 bg-noche-900/60 p-3">
          <div className="mb-2.5 flex items-center justify-between">
            <span className="text-xs uppercase tracking-wide text-noche-400">
              Precio en promoción
            </span>
            {borrador.precioPromocional ? (
              <button
                type="button"
                className="text-xs text-oro-300"
                onClick={() =>
                  cambiar({ precioPromocional: null, promocionDesde: null, promocionHasta: null })
                }
              >
                Quitar la promoción
              </button>
            ) : null}
          </div>

          <input
            inputMode="numeric"
            placeholder="Vacío: se vende al precio normal"
            value={borrador.precioPromocional || ''}
            onChange={(e) =>
              cambiar({ precioPromocional: Number(e.target.value.replace(/\D/g, '')) || null })
            }
            className={CAMPO}
          />

          {borrador.precioPromocional ? (
            <>
              <div className="mt-3 grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
                    Desde
                  </span>
                  <input
                    type="date"
                    value={borrador.promocionDesde ?? ''}
                    onChange={(e) => cambiar({ promocionDesde: e.target.value || null })}
                    className={CAMPO}
                  />
                </label>
                <label className="block">
                  <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
                    Hasta
                  </span>
                  <input
                    type="date"
                    value={borrador.promocionHasta ?? ''}
                    onChange={(e) => cambiar({ promocionHasta: e.target.value || null })}
                    className={CAMPO}
                  />
                </label>
              </div>
              <p className="mt-1.5 text-xs text-noche-400">
                Sin fechas, la promoción vale mientras el precio esté puesto. Con fechas, empieza y
                termina sola.
              </p>
            </>
          ) : null}
        </div>

        <label className="block">
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            Categoría
          </span>
          <select
            value={borrador.categoriaId}
            onChange={(e) => cambiar({ categoriaId: e.target.value })}
            className={CAMPO}
          >
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
        </label>

        <div>
          <span className="mb-1.5 block text-xs uppercase tracking-wide text-noche-400">
            ¿A qué pantalla va la comanda?
          </span>
          <div className="grid grid-cols-2 gap-2">
            {(['cocina', 'bar'] as const).map((destino) => (
              <button
                key={destino}
                type="button"
                onClick={() => cambiar({ destino })}
                className={`min-h-toque rounded-xl border text-sm font-medium transition ${
                  borrador.destino === destino
                    ? 'border-oro-500 bg-oro-500/15 text-crema-100'
                    : 'border-noche-700 bg-noche-850 text-noche-300 hover:bg-noche-800'
                }`}
              >
                {destino === 'cocina' ? 'Cocina' : 'Barra'}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-between gap-3 rounded-xl border border-noche-700 bg-noche-850 px-3.5 py-3">
          <span className="text-sm text-crema-100">Disponible hoy</span>
          <Interruptor
            activo={borrador.disponible}
            onCambiar={(disponible) => cambiar({ disponible })}
            etiqueta="Disponible"
            descripcion={borrador.disponible ? 'En carta' : 'Agotado'}
          />
        </div>

        {/* ---------- Modificadores ---------- */}
        <div>
          <div className="mb-2 flex items-center justify-between gap-2">
            <span className="text-sm font-semibold text-crema-100">Modificadores</span>
            <Boton tamano="compacto" icono={<Plus className="h-4 w-4" />} onClick={agregarModificador}>
              Agregar
            </Boton>
          </div>

          <div className="space-y-3">
            {(borrador.modificadores ?? []).map((modificador, indice) => (
              <div key={modificador.id} className="rounded-xl border border-noche-700 bg-noche-850 p-3">
                <div className="flex gap-2">
                  <input
                    value={modificador.nombre}
                    onChange={(e) => cambiarModificador(indice, { nombre: e.target.value })}
                    placeholder="Término de la carne"
                    className={CAMPO}
                  />
                  <button
                    type="button"
                    onClick={() => quitarModificador(indice)}
                    aria-label="Quitar modificador"
                    className="flex h-toque w-11 shrink-0 items-center justify-center rounded-xl text-noche-500 transition hover:bg-estado-demorado/15 hover:text-estado-demorado"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </button>
                </div>

                <div className="mt-2 flex flex-wrap gap-1.5">
                  {TIPOS.map((tipo) => (
                    <button
                      key={tipo.id}
                      type="button"
                      onClick={() => cambiarModificador(indice, { tipo: tipo.id })}
                      className={`min-h-[36px] rounded-lg border px-2.5 text-xs transition ${
                        modificador.tipo === tipo.id
                          ? 'border-oro-500 bg-oro-500/15 text-oro-300'
                          : 'border-noche-700 bg-noche-900 text-noche-400'
                      }`}
                    >
                      {tipo.etiqueta}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() =>
                      cambiarModificador(indice, { obligatorio: !modificador.obligatorio })
                    }
                    className={`min-h-[36px] rounded-lg border px-2.5 text-xs transition ${
                      modificador.obligatorio
                        ? 'border-estado-proceso/50 bg-estado-proceso/15 text-estado-proceso'
                        : 'border-noche-700 bg-noche-900 text-noche-400'
                    }`}
                  >
                    {modificador.obligatorio ? 'Obligatorio' : 'Opcional'}
                  </button>
                </div>

                {modificador.tipo !== 'texto_libre' && (
                  <ul className="mt-2 space-y-1.5">
                    {(modificador.opciones ?? []).map((opcion, io) => (
                      <li key={io} className="flex gap-2">
                        <input
                          value={opcion.nombre}
                          onChange={(e) =>
                            cambiarModificador(indice, {
                              opciones: (modificador.opciones ?? []).map((o, i) =>
                                i === io ? { ...o, nombre: e.target.value } : o,
                              ),
                            })
                          }
                          placeholder="Término medio"
                          className={`${CAMPO} flex-1`}
                        />
                        <input
                          inputMode="numeric"
                          value={opcion.precioAdicional || ''}
                          onChange={(e) =>
                            cambiarModificador(indice, {
                              opciones: (modificador.opciones ?? []).map((o, i) =>
                                i === io
                                  ? {
                                      ...o,
                                      precioAdicional: Number(e.target.value.replace(/\D/g, '')) || 0,
                                    }
                                  : o,
                              ),
                            })
                          }
                          placeholder="+$0"
                          className={`${CAMPO} w-24`}
                        />
                        <button
                          type="button"
                          onClick={() =>
                            cambiarModificador(indice, {
                              opciones: (modificador.opciones ?? []).filter((_, i) => i !== io),
                            })
                          }
                          aria-label="Quitar opción"
                          className="flex h-toque w-10 shrink-0 items-center justify-center rounded-xl text-noche-500 transition hover:text-estado-demorado"
                        >
                          <Trash2 className="h-3.5 w-3.5" aria-hidden />
                        </button>
                      </li>
                    ))}
                    <li>
                      <button
                        type="button"
                        onClick={() =>
                          cambiarModificador(indice, {
                            opciones: [...(modificador.opciones ?? []), { nombre: '', precioAdicional: 0 }],
                          })
                        }
                        className="text-xs font-medium text-oro-300 hover:underline"
                      >
                        + Otra opción
                      </button>
                    </li>
                  </ul>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </HojaInferior>
  )
}

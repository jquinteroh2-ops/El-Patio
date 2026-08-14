import { calcularCuenta } from './calculos'
import { INC_POR_DEFECTO } from './config'
import { claveDia } from './formato'
import type {
  BaseDatos,
  CargoAdicional,
  CategoriaCarta,
  CierreCaja,
  ItemCarta,
  ItemOrden,
  Mesa,
  Modificador,
  Orden,
  Pago,
  Reserva,
  Usuario,
} from './tipos'

/**
 * Datos de demostracion. Todos ficticios.
 *
 * Nada aqui usa fechas fijas: el salon, la cocina y el historico se construyen
 * contra la hora en que se abre la aplicacion, para que la demostracion se vea
 * viva a cualquier hora del dia.
 */

// ---------------------------------------------------------------------------
// Aleatoriedad reproducible
// ---------------------------------------------------------------------------

let semilla = 20260813

function aleatorio(): number {
  semilla = (semilla * 1664525 + 1013904223) % 4294967296
  return semilla / 4294967296
}

const entre = (min: number, max: number): number => Math.floor(aleatorio() * (max - min + 1)) + min
const uno = <T>(lista: readonly T[]): T => lista[Math.floor(aleatorio() * lista.length)]

const haceMinutos = (minutos: number): string => new Date(Date.now() - minutos * 60000).toISOString()

// ---------------------------------------------------------------------------
// Personal
// ---------------------------------------------------------------------------

export const USUARIOS: Usuario[] = [
  { id: 'u1', nombre: 'María Fernanda Ospina', rol: 'mesero', usuario: 'mesero', clave: '1234', activo: true },
  { id: 'u2', nombre: 'Jhon Alexis Padilla', rol: 'cocina', usuario: 'cocina', clave: '1234', activo: true },
  { id: 'u3', nombre: 'Katherine Villalba', rol: 'cajero', usuario: 'cajero', clave: '1234', activo: true },
  { id: 'u4', nombre: 'Álvaro Restrepo Díaz', rol: 'administrador', usuario: 'admin', clave: '1234', activo: true },
  { id: 'u5', nombre: 'Deivis Cabarcas', rol: 'mesero', usuario: 'mesero2', clave: '1234', activo: true },
]

/** Los cuatro roles que se muestran en la pantalla de acceso. */
export const CREDENCIALES_DEMO = [
  { rol: 'Mesero', usuario: 'mesero', clave: '1234', destino: 'Comandera' },
  { rol: 'Cocina', usuario: 'cocina', clave: '1234', destino: 'Pantalla de cocina' },
  { rol: 'Cajero', usuario: 'cajero', clave: '1234', destino: 'Panel administrativo' },
  { rol: 'Administrador', usuario: 'admin', clave: '1234', destino: 'Panel completo' },
] as const

// ---------------------------------------------------------------------------
// Salon
// ---------------------------------------------------------------------------

export const MESAS: Mesa[] = [
  { id: 'm1', numero: 1, zona: 'salon', capacidad: 2, estado: 'libre' },
  { id: 'm2', numero: 2, zona: 'salon', capacidad: 2, estado: 'libre' },
  { id: 'm3', numero: 3, zona: 'salon', capacidad: 4, estado: 'libre' },
  { id: 'm4', numero: 4, zona: 'salon', capacidad: 4, estado: 'libre' },
  { id: 'm5', numero: 5, zona: 'salon', capacidad: 4, estado: 'libre' },
  { id: 'm6', numero: 6, zona: 'salon', capacidad: 6, estado: 'libre' },
  { id: 'm7', numero: 7, zona: 'salon', capacidad: 6, estado: 'libre' },
  { id: 'm8', numero: 8, zona: 'salon', capacidad: 4, estado: 'libre' },
  { id: 'm9', numero: 9, zona: 'salon', capacidad: 2, estado: 'libre' },
  { id: 'm10', numero: 10, zona: 'salon', capacidad: 8, estado: 'libre' },
  { id: 'm11', numero: 11, nombre: 'Terraza 1', zona: 'terraza', capacidad: 4, estado: 'libre' },
  { id: 'm12', numero: 12, nombre: 'Terraza 2', zona: 'terraza', capacidad: 4, estado: 'libre' },
  { id: 'm13', numero: 13, nombre: 'Terraza 3', zona: 'terraza', capacidad: 6, estado: 'libre' },
  { id: 'm14', numero: 14, nombre: 'Terraza 4', zona: 'terraza', capacidad: 6, estado: 'libre' },
  { id: 'm15', numero: 15, nombre: 'Terraza 5', zona: 'terraza', capacidad: 2, estado: 'libre' },
  { id: 'm16', numero: 16, nombre: 'Privado 1', zona: 'privado', capacidad: 8, estado: 'libre' },
  { id: 'm17', numero: 17, nombre: 'Privado 2', zona: 'privado', capacidad: 10, estado: 'libre' },
  { id: 'm18', numero: 18, nombre: 'Privado 3', zona: 'privado', capacidad: 12, estado: 'libre' },
]

// ---------------------------------------------------------------------------
// Carta
// ---------------------------------------------------------------------------

export const CATEGORIAS: CategoriaCarta[] = [
  { id: 'c1', nombre: 'Entradas y picadas', orden: 1 },
  { id: 'c2', nombre: 'Fusión de autor', orden: 2 },
  { id: 'c3', nombre: 'Carnes y parrilla', orden: 3 },
  { id: 'c4', nombre: 'Pescados y mariscos', orden: 4 },
  { id: 'c5', nombre: 'Pastas y risottos', orden: 5 },
  { id: 'c6', nombre: 'Guarniciones', orden: 6 },
  { id: 'c7', nombre: 'Postres', orden: 7 },
  { id: 'c8', nombre: 'Coctelería de autor', orden: 8 },
  { id: 'c9', nombre: 'Coctelería clásica', orden: 9 },
  { id: 'c10', nombre: 'Cervezas y bebidas', orden: 10 },
]

const modTermino: Modificador = {
  id: 'mod_termino',
  nombre: 'Término de la carne',
  tipo: 'seleccion_unica',
  obligatorio: true,
  opciones: [
    { nombre: 'Sellado', precioAdicional: 0 },
    { nombre: 'Término medio', precioAdicional: 0 },
    { nombre: 'Tres cuartos', precioAdicional: 0 },
    { nombre: 'Bien asado', precioAdicional: 0 },
  ],
}

const modGuarnicion: Modificador = {
  id: 'mod_guarnicion',
  nombre: 'Guarnición',
  tipo: 'seleccion_unica',
  obligatorio: true,
  opciones: [
    { nombre: 'Papa criolla al romero', precioAdicional: 0 },
    { nombre: 'Puré de ñame', precioAdicional: 0 },
    { nombre: 'Yuca al vapor', precioAdicional: 0 },
    { nombre: 'Ensalada fresca', precioAdicional: 0 },
    { nombre: 'Vegetales baby', precioAdicional: 3000 },
    { nombre: 'Risotto de champiñones', precioAdicional: 9000 },
  ],
}

const modSinIngredientes: Modificador = {
  id: 'mod_sin',
  nombre: 'Preparar sin',
  tipo: 'seleccion_multiple',
  obligatorio: false,
  opciones: [
    { nombre: 'Sin cebolla', precioAdicional: 0 },
    { nombre: 'Sin cilantro', precioAdicional: 0 },
    { nombre: 'Sin picante', precioAdicional: 0 },
    { nombre: 'Sin queso', precioAdicional: 0 },
  ],
}

const modPreparacionPescado: Modificador = {
  id: 'mod_preparacion',
  nombre: 'Preparación',
  tipo: 'seleccion_unica',
  obligatorio: true,
  opciones: [
    { nombre: 'Frito', precioAdicional: 0 },
    { nombre: 'A la plancha', precioAdicional: 0 },
    { nombre: 'Al vapor', precioAdicional: 0 },
  ],
}

const modPicante: Modificador = {
  id: 'mod_picante',
  nombre: 'Punto de picante',
  tipo: 'seleccion_unica',
  obligatorio: false,
  opciones: [
    { nombre: 'Sin picante', precioAdicional: 0 },
    { nombre: 'Suave', precioAdicional: 0 },
    { nombre: 'Fuerte', precioAdicional: 0 },
  ],
}

const modBar: Modificador = {
  id: 'mod_bar',
  nombre: 'Preparación del bar',
  tipo: 'seleccion_multiple',
  obligatorio: false,
  opciones: [
    { nombre: 'Sin hielo', precioAdicional: 0 },
    { nombre: 'Menos dulce', precioAdicional: 0 },
    { nombre: 'Doble licor', precioAdicional: 9000 },
  ],
}

const modLicorBase: Modificador = {
  id: 'mod_licor',
  nombre: 'Licor base',
  tipo: 'seleccion_unica',
  obligatorio: true,
  opciones: [
    { nombre: 'Ron', precioAdicional: 0 },
    { nombre: 'Vodka', precioAdicional: 0 },
    { nombre: 'Ginebra', precioAdicional: 4000 },
    { nombre: 'Tequila', precioAdicional: 4000 },
  ],
}

const modDedicatoria: Modificador = {
  id: 'mod_dedicatoria',
  nombre: 'Dedicatoria en el plato',
  tipo: 'texto_libre',
  obligatorio: false,
}

export const CARTA: ItemCarta[] = [
  // --- Entradas y picadas ---
  {
    id: 'p01',
    categoriaId: 'c1',
    nombre: 'Croquetas de arroz de coco',
    descripcion: 'Rellenas de posta negra cartagenera, queso criollo y suero costeño.',
    precio: 22000,
    disponible: true,
    tiempoPreparacionMin: 12,
    destino: 'cocina',
  },
  {
    id: 'p02',
    categoriaId: 'c1',
    nombre: 'Ceviche El Patio',
    descripcion: 'Pescado del día en leche de tigre de maracuyá, camarón y chips de plátano.',
    precio: 32000,
    disponible: true,
    tiempoPreparacionMin: 10,
    destino: 'cocina',
    modificadores: [modPicante],
  },
  {
    id: 'p03',
    categoriaId: 'c1',
    nombre: 'Carpaccio de pulpo',
    descripcion: 'Láminas finas de pulpo, aceite de oliva, alcaparras y limón amarillo.',
    precio: 34000,
    disponible: true,
    tiempoPreparacionMin: 10,
    destino: 'cocina',
  },
  {
    id: 'p04',
    categoriaId: 'c1',
    nombre: 'Tartar de atún y aguacate',
    descripcion: 'Atún fresco marinado en soya y ajonjolí sobre cama de aguacate.',
    precio: 35000,
    disponible: true,
    tiempoPreparacionMin: 12,
    destino: 'cocina',
    modificadores: [modSinIngredientes],
  },
  {
    id: 'p05',
    categoriaId: 'c1',
    nombre: 'Chicharrón crocante',
    descripcion: 'Panceta de cerdo crocante con guacamole, patacón y suero costeño.',
    precio: 26000,
    disponible: true,
    tiempoPreparacionMin: 15,
    destino: 'cocina',
  },
  {
    id: 'p06',
    categoriaId: 'c1',
    nombre: 'Alitas glaseadas en tamarindo',
    descripcion: 'Ocho alitas con glaseado de tamarindo y ajonjolí tostado.',
    precio: 28000,
    disponible: true,
    tiempoPreparacionMin: 18,
    destino: 'cocina',
    modificadores: [modPicante],
  },
  {
    id: 'p07',
    categoriaId: 'c1',
    nombre: 'Burrata criolla',
    descripcion: 'Burrata fresca con tomates asados, albahaca y reducción de panela.',
    precio: 30000,
    disponible: true,
    tiempoPreparacionMin: 8,
    destino: 'cocina',
  },
  {
    id: 'p08',
    categoriaId: 'c1',
    nombre: 'Empanaditas de langostino',
    descripcion: 'Seis unidades con alioli de ají amarillo.',
    precio: 24000,
    disponible: true,
    tiempoPreparacionMin: 14,
    destino: 'cocina',
  },

  // --- Fusion de autor ---
  {
    id: 'p09',
    categoriaId: 'c2',
    nombre: 'Róbalo al bijao',
    descripcion:
      'Cocción lenta en hoja de bijao sobre arroz cremoso de ají amarillo, leche de coco y achiote.',
    precio: 58000,
    disponible: true,
    tiempoPreparacionMin: 25,
    destino: 'cocina',
    modificadores: [modSinIngredientes],
  },
  {
    id: 'p10',
    categoriaId: 'c2',
    nombre: 'Lomo encostrado en quinua',
    descripcion: 'Medallón de lomo fino sobre arroz cremoso de cilantro y frutos del mar.',
    precio: 64000,
    disponible: true,
    tiempoPreparacionMin: 24,
    destino: 'cocina',
    modificadores: [modTermino],
  },
  {
    id: 'p11',
    categoriaId: 'c2',
    nombre: 'Cerdo laqueado en panela',
    descripcion: 'Lomo de cerdo laqueado en panela y soya con puré de ñame y encurtido de cebolla.',
    precio: 52000,
    disponible: true,
    tiempoPreparacionMin: 22,
    destino: 'cocina',
  },
  {
    id: 'p12',
    categoriaId: 'c2',
    nombre: 'Arroz cremoso de jaiba y coco',
    descripcion: 'Carne de jaiba, leche de coco y bisque, terminado con maíz tierno.',
    precio: 56000,
    disponible: true,
    tiempoPreparacionMin: 22,
    destino: 'cocina',
  },
  {
    id: 'p13',
    categoriaId: 'c2',
    nombre: 'Tataki de res al ajonjolí',
    descripcion: 'Lomo sellado en costra de ajonjolí con vinagreta de maracuyá y ají.',
    precio: 60000,
    disponible: false,
    tiempoPreparacionMin: 20,
    destino: 'cocina',
    modificadores: [modTermino],
  },

  // --- Carnes y parrilla ---
  {
    id: 'p14',
    categoriaId: 'c3',
    nombre: 'Tomahawk 800 g',
    descripcion: 'Corte con hueso marinado en sal gruesa y vino tinto. Ideal para compartir.',
    precio: 118000,
    disponible: true,
    tiempoPreparacionMin: 35,
    destino: 'cocina',
    modificadores: [modTermino, modGuarnicion],
  },
  {
    id: 'p15',
    categoriaId: 'c3',
    nombre: 'Ojo de bife madurado 400 g',
    descripcion: 'Corte madurado 30 días, sal parrilla y mantequilla de hierbas.',
    precio: 92000,
    disponible: true,
    tiempoPreparacionMin: 28,
    destino: 'cocina',
    modificadores: [modTermino, modGuarnicion],
  },
  {
    id: 'p16',
    categoriaId: 'c3',
    nombre: 'Punta de anca 350 g',
    descripcion: 'A la parrilla con chimichurri de la casa.',
    precio: 68000,
    disponible: true,
    tiempoPreparacionMin: 25,
    destino: 'cocina',
    modificadores: [modTermino, modGuarnicion],
  },
  {
    id: 'p17',
    categoriaId: 'c3',
    nombre: 'Lomo fino en salsa de pimienta',
    descripcion: 'Medallones de lomo bañados en salsa de pimienta y brandy.',
    precio: 62000,
    disponible: true,
    tiempoPreparacionMin: 22,
    destino: 'cocina',
    modificadores: [modTermino, modGuarnicion],
  },
  {
    id: 'p18',
    categoriaId: 'c3',
    nombre: 'Mar y tierra del Patio',
    descripcion: 'Medallones de lomo al ajillo con langostinos salteados al brandy.',
    precio: 72000,
    disponible: true,
    tiempoPreparacionMin: 26,
    destino: 'cocina',
    modificadores: [modTermino, modGuarnicion],
  },
  {
    id: 'p19',
    categoriaId: 'c3',
    nombre: 'Churrasco de cerdo',
    descripcion: 'Lomo de cerdo en mariposa a la parrilla con chimichurri.',
    precio: 46000,
    disponible: true,
    tiempoPreparacionMin: 20,
    destino: 'cocina',
    modificadores: [modGuarnicion],
  },
  {
    id: 'p20',
    categoriaId: 'c3',
    nombre: 'Costillas BBQ de corozo',
    descripcion: 'Cocción lenta de ocho horas con salsa artesanal de corozo.',
    precio: 54000,
    disponible: true,
    tiempoPreparacionMin: 18,
    destino: 'cocina',
    modificadores: [modGuarnicion],
  },
  {
    id: 'p21',
    categoriaId: 'c3',
    nombre: 'Suprema de pollo rellena',
    descripcion: 'Pechuga rellena de jamón, queso y cebolla caramelizada en salsa de champiñones.',
    precio: 42000,
    disponible: true,
    tiempoPreparacionMin: 22,
    destino: 'cocina',
    modificadores: [modGuarnicion],
  },

  // --- Pescados y mariscos ---
  {
    id: 'p22',
    categoriaId: 'c4',
    nombre: 'Salmón en costra de ajonjolí',
    descripcion: 'Filete de salmón con reducción de lulo y puré de ñame.',
    precio: 68000,
    disponible: true,
    tiempoPreparacionMin: 22,
    destino: 'cocina',
  },
  {
    id: 'p23',
    categoriaId: 'c4',
    nombre: 'Róbalo a la marinera',
    descripcion: 'Filete marinado en cítricos bañado en salsa de frutos del mar.',
    precio: 62000,
    disponible: true,
    tiempoPreparacionMin: 24,
    destino: 'cocina',
  },
  {
    id: 'p24',
    categoriaId: 'c4',
    nombre: 'Mojarra con arroz titoté',
    descripcion: 'Mojarra entera, arroz de coco titoté, patacones y aguacate.',
    precio: 52000,
    disponible: true,
    tiempoPreparacionMin: 25,
    destino: 'cocina',
    modificadores: [modPreparacionPescado],
  },
  {
    id: 'p25',
    categoriaId: 'c4',
    nombre: 'Cazuela de mariscos gratinada',
    descripcion: 'Frutos del mar en bisque atomatado, gratinados con mozzarella y parmesano.',
    precio: 65000,
    disponible: true,
    tiempoPreparacionMin: 26,
    destino: 'cocina',
  },
  {
    id: 'p26',
    categoriaId: 'c4',
    nombre: 'Langostinos al ajillo',
    descripcion: 'Salteados en mantequilla, vino blanco y perejil, con arroz de coco.',
    precio: 66000,
    disponible: true,
    tiempoPreparacionMin: 20,
    destino: 'cocina',
  },
  {
    id: 'p27',
    categoriaId: 'c4',
    nombre: 'Pulpo a la parrilla',
    descripcion: 'Tentáculo a la brasa sobre puré de ñame con aceite de pimentón.',
    precio: 74000,
    disponible: true,
    tiempoPreparacionMin: 28,
    destino: 'cocina',
  },

  // --- Pastas y risottos ---
  {
    id: 'p28',
    categoriaId: 'c5',
    nombre: 'Ravioli de ricotta y albahaca',
    descripcion: 'Pasta fresca en salsa pomodoro con parmesano curado.',
    precio: 40000,
    disponible: true,
    tiempoPreparacionMin: 18,
    destino: 'cocina',
    modificadores: [modSinIngredientes],
  },
  {
    id: 'p29',
    categoriaId: 'c5',
    nombre: 'Fettuccine frutos del mar',
    descripcion: 'Pasta salteada con mariscos, brandy y bisque atomatado.',
    precio: 48000,
    disponible: true,
    tiempoPreparacionMin: 20,
    destino: 'cocina',
  },
  {
    id: 'p30',
    categoriaId: 'c5',
    nombre: 'Risotto de champiñones',
    descripcion: 'Arroz arbóreo, champiñones portobello y aceite de trufa.',
    precio: 46000,
    disponible: true,
    tiempoPreparacionMin: 24,
    destino: 'cocina',
  },
  {
    id: 'p31',
    categoriaId: 'c5',
    nombre: 'Boloñesa de la casa',
    descripcion: 'Ragú de res cocido a fuego lento con tostadas gratinadas.',
    precio: 38000,
    disponible: true,
    tiempoPreparacionMin: 18,
    destino: 'cocina',
  },
  {
    id: 'p32',
    categoriaId: 'c5',
    nombre: 'Lasaña de berenjena',
    descripcion: 'Capas de berenjena asada, pomodoro y queso gratinado. Opción vegetariana.',
    precio: 38000,
    disponible: true,
    tiempoPreparacionMin: 20,
    destino: 'cocina',
  },

  // --- Guarniciones ---
  {
    id: 'p33',
    categoriaId: 'c6',
    nombre: 'Papa criolla al romero',
    descripcion: 'Salteada con mantequilla de hierbas.',
    precio: 12000,
    disponible: true,
    tiempoPreparacionMin: 10,
    destino: 'cocina',
  },
  {
    id: 'p34',
    categoriaId: 'c6',
    nombre: 'Puré de ñame con albahaca',
    descripcion: 'Cremoso, con queso costeño.',
    precio: 12000,
    disponible: true,
    tiempoPreparacionMin: 8,
    destino: 'cocina',
  },
  {
    id: 'p35',
    categoriaId: 'c6',
    nombre: 'Ensalada fresca de la casa',
    descripcion: 'Mezcla verde, tomate cherry y vinagreta de maracuyá.',
    precio: 14000,
    disponible: true,
    tiempoPreparacionMin: 6,
    destino: 'cocina',
  },
  {
    id: 'p36',
    categoriaId: 'c6',
    nombre: 'Yuca al vapor con suero',
    descripcion: 'Yuca criolla con suero costeño.',
    precio: 10000,
    disponible: true,
    tiempoPreparacionMin: 8,
    destino: 'cocina',
  },
  {
    id: 'p37',
    categoriaId: 'c6',
    nombre: 'Vegetales baby salteados',
    descripcion: 'Zanahoria, arveja y calabacín al wok.',
    precio: 14000,
    disponible: true,
    tiempoPreparacionMin: 8,
    destino: 'cocina',
  },

  // --- Postres ---
  {
    id: 'p38',
    categoriaId: 'c7',
    nombre: 'Enyucado con helado',
    descripcion: 'Postre tradicional de yuca y coco con helado de vainilla.',
    precio: 18000,
    disponible: true,
    tiempoPreparacionMin: 8,
    destino: 'cocina',
    modificadores: [modDedicatoria],
  },
  {
    id: 'p39',
    categoriaId: 'c7',
    nombre: 'Volcán de chocolate',
    descripcion: 'Bizcocho tibio de centro líquido con helado de vainilla.',
    precio: 20000,
    disponible: true,
    tiempoPreparacionMin: 12,
    destino: 'cocina',
    modificadores: [modDedicatoria],
  },
  {
    id: 'p40',
    categoriaId: 'c7',
    nombre: 'Flan de coco y arequipe',
    descripcion: 'Con salsa de caramelo y cereza.',
    precio: 18000,
    disponible: true,
    tiempoPreparacionMin: 6,
    destino: 'cocina',
  },
  {
    id: 'p41',
    categoriaId: 'c7',
    nombre: 'Cheesecake de corozo',
    descripcion: 'Base de galleta con coulis de corozo del Caribe.',
    precio: 22000,
    disponible: true,
    tiempoPreparacionMin: 6,
    destino: 'cocina',
  },
  {
    id: 'p42',
    categoriaId: 'c7',
    nombre: 'Tiramisú del Patio',
    descripcion: 'Con café de la sierra y cacao amargo.',
    precio: 20000,
    disponible: true,
    tiempoPreparacionMin: 6,
    destino: 'cocina',
  },

  // --- Cocteleria de autor (barra) ---
  {
    id: 'p43',
    categoriaId: 'c8',
    nombre: 'Carajillo del Patio',
    descripcion: 'Espresso, licor de hierbas y espuma de panela.',
    precio: 28000,
    disponible: true,
    tiempoPreparacionMin: 5,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p44',
    categoriaId: 'c8',
    nombre: 'Patio Sour',
    descripcion: 'Corozo, cítricos y clara de huevo sobre base de ron añejo.',
    precio: 30000,
    disponible: true,
    tiempoPreparacionMin: 6,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p45',
    categoriaId: 'c8',
    nombre: 'Mojito de tamarindo',
    descripcion: 'Hierbabuena fresca, tamarindo y ron blanco.',
    precio: 26000,
    disponible: true,
    tiempoPreparacionMin: 5,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p46',
    categoriaId: 'c8',
    nombre: 'Gin tónico botánico',
    descripcion: 'Ginebra premium, tónica artesanal y botánicos de la casa.',
    precio: 32000,
    disponible: true,
    tiempoPreparacionMin: 4,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p47',
    categoriaId: 'c8',
    nombre: 'Margarita de maracuyá',
    descripcion: 'Tequila, maracuyá fresco y borde de sal ahumada.',
    precio: 28000,
    disponible: true,
    tiempoPreparacionMin: 5,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p48',
    categoriaId: 'c8',
    nombre: 'Old Fashioned de panela',
    descripcion: 'Whisky, panela ahumada y amargo de angostura.',
    precio: 32000,
    disponible: true,
    tiempoPreparacionMin: 5,
    destino: 'bar',
    modificadores: [modBar],
  },

  // --- Cocteleria clasica (barra) ---
  {
    id: 'p49',
    categoriaId: 'c9',
    nombre: 'Mojito clásico',
    descripcion: 'Ron blanco, hierbabuena, limón y soda.',
    precio: 24000,
    disponible: true,
    tiempoPreparacionMin: 4,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p50',
    categoriaId: 'c9',
    nombre: 'Piña colada',
    descripcion: 'Crema de coco, piña y ron.',
    precio: 26000,
    disponible: true,
    tiempoPreparacionMin: 4,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p51',
    categoriaId: 'c9',
    nombre: 'Daiquiri de fresa',
    descripcion: 'Fresa natural, limón y ron blanco.',
    precio: 24000,
    disponible: true,
    tiempoPreparacionMin: 4,
    destino: 'bar',
    modificadores: [modBar],
  },
  {
    id: 'p52',
    categoriaId: 'c9',
    nombre: 'Coctel de la casa',
    descripcion: 'El bartender lo prepara con el licor que elijas.',
    precio: 22000,
    disponible: true,
    tiempoPreparacionMin: 5,
    destino: 'bar',
    modificadores: [modLicorBase, modBar],
  },

  // --- Cervezas y bebidas (barra) ---
  {
    id: 'p53',
    categoriaId: 'c10',
    nombre: 'Cerveza nacional',
    descripcion: 'Botella 330 ml bien fría.',
    precio: 9000,
    disponible: true,
    tiempoPreparacionMin: 1,
    destino: 'bar',
  },
  {
    id: 'p54',
    categoriaId: 'c10',
    nombre: 'Cerveza artesanal',
    descripcion: 'Selección rotativa de cervecería local.',
    precio: 16000,
    disponible: true,
    tiempoPreparacionMin: 1,
    destino: 'bar',
  },
  {
    id: 'p55',
    categoriaId: 'c10',
    nombre: 'Limonada de coco',
    descripcion: 'Jarra individual, cremosa y fría.',
    precio: 14000,
    disponible: true,
    tiempoPreparacionMin: 4,
    destino: 'bar',
  },
  {
    id: 'p56',
    categoriaId: 'c10',
    nombre: 'Limonada natural',
    descripcion: 'En agua o en soda.',
    precio: 8000,
    disponible: true,
    tiempoPreparacionMin: 3,
    destino: 'bar',
  },
  {
    id: 'p57',
    categoriaId: 'c10',
    nombre: 'Jugo natural',
    descripcion: 'Mango, maracuyá, lulo o corozo.',
    precio: 9000,
    disponible: true,
    tiempoPreparacionMin: 3,
    destino: 'bar',
  },
  {
    id: 'p58',
    categoriaId: 'c10',
    nombre: 'Agua sin gas',
    descripcion: 'Botella 500 ml.',
    precio: 6000,
    disponible: true,
    tiempoPreparacionMin: 1,
    destino: 'bar',
  },
  {
    id: 'p59',
    categoriaId: 'c10',
    nombre: 'Gaseosa',
    descripcion: 'Botella personal.',
    precio: 7000,
    disponible: true,
    tiempoPreparacionMin: 1,
    destino: 'bar',
  },
]

const porId = (id: string): ItemCarta => {
  const item = CARTA.find((i) => i.id === id)
  if (!item) throw new Error(`Producto inexistente en la carta: ${id}`)
  return item
}

// ---------------------------------------------------------------------------
// Constructores
// ---------------------------------------------------------------------------

let contadorItem = 0

interface OpcionesItem {
  cantidad?: number
  modificadores?: { nombre: string; valor: string; precioAdicional?: number }[]
  nota?: string
  estado?: ItemOrden['estado']
  turno?: number
  minutosDesdeEnvio?: number
  minutosDesdeListo?: number
}

function crearItemOrden(itemCartaId: string, opciones: OpcionesItem = {}): ItemOrden {
  const carta = porId(itemCartaId)
  const turno = opciones.turno ?? 0
  return {
    id: `io${++contadorItem}`,
    itemCartaId: carta.id,
    nombre: carta.nombre,
    precioUnitario: carta.precio,
    cantidad: opciones.cantidad ?? 1,
    modificadoresSeleccionados: (opciones.modificadores ?? []).map((m) => ({
      nombre: m.nombre,
      valor: m.valor,
      precioAdicional: m.precioAdicional ?? 0,
    })),
    notaCocina: opciones.nota,
    estado: opciones.estado ?? 'pendiente',
    destino: carta.destino,
    turnoEnvio: turno,
    enviadoEn: turno > 0 ? haceMinutos(opciones.minutosDesdeEnvio ?? 5) : undefined,
    listoEn:
      opciones.minutosDesdeListo !== undefined ? haceMinutos(opciones.minutosDesdeListo) : undefined,
  }
}

let contadorOrden = 0

function crearOrden(datos: {
  mesaId: string
  meseroId: string
  numero: number
  estado: Orden['estado']
  items: ItemOrden[]
  comensales: number
  minutosAbierta: number
  cargos?: CargoAdicional[]
  notas?: string
  minutosCerrada?: number
}): Orden {
  return {
    id: `ord${++contadorOrden}`,
    mesaId: datos.mesaId,
    meseroId: datos.meseroId,
    numero: datos.numero,
    estado: datos.estado,
    items: datos.items,
    cargosAdicionales: datos.cargos ?? [],
    comensales: datos.comensales,
    abiertaEn: haceMinutos(datos.minutosAbierta),
    cerradaEn: datos.minutosCerrada !== undefined ? haceMinutos(datos.minutosCerrada) : undefined,
    notas: datos.notas,
  }
}

// ---------------------------------------------------------------------------
// Salon en marcha: 6 mesas ocupadas y 3 comandas en cocina
// ---------------------------------------------------------------------------

function construirSalonActivo(): { ordenes: Orden[]; mesas: Mesa[] } {
  const mesas = MESAS.map((m) => ({ ...m }))
  const ordenes: Orden[] = []

  const asignar = (mesaId: string, orden: Orden, estado: Mesa['estado']) => {
    const mesa = mesas.find((m) => m.id === mesaId)!
    mesa.estado = estado
    mesa.meseroId = orden.meseroId
    mesa.ordenActivaId = orden.id
    ordenes.push(orden)
  }

  // Mesa 3 — recien enviada, cocina la ve en pendientes (verde).
  const mesa3 = crearOrden({
    mesaId: 'm3',
    meseroId: 'u1',
    numero: 41,
    estado: 'enviada',
    comensales: 4,
    minutosAbierta: 14,
    items: [
      crearItemOrden('p02', { turno: 1, minutosDesdeEnvio: 6, cantidad: 2 }),
      crearItemOrden('p05', { turno: 1, minutosDesdeEnvio: 6 }),
      crearItemOrden('p45', { turno: 1, minutosDesdeEnvio: 6, cantidad: 2, estado: 'listo', minutosDesdeListo: 2 }),
      crearItemOrden('p56', { turno: 1, minutosDesdeEnvio: 6, cantidad: 2, estado: 'servido' }),
    ],
  })
  asignar('m3', mesa3, 'ocupada')

  // Mesa 5 — segundo turno en preparacion (ambar).
  const mesa5 = crearOrden({
    mesaId: 'm5',
    meseroId: 'u1',
    numero: 38,
    estado: 'en_preparacion',
    comensales: 4,
    minutosAbierta: 38,
    items: [
      crearItemOrden('p01', { turno: 1, minutosDesdeEnvio: 34, estado: 'servido' }),
      crearItemOrden('p07', { turno: 1, minutosDesdeEnvio: 34, estado: 'servido' }),
      crearItemOrden('p46', { turno: 1, minutosDesdeEnvio: 34, cantidad: 2, estado: 'servido' }),
      crearItemOrden('p16', {
        turno: 2,
        minutosDesdeEnvio: 14,
        estado: 'en_preparacion',
        modificadores: [
          { nombre: 'Término de la carne', valor: 'Término medio' },
          { nombre: 'Guarnición', valor: 'Papa criolla al romero' },
        ],
      }),
      crearItemOrden('p22', { turno: 2, minutosDesdeEnvio: 14, estado: 'en_preparacion' }),
      crearItemOrden('p29', {
        turno: 2,
        minutosDesdeEnvio: 14,
        estado: 'en_preparacion',
        nota: 'Sin picante, la señora es alérgica',
      }),
    ],
  })
  asignar('m5', mesa5, 'ocupada')

  // Mesa 7 — turno pasado de 20 minutos: la cocina lo ve en rojo.
  const mesa7 = crearOrden({
    mesaId: 'm7',
    meseroId: 'u5',
    numero: 35,
    estado: 'en_preparacion',
    comensales: 6,
    minutosAbierta: 56,
    items: [
      crearItemOrden('p06', { turno: 1, minutosDesdeEnvio: 52, cantidad: 2, estado: 'servido' }),
      crearItemOrden('p53', { turno: 1, minutosDesdeEnvio: 52, cantidad: 4, estado: 'servido' }),
      crearItemOrden('p14', {
        turno: 2,
        minutosDesdeEnvio: 24,
        estado: 'en_preparacion',
        modificadores: [
          { nombre: 'Término de la carne', valor: 'Tres cuartos' },
          { nombre: 'Guarnición', valor: 'Vegetales baby', precioAdicional: 3000 },
        ],
        nota: 'Para compartir, servir en el centro',
      }),
      crearItemOrden('p20', { turno: 2, minutosDesdeEnvio: 24, cantidad: 2, estado: 'pendiente' }),
      crearItemOrden('p33', { turno: 2, minutosDesdeEnvio: 24, cantidad: 2, estado: 'pendiente' }),
    ],
  })
  asignar('m7', mesa7, 'ocupada')

  // Terraza 2 — el mesero esta armando el pedido, todavia sin enviar.
  const mesa12 = crearOrden({
    mesaId: 'm12',
    meseroId: 'u1',
    numero: 43,
    estado: 'abierta',
    comensales: 2,
    minutosAbierta: 7,
    items: [
      crearItemOrden('p03'),
      crearItemOrden('p44', { cantidad: 2 }),
    ],
  })
  asignar('m12', mesa12, 'ocupada')

  // Terraza 4 — ya pidio la cuenta, pendiente de cobrar.
  const mesa14 = crearOrden({
    mesaId: 'm14',
    meseroId: 'u5',
    numero: 31,
    estado: 'cuenta_pedida',
    comensales: 5,
    minutosAbierta: 82,
    items: [
      crearItemOrden('p04', { turno: 1, minutosDesdeEnvio: 78, estado: 'servido' }),
      crearItemOrden('p08', { turno: 1, minutosDesdeEnvio: 78, estado: 'servido' }),
      crearItemOrden('p18', { turno: 2, minutosDesdeEnvio: 60, estado: 'servido', modificadores: [
        { nombre: 'Término de la carne', valor: 'Término medio' },
        { nombre: 'Guarnición', valor: 'Puré de ñame' },
      ] }),
      crearItemOrden('p24', { turno: 2, minutosDesdeEnvio: 60, estado: 'servido', modificadores: [
        { nombre: 'Preparación', valor: 'Frito' },
      ] }),
      crearItemOrden('p31', { turno: 2, minutosDesdeEnvio: 60, estado: 'servido' }),
      crearItemOrden('p43', { turno: 3, minutosDesdeEnvio: 22, cantidad: 3, estado: 'servido' }),
      crearItemOrden('p38', { turno: 3, minutosDesdeEnvio: 22, cantidad: 2, estado: 'servido' }),
    ],
  })
  asignar('m14', mesa14, 'cuenta_pedida')

  // Privado 2 — cumpleanos, con cargo adicional declarado desde el principio.
  const mesa17 = crearOrden({
    mesaId: 'm17',
    meseroId: 'u1',
    numero: 36,
    estado: 'en_preparacion',
    comensales: 9,
    minutosAbierta: 44,
    notas: 'Cumpleaños de la señora Miranda. Torta a las 9:00 p. m.',
    cargos: [
      {
        id: 'cg1',
        nombre: 'Decoración de cumpleaños',
        valor: 45000,
        agregadoPor: 'María Fernanda Ospina',
        agregadoEn: haceMinutos(44),
      },
    ],
    items: [
      crearItemOrden('p01', { turno: 1, minutosDesdeEnvio: 40, cantidad: 3, estado: 'servido' }),
      crearItemOrden('p05', { turno: 1, minutosDesdeEnvio: 40, cantidad: 2, estado: 'servido' }),
      crearItemOrden('p47', { turno: 1, minutosDesdeEnvio: 40, cantidad: 4, estado: 'servido' }),
      // Turno completo listo: la mesa tiene platos esperando que el mesero los recoja.
      crearItemOrden('p15', {
        turno: 2,
        minutosDesdeEnvio: 12,
        cantidad: 2,
        estado: 'listo',
        minutosDesdeListo: 2,
        modificadores: [
          { nombre: 'Término de la carne', valor: 'Bien asado' },
          { nombre: 'Guarnición', valor: 'Risotto de champiñones', precioAdicional: 9000 },
        ],
      }),
      crearItemOrden('p25', {
        turno: 2,
        minutosDesdeEnvio: 12,
        cantidad: 2,
        estado: 'listo',
        minutosDesdeListo: 2,
      }),
      crearItemOrden('p32', {
        turno: 2,
        minutosDesdeEnvio: 12,
        estado: 'listo',
        minutosDesdeListo: 1,
        nota: 'Vegetariana',
      }),
      crearItemOrden('p43', { turno: 2, minutosDesdeEnvio: 12, cantidad: 4, estado: 'listo', minutosDesdeListo: 3 }),
    ],
  })
  asignar('m17', mesa17, 'ocupada')

  return { ordenes, mesas }
}

// ---------------------------------------------------------------------------
// Historico de los ultimos 10 dias
// ---------------------------------------------------------------------------

const PLATOS_FUERTES = ['p09', 'p10', 'p11', 'p12', 'p14', 'p15', 'p16', 'p17', 'p18', 'p19', 'p20', 'p21', 'p22', 'p23', 'p24', 'p25', 'p26', 'p27', 'p28', 'p29', 'p30', 'p31', 'p32']
const ENTRADAS = ['p01', 'p02', 'p03', 'p04', 'p05', 'p06', 'p07', 'p08']
const BEBIDAS = ['p43', 'p44', 'p45', 'p46', 'p47', 'p48', 'p49', 'p50', 'p51', 'p53', 'p54', 'p55', 'p56', 'p57', 'p58', 'p59']
const POSTRES = ['p38', 'p39', 'p40', 'p41', 'p42']
const MESEROS = ['u1', 'u5']
const METODOS: Pago['metodo'][] = ['efectivo', 'tarjeta', 'tarjeta', 'transferencia', 'tarjeta', 'efectivo']

function construirHistorico(porcentajeInc: number): {
  ordenes: Orden[]
  pagos: Pago[]
  cierres: CierreCaja[]
} {
  const ordenes: Orden[] = []
  const pagos: Pago[] = []
  const cierres: CierreCaja[] = []
  const ahora = Date.now()

  for (let dia = 10; dia >= 0; dia--) {
    const esHoy = dia === 0
    // De hoy solo existen las mesas ya cobradas: entre seis horas atras y hace media hora.
    const cantidad = esHoy ? entre(5, 9) : entre(14, 24)
    let consecutivo = 1
    const pagosDelDia: Pago[] = []

    for (let i = 0; i < cantidad; i++) {
      // Los dias pasados se reparten dentro del horario de servicio. Las mesas
      // ya cobradas de hoy se cuelgan de la hora actual, para que la
      // demostracion tenga ventas del dia sin importar a que hora se abra.
      let minutosAtras: number
      if (esHoy) {
        minutosAtras = entre(35, 360)
      } else {
        const cuando = new Date(ahora - dia * 86400000)
        cuando.setHours(entre(12, 22), entre(0, 59), 0, 0)
        minutosAtras = Math.round((ahora - cuando.getTime()) / 60000)
      }
      const cierreEn = new Date(ahora - minutosAtras * 60000)

      const comensales = entre(2, 8)
      const items: ItemOrden[] = []
      const cuantosFuertes = Math.max(1, Math.round(comensales * 0.8))
      for (let f = 0; f < cuantosFuertes; f++) items.push(crearItemOrden(uno(PLATOS_FUERTES), { turno: 1 }))
      for (let e = 0; e < entre(1, 3); e++) items.push(crearItemOrden(uno(ENTRADAS), { turno: 1 }))
      for (let b = 0; b < entre(2, 6); b++) items.push(crearItemOrden(uno(BEBIDAS), { turno: 1 }))
      if (aleatorio() > 0.55) items.push(crearItemOrden(uno(POSTRES), { turno: 2, cantidad: entre(1, 2) }))

      const duracion = entre(55, 130)

      // Cada plato guarda cuándo salió la comanda y cuándo estuvo listo, con la
      // variación propia de una cocina real. Sin esto el reporte de tiempos de
      // preparación queda vacío y no se puede medir a la cocina.
      const abiertaEnMs = ahora - (minutosAtras + duracion) * 60000
      const itemsServidos = items.map((it) => {
        const carta = porId(it.itemCartaId)
        const enviadoEn = abiertaEnMs + entre(3, 12) * 60000
        const preparacion = Math.max(2, Math.round(carta.tiempoPreparacionMin * (0.7 + aleatorio() * 0.8)))
        return {
          ...it,
          estado: 'servido' as const,
          enviadoEn: new Date(enviadoEn).toISOString(),
          listoEn: new Date(enviadoEn + preparacion * 60000).toISOString(),
        }
      })

      const orden = crearOrden({
        mesaId: uno(MESAS).id,
        meseroId: uno(MESEROS),
        numero: consecutivo++,
        estado: 'pagada',
        comensales,
        items: itemsServidos,
        minutosAbierta: minutosAtras + duracion,
        minutosCerrada: minutosAtras,
      })

      // Cargo adicional ocasional: siempre queda registrado con nombre y valor.
      if (aleatorio() > 0.88) {
        orden.cargosAdicionales.push({
          id: `cgh${orden.id}`,
          nombre: 'Decoración de cumpleaños',
          valor: 45000,
          agregadoPor: 'María Fernanda Ospina',
          agregadoEn: orden.abiertaEn,
        })
      }

      // La propina es voluntaria: hay cuentas que la dejan y cuentas que no.
      const sorteo = aleatorio()
      const porcentajePropina = sorteo > 0.55 ? 10 : sorteo > 0.25 ? 5 : 0
      const cuenta = calcularCuenta(orden, porcentajeInc, porcentajePropina)
      const metodo = uno(METODOS)

      const pago: Pago = {
        id: `pg${orden.id}`,
        ordenId: orden.id,
        subtotal: cuenta.subtotal,
        inc: cuenta.inc,
        propina: cuenta.propina,
        cargosAdicionales: cuenta.cargosAdicionales,
        total: cuenta.total,
        metodo,
        recibidoPor: 'Katherine Villalba',
        fechaHora: cierreEn.toISOString(),
      }

      ordenes.push(orden)
      pagos.push(pago)
      pagosDelDia.push(pago)
    }

    // Cierre de caja de los dias ya terminados.
    if (!esHoy && pagosDelDia.length > 0) {
      const fecha = new Date(ahora - dia * 86400000)
      const cerradoALas = new Date(fecha)
      cerradoALas.setHours(23, 30, 0, 0)
      const suma = (f: (p: Pago) => number) => pagosDelDia.reduce((s, p) => s + f(p), 0)
      const porMetodo = (m: Pago['metodo']) =>
        pagosDelDia.filter((p) => p.metodo === m).reduce((s, p) => s + p.total, 0)
      const ventaTotal = suma((p) => p.total)

      cierres.push({
        id: `cc${dia}`,
        fecha: claveDia(fecha),
        turno: 'cena',
        ventaTotal,
        totalEfectivo: porMetodo('efectivo'),
        totalTarjeta: porMetodo('tarjeta'),
        totalTransferencia: porMetodo('transferencia'),
        propinasTotales: suma((p) => p.propina),
        incTotal: suma((p) => p.inc),
        ordenesAtendidas: pagosDelDia.length,
        ticketPromedio: Math.round(ventaTotal / pagosDelDia.length),
        cerradoPor: 'Katherine Villalba',
        fechaHora: cerradoALas.toISOString(),
      })
    }
  }

  return { ordenes, pagos, cierres }
}

// ---------------------------------------------------------------------------
// Reservas
// ---------------------------------------------------------------------------

function construirReservas(): Reserva[] {
  const hoy = new Date()
  const aLasHoras = (hora: number, minutos = 0, diasAdelante = 0): string => {
    const fecha = new Date(hoy)
    fecha.setDate(fecha.getDate() + diasAdelante)
    fecha.setHours(hora, minutos, 0, 0)
    return fecha.toISOString()
  }

  return [
    {
      id: 'r1',
      nombreCliente: 'Carolina Mendoza',
      telefono: '3012345678',
      fechaHora: aLasHoras(19, 30),
      personas: 6,
      ocasion: 'cumpleanos',
      estado: 'confirmada',
      notas: 'Cumpleaños número 40. Piden torta al final.',
      mesaAsignadaId: 'm16',
    },
    {
      id: 'r2',
      nombreCliente: 'Familia Barrios Puello',
      telefono: '3155558899',
      fechaHora: aLasHoras(20, 0),
      personas: 4,
      ocasion: 'ninguna',
      estado: 'confirmada',
      mesaAsignadaId: 'm13',
    },
    {
      id: 'r3',
      nombreCliente: 'Andrés Felipe Torres',
      telefono: '3004471122',
      fechaHora: aLasHoras(21, 0),
      personas: 2,
      ocasion: 'aniversario',
      estado: 'solicitada',
      notas: 'Si es posible, mesa en la terraza.',
    },
    {
      id: 'r4',
      nombreCliente: 'Inversiones Caribe S.A.S.',
      telefono: '3187764433',
      fechaHora: aLasHoras(13, 0, 1),
      personas: 10,
      ocasion: 'negocios',
      estado: 'solicitada',
      notas: 'Almuerzo de trabajo, necesitan factura electrónica.',
    },
  ]
}

// ---------------------------------------------------------------------------
// Base completa
// ---------------------------------------------------------------------------

export function crearBaseInicial(): BaseDatos {
  semilla = 20260813
  contadorItem = 0
  contadorOrden = 0

  const porcentajeInc = INC_POR_DEFECTO
  const historico = construirHistorico(porcentajeInc)
  const salon = construirSalonActivo()

  return {
    version: 1,
    usuarios: USUARIOS,
    mesas: salon.mesas,
    categorias: CATEGORIAS,
    carta: CARTA,
    ordenes: [...historico.ordenes, ...salon.ordenes],
    pagos: historico.pagos,
    reservas: construirReservas(),
    cierres: historico.cierres,
    ajustes: {
      porcentajeInc,
      simularSinConexion: false,
      consecutivoOrden: 44,
      fechaConsecutivo: claveDia(),
    },
  }
}

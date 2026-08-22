/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // --- Sitio publico: calido y elegante ---
        crema: {
          50: '#FBF8F2',
          100: '#F5EEE1',
          200: '#E9DDC7',
          300: '#D8C6A5',
          400: '#C0A87F',
        },
        // El fondo del sitio publico. Negro con una gota de calor: un negro
        // puro (#000) sobre pantalla se ve plano y duro, y a 20 cm de la cara
        // cansa. Estos llevan un punto de marron que no se nombra pero se
        // siente, y es lo que separa «oscuro» de «elegante».
        onix: {
          950: '#0A0908',
          900: '#100F0D',
          800: '#171512',
          700: '#211E1A',
          600: '#2D2924',
          500: '#3B352E',
        },
        // El dorado de la casa. El anterior era ambar -naranja quemado- y
        // sobre negro leia a cobre, no a oro. Estos bajan el rojo y suben el
        // verde hasta el amarillo del metal.
        //
        // Se usa con cuentagotas: filetes, versalitas, el boton principal y
        // poco mas. Un dorado repartido por toda la pantalla deja de parecer
        // oro y empieza a parecer amarillo.
        oro: {
          700: '#7A5E1F',
          600: '#9B7A28',
          500: '#C09A33',
          400: '#D4B255',
          300: '#E3CA84',
          200: '#F0E2B8',
        },
        // --- Areas operativas: neutro profundo, se usan de noche ---
        noche: {
          950: '#0A0908',
          900: '#121110',
          850: '#191716',
          800: '#211E1C',
          700: '#2C2825',
          600: '#3D3833',
          500: '#57504A',
          400: '#8A8079',
          300: '#B5ADA6',
        },
        // --- Semantica de estado, consistente en todo el sistema ---
        estado: {
          listo: '#22C55E',
          'listo-suave': '#0F3D22',
          proceso: '#F59E0B',
          'proceso-suave': '#40300A',
          demorado: '#EF4444',
          'demorado-suave': '#3F1414',
          libre: '#6B7280',
          reservada: '#3B82F6',
          'reservada-suave': '#12294A',
        },
      },
      fontFamily: {
        marca: ['Cinzel', 'Georgia', 'serif'],
        titulo: ['"Cormorant Garamond"', 'Georgia', 'serif'],
        texto: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
      minHeight: {
        toque: '48px',
      },
      height: {
        toque: '48px',
      },
      keyframes: {
        entrada: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        destello: {
          '0%': { boxShadow: '0 0 0 0 rgba(198,116,31,0.55)' },
          '70%': { boxShadow: '0 0 0 12px rgba(198,116,31,0)' },
          '100%': { boxShadow: '0 0 0 0 rgba(198,116,31,0)' },
        },
        latido: {
          '0%,100%': { opacity: '1' },
          '50%': { opacity: '0.45' },
        },
        deslizar: {
          '0%': { transform: 'translateY(100%)' },
          '100%': { transform: 'translateY(0)' },
        },
        aparecer: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        caer: {
          '0%': { opacity: '0', transform: 'translateY(-12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        entrada: 'entrada 0.22s ease-out',
        destello: 'destello 1.1s ease-out 2',
        latido: 'latido 1.6s ease-in-out infinite',
        deslizar: 'deslizar 0.24s cubic-bezier(0.32, 0.72, 0, 1)',
        aparecer: 'aparecer 0.18s ease-out',
        caer: 'caer 0.2s ease-out',
      },
    },
  },
  plugins: [],
}

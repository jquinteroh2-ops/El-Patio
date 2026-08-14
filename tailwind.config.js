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
        bosque: {
          950: '#08170F',
          900: '#0C2317',
          800: '#123322',
          700: '#1A4A31',
          600: '#256446',
          500: '#357F5C',
        },
        ambar: {
          700: '#8A4B10',
          600: '#A85D16',
          500: '#C6741F',
          400: '#DC8B33',
          300: '#E9A75E',
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

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        munowatch: {
          bg: '#010318',
          surface: '#0A192F',
          elevated: '#132845',
          variant: '#1E293B',
          green: '#10B981',
          greenLight: '#34D399',
          greenDark: '#059669',
          focusBorder: '#34D399',
          gold: '#FBBF24',
          textPrimary: '#F8FAFC',
          textSecondary: '#94A3B8',
          textMuted: '#64748B',
          error: '#EF4444'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif']
      }
    },
  },
  plugins: [],
}

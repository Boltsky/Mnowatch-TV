/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        muno: {
          dark: '#0a0f0d',
          card: '#121a16',
          border: '#1f2d26',
          primary: '#10b981', // Emerald green brand color
          glow: '#059669',
          accent: '#34d399',
          surface: '#18241e'
        }
      }
    },
  },
  plugins: [],
}

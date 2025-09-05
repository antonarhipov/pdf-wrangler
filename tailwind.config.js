/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        'pdf-blue': '#3B82F6',
        'pdf-gray': '#6B7280'
      }
    }
  },
  plugins: []
}
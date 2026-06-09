/** @type {import('tailwindcss').Config} */
export default {
  content: ["./public/index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      boxShadow: {
        command: '0 18px 50px rgba(0, 0, 0, 0.28)'
      }
    },
  },
  plugins: [],
}

module.exports = {
  '/api/**': {
    target: process.env.API_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  },
};
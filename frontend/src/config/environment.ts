// Configuration de l'environnement
// En développement: utilise l'URL locale
// En production: utilise l'URL du backend déployé

function getApiBaseUrl(): string {
  // Vercel injecte les variables d'environnement via process.env
  // Les variables doivent être préfixées avec VERCEL_ ou être dans .env
  // Pour Angular, on utilise une approche différente: injection via window au runtime
  
  // Méthode 1: Variable d'environnement au build (pour Vercel)
  // Dans Vercel, configurez: NEXT_PUBLIC_API_URL ou VITE_API_URL
  // Angular n'utilise pas directement process.env, donc on l'injecte via un script
  if (typeof window !== 'undefined') {
    // Vérifier si une variable a été injectée via un script dans index.html
    const injectedUrl = (window as any).__API_URL__;
    if (injectedUrl) {
      return injectedUrl;
    }
    
    // Vérifier si window.ENV est défini
    if ((window as any).ENV?.API_URL) {
      return (window as any).ENV.API_URL;
    }
  }
  
  // En développement, utiliser localhost
  if (typeof window !== 'undefined' && 
      (window.location.hostname === 'localhost' || 
       window.location.hostname === '127.0.0.1' ||
       window.location.hostname.includes('192.168'))) {
    return 'http://localhost:8080/api';
  }
  
  // En production sur Vercel, utiliser l'URL du backend déployé
  // REMPLACEZ cette valeur par l'URL de votre backend (Railway, Render, etc.)
  // Ou configurez-la via les variables d'environnement Vercel et un script d'injection
  const defaultProdUrl = 'https://votre-backend.railway.app/api';
  
  return defaultProdUrl;
}

export const API_BASE_URL = getApiBaseUrl();

console.log('API Base URL:', API_BASE_URL);

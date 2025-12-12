// Configuration de l'environnement
// En développement: utilise l'URL locale
// En production: utilise l'URL du backend déployé

function getApiBaseUrl(): string {
  if (typeof window === 'undefined') {
    // Server-side rendering fallback
    return 'https://bf4investapp-production.up.railway.app/api';
  }
  
  // En développement, utiliser localhost
  if (window.location.hostname === 'localhost' || 
      window.location.hostname === '127.0.0.1' ||
      window.location.hostname.includes('192.168')) {
    return 'http://localhost:8080/api';
  }
  
  // Vérifier si une variable a été injectée via un script dans index.html
  // Le script inject-env.js injecte window.__API_URL__ après le build
  const injectedUrl = (window as any).__API_URL__;
  if (injectedUrl && injectedUrl !== 'https://votre-backend.railway.app/api') {
    return injectedUrl;
  }
  
  // Vérifier si window.ENV est défini
  if ((window as any).ENV?.API_URL) {
    return (window as any).ENV.API_URL;
  }
  
  // URL par défaut pour la production (Railway)
  return 'https://bf4investapp-production.up.railway.app/api';
}

// Export une fonction qui réévalue l'URL à chaque appel (au cas où window.__API_URL__ serait défini plus tard)
export function getApiBaseUrlDynamic(): string {
  return getApiBaseUrl();
}

// Pour la compatibilité, on garde une constante mais elle sera réévaluée si nécessaire
export const API_BASE_URL = getApiBaseUrl();

console.log('API Base URL:', API_BASE_URL);

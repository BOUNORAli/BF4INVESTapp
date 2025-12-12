// Script pour injecter les variables d'environnement dans index.html après le build
// Exécuté après ng build sur Vercel

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const distPath = path.join(__dirname, '..', 'dist');
const indexPath = path.join(distPath, 'index.html');

if (fs.existsSync(indexPath)) {
  let html = fs.readFileSync(indexPath, 'utf8');
  
  // Récupérer l'URL de l'API depuis les variables d'environnement Vercel
  const apiUrl = process.env.NEXT_PUBLIC_API_URL || 
                 process.env.VITE_API_URL || 
                 process.env.API_URL ||
                 'https://bf4investapp-production.up.railway.app/api';
  
  // Injecter dans le script window.__API_URL__ (remplacer la valeur par défaut)
  const scriptTag = `  <script>
    window.__API_URL__ = '${apiUrl}';
  </script>`;
  
  // Remplacer le script existant ou l'ajouter avant </head>
  if (html.includes('window.__API_URL__')) {
    // Remplacer la valeur existante
    html = html.replace(
      /window\.__API_URL__ = '[^']*';/g, 
      `window.__API_URL__ = '${apiUrl}';`
    );
  } else {
    // Ajouter le script avant </head>
    html = html.replace('</head>', `${scriptTag}\n</head>`);
  }
  
  fs.writeFileSync(indexPath, html, 'utf8');
  console.log(`✅ URL API injectée: ${apiUrl}`);
} else {
  console.warn(`⚠️  index.html non trouvé dans ${indexPath}`);
}


import type { NavLink, Partner, Stat, Product, FaqItem, ProductCategory } from './types';

export const NAV_LINKS: NavLink[] = [
  { name: 'Accueil', href: '/' },
  { name: 'À propos', href: '/a-propos' },
  { name: 'Produits', href: '/produits' },
  { name: 'Contact', href: '/contact' },
];

export const PARTNERS: Partner[] = [
  { name: 'SONASID', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/Sonasid_logo.svg/2560px-Sonasid_logo.svg.png' },
  { name: 'Ciments du Maroc', logoUrl: 'https://upload.wikimedia.org/wikipedia/fr/a/a8/Ciments_du_Maroc_logo.png' },
  { name: 'LafargeHolcim', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c0/LafargeHolcim_logo.svg/2560px-LafargeHolcim_logo.svg.png' },
  { name: 'OLA Energy', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Ola_Energy_logo.svg/2560px-Ola_Energy_logo.svg.png' },
  { name: 'Plastima', logoUrl: 'https://plastimacanalisations.com/wp-content/uploads/2024/07/logo-plastima-canalisations-2.png.webp' },
  { name: 'Ines SA', logoUrl: 'https://media.licdn.com/dms/image/v2/C4D0BAQGLSthhH4SQhQ/company-logo_200_200/company-logo_200_200/0/1630541080083/inesmax_logo?e=2147483647&v=beta&t=xD6CUnian8Wl4ZPRqEtDCd5cafBqM4S_Mo5VQNKlYN4' },
  { name: 'Maghreb Grillage', logoUrl: 'http://maghrebgrillage.ma/wp-content/uploads/2015/10/MA6033547-3085-B1.jpg' },
  { name: 'CMCP', logoUrl: 'https://www.cmcp-ip.com/wp-content/uploads/2019/04/logo-cmcp-ip.png' }
];

export const STATS: Stat[] = [
  { value: 500, label: 'Références Actives', suffix: '+' },
  { value: 24, label: 'Délai Initial de Réponse', suffix: 'h' },
  { value: 15, label: "Années d'Expertise Marché", suffix: '+' },
  { value: 98, label: 'Taux de Service Opérationnel', suffix: '%' },
];

export const PRODUCT_CATEGORIES: ProductCategory[] = [
  'Tous',
  'Boulonnerie et Visserie',
  'Outillage Industriel',
  'Aciers et Fils',
  'Tubes et Canalisations',
  'Matériaux de Construction',
  'Signalisation et EPI',
];

// Catalogue statique d'exemple (utilisé en secours si l'API n'est pas disponible)
export const PRODUCTS: Product[] = [
  // 1. Boulonnerie
  { name: 'Vis Tête Hexagonale TH - Acier 8.8 Zingué DIN 933', unit: 'Boîte 100u', price: 'Sur demande', category: 'Boulonnerie et Visserie' },
  { name: 'Écrou Hexagonal CL8 Zingué DIN 934 - M12', unit: 'Boîte 200u', price: 'Sur demande', category: 'Boulonnerie et Visserie' },
  { name: 'Rondelle Plate Forme M Zinguée DIN 125', unit: 'Sachet 500u', price: 'Sur demande', category: 'Boulonnerie et Visserie' },
  { name: 'Tige Filetée 1m Acier 4.8 Zingué DIN 975', unit: 'U', price: 'Sur demande', category: 'Boulonnerie et Visserie' },

  // 2. Outillage Industriel
  { name: 'Groupe Électrogène de Chantier 6.5 kVA', unit: 'U', price: 9192.00, category: 'Outillage Industriel' },
  { name: 'Pilonneuse Thermique 79KG - Moteur Honda', unit: 'U', price: 12072.00, category: 'Outillage Industriel' },
  { name: 'Scie à Sol Professionnelle Diam 350mm', unit: 'U', price: 16200.00, category: 'Outillage Industriel' },

  // 3. Aciers
  { name: 'Fer Tor / 500 Diam 12 - Haute Adhérence', unit: 'T', price: 10700.00, category: 'Aciers et Fils' },
  { name: 'Acier pour Béton D8 - Certifié NM', unit: 'KG', price: 10.38, category: 'Aciers et Fils' },

  // 4. Canalisations
  { name: 'Tube PVC SN4 DN 200 - Assainissement', unit: 'ML', price: 93.60, category: 'Tubes et Canalisations' },
  { name: 'Tube PEHD PN16 DN 63 - Adduction Eau', unit: 'ML', price: 'Sur demande', category: 'Tubes et Canalisations' },

  // 5. Matériaux
  { name: 'Ciment CPJ 45 - Sac de 50KG (LafargeHolcim)', unit: 'T', price: 1700.00, category: 'Matériaux de Construction' },
  { name: 'Béton B30 - Livraison par Toupie', unit: 'M3', price: 1165.23, category: 'Matériaux de Construction' },
];

export const FAQ_ITEMS: FaqItem[] = [
  {
    question: 'Pouvez-vous traiter des besoins urgents pour des chantiers en activité ?',
    answer: "Oui. Nous priorisons les demandes critiques avec une qualification rapide, puis une proposition adaptée aux contraintes de délai et de disponibilité."
  },
  {
    question: 'Comment se déroule une demande de devis chez BF4 Invest ?',
    answer: "Vous transmettez votre besoin via le formulaire ou par téléphone, notre équipe consolide les références, vérifie la faisabilité logistique et vous adresse une proposition claire."
  },
  {
    question: 'Travaillez-vous uniquement à Meknès ?',
    answer: "Non. Notre base est à Meknès, mais nous accompagnons des clients sur l'ensemble du territoire marocain avec une organisation logistique dédiée."
  }
];


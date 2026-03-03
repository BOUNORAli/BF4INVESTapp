// Types et contextes pour le site vitrine BF4 Invest

import React, { createContext } from 'react';

export interface NavLink {
  name: string;
  href: string;
}

export interface ValueCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

export interface Partner {
  name: string;
  logoUrl: string;
}

export interface Stat {
  value: number;
  label: string;
  suffix: string;
}

export type ProductCategory =
  | 'Tous'
  | 'Matériaux de Construction et Granulats'
  | 'Aciers et Fils'
  | 'Tubes et Canalisations'
  | 'Robinetterie et Accessoires'
  | 'Pièces de Raccordement et Brides'
  | 'Colliers et Petits Raccords PE'
  | 'Outillage, Équipement et Services'
  | 'Divers et Signalisation'
  // Catégories supplémentaires utilisées dans constants.ts
  | 'Boulonnerie et Visserie'
  | 'Outillage Industriel'
  | 'Matériaux de Construction'
  | 'Signalisation et EPI';

export interface Product {
  name: string;
  ref?: string;
  unit: string;
  price: number | 'Sur demande';
  category: ProductCategory;
  // URL d'image optionnelle issue du backend (data URL ou HTTP)
  imageUrl?: string;
}

export interface FaqItem {
  question: string;
  answer: string;
}

// --- CONTEXTES ---

// Thème
export type Theme = 'light' | 'dark';

export interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

// Navigation
export type Page = 'accueil' | 'a-propos' | 'produits' | 'contact';

export interface NavigationContextType {
  page: Page;
  setPage: (page: Page) => void;
}

export const NavigationContext = createContext<NavigationContextType | undefined>(undefined);


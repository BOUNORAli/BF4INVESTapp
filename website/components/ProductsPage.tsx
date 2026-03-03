import React, { useState, useMemo, useEffect } from 'react';
import { PRODUCTS, PRODUCT_CATEGORIES } from '../constants';
import type { Product, ProductCategory } from '../types';
import { Search, ArrowRight, Download, FileText, Check } from './icons';
import { motion, AnimatePresence } from 'motion/react';
import { useNavigate } from 'react-router-dom';

const API_BASE_URL = (import.meta as any).env.VITE_API_BASE_URL || 'https://bf4investapp-production.up.railway.app/api';
const PUBLIC_PRODUCTS_URL = `${API_BASE_URL}/public/produits`;

type PublicProductDto = {
  id: string;
  refArticle?: string;
  designation?: string;
  unite?: string;
  prixVentePondereHT?: number;
  tva?: number;
  quantiteEnStock?: number;
  imageBase64?: string;
  imageContentType?: string;
};

const getProductImage = (product: Product): string => {
  // Si une image vient du backend, la privilégier
  if (product.imageUrl) {
    return product.imageUrl;
  }

  const categoryImages: Record<ProductCategory, string> = {
    'Matériaux de Construction et Granulats':
      'https://images.unsplash.com/photo-1621416894569-0f39ed31d247?auto=format&fit=crop&q=80&w=800',
    'Aciers et Fils':
      'https://images.unsplash.com/photo-1564182842519-8a3b2af3e228?auto=format&fit=crop&q=80&w=800',
    'Tubes et Canalisations':
      'https://images.unsplash.com/photo-1541336528065-8f1fdc435835?auto=format&fit=crop&q=80&w=800',
    'Robinetterie et Accessoires':
      'https://images.unsplash.com/photo-1535063404217-42781c80a521?auto=format&fit=crop&q=80&w=800',
    'Pièces de Raccordement et Brides':
      'https://images.unsplash.com/photo-1531693251400-38df35776de7?auto=format&fit=crop&q=80&w=800',
    'Colliers et Petits Raccords PE':
      'https://plus.unsplash.com/premium_photo-1664302152996-1048c1791a01?auto=format&fit=crop&q=80&w=800',
    'Outillage, Équipement et Services':
      'https://images.unsplash.com/photo-1581244277943-fe4a9c777189?auto=format&fit=crop&q=80&w=800',
    'Divers et Signalisation':
      'https://images.unsplash.com/photo-1529321044792-54a26f2f9cb9?auto=format&fit=crop&q=80&w=800',
    'Boulonnerie et Visserie':
      'https://images.unsplash.com/photo-1530124560676-43bc27aa8596?auto=format&fit=crop&q=80&w=800',
    'Outillage Industriel':
      'https://images.unsplash.com/photo-1534073828943-f801091bb270?auto=format&fit=crop&q=80&w=800',
    'Matériaux de Construction':
      'https://images.unsplash.com/photo-1504307651254-35680f356dfd?auto=format&fit=crop&q=80&w=800',
    'Signalisation et EPI':
      'https://images.unsplash.com/photo-1529321044792-54a26f2f9cb9?auto=format&fit=crop&q=80&w=800',
    Tous:
      'https://images.unsplash.com/photo-1518391846015-55a77d00ce8a?auto=format&fit=crop&q=80&w=800',
  };

  return categoryImages[product.category] || categoryImages['Tous'];
};

interface ProductCardProps {
  product: Product;
  onContactClick: () => void;
}

const ProductCard: React.FC<ProductCardProps> = ({ product, onContactClick }) => {

  return (
    <article className="group flex flex-col overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-light-gray/60 transition-all hover:-translate-y-1 hover:shadow-lg">
      {/* Badge stock */}
      <div className="flex items-center justify-between px-4 pt-4 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
        <span className="flex items-center gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
          En stock
        </span>
      </div>

      {/* Image */}
      <div className="relative mt-3 h-40 overflow-hidden bg-slate-100">
        <img
          src={getProductImage(product)}
          alt={product.name}
          className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
      </div>

      {/* Infos */}
      <div className="flex flex-1 flex-col gap-2 px-4 py-4 text-sm">
        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-accent">
          {product.category}
        </p>
        <h3 className="text-sm font-semibold text-primary">{product.name}</h3>

        {/* Prix */}
        <div className="mt-2 flex items-baseline gap-2">
          {typeof product.price === 'number' ? (
            <>
              <span className="text-lg font-extrabold text-primary">
                {product.price.toFixed(2)} DH HT
              </span>
              <span className="text-xs text-secondary">/ {product.unit}</span>
            </>
          ) : (
            <span className="text-sm font-semibold text-primary">Sur devis</span>
          )}
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center justify-between border-t border-light-gray px-4 py-3">
        <button
          type="button"
          className="flex items-center gap-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-secondary hover:text-primary"
        >
          <FileText className="h-3.5 w-3.5" />
          Fiche tech.
        </button>
        <button
          type="button"
          onClick={onContactClick}
          className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-white shadow-md transition-all hover:-translate-y-0.5 hover:bg-accent-hover hover:shadow-lg"
          aria-label="Commander"
        >
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </article>
  );
};

export const ProductsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [activeCategory, setActiveCategory] = useState<ProductCategory>('Tous');
  const [apiProducts, setApiProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true);
        const res = await fetch(PUBLIC_PRODUCTS_URL);
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data: PublicProductDto[] = await res.json();
        const mapped: Product[] = data.map((p) => {
          const name = p.designation || p.refArticle || 'Produit BF4 Invest';
          const unit = p.unite || 'U';
          const price = typeof p.prixVentePondereHT === 'number' ? p.prixVentePondereHT : 'Sur demande';
          const imageUrl =
            p.imageBase64 && p.imageContentType
              ? `data:${p.imageContentType};base64,${p.imageBase64}`
              : undefined;

          // Faute de catégorie dans le backend, on rattache au bloc Matériaux de Construction
          const category: ProductCategory = 'Matériaux de Construction';

          return { name, ref: p.refArticle, unit, price, category, imageUrl };
        });
        setApiProducts(mapped);
      } catch (err) {
        // En cas d'erreur, on reste sur le catalogue statique
        console.warn('Impossible de charger les produits publics BF4 depuis le backend :', err);
      } finally {
        setLoading(false);
      }
    };

    fetchProducts();
  }, []);

  const sourceProducts = apiProducts.length > 0 ? apiProducts : PRODUCTS;

  const filteredProducts = useMemo(() => {
    return sourceProducts.filter((product) => {
      const matchesCategory = activeCategory === 'Tous' || product.category === activeCategory;
      const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [searchTerm, activeCategory, sourceProducts]);

  return (
    <section className="bg-light py-10">
      <div className="mx-auto max-w-6xl px-6 md:px-10">
        {/* En-tête */}
        <header className="mb-8 flex flex-col gap-4 md:mb-10 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-accent">Catalogue 2025</p>
            <h2 className="mt-2 text-2xl font-extrabold text-primary md:text-3xl">Nos Produits</h2>
            <p className="mt-2 text-sm text-secondary">
              Plus de 500 références disponibles immédiatement sur notre parc logistique de Meknès.
            </p>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-2 rounded-sm border border-light-gray px-4 py-2 text-xs font-semibold text-primary hover:border-accent hover:text-accent"
          >
            <Download className="h-4 w-4" />
            Télécharger PDF
          </button>
        </header>

        <div className="grid gap-8 lg:grid-cols-[260px,1fr]">
          {/* Filtres */}
          <aside className="space-y-6">
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-[0.25em] text-primary">
                Départements
              </h3>
              <div className="mt-4 flex flex-col gap-1">
                {PRODUCT_CATEGORIES.map((cat) => (
                  <button
                    key={cat}
                    type="button"
                    onClick={() => setActiveCategory(cat)}
                    className={`flex w-full items-center justify-between border-l-2 px-4 py-2 text-left text-sm font-medium transition-all ${
                      activeCategory === cat
                        ? 'border-accent bg-light text-primary'
                        : 'border-transparent text-gray-500 hover:bg-light hover:text-primary'
                    }`}
                  >
                    <span>{cat}</span>
                    {activeCategory === cat && <Check className="h-4 w-4 text-accent" />}
                  </button>
                ))}
              </div>
            </div>
            <div className="rounded-lg bg-white p-4 text-xs text-secondary shadow-sm">
              <p className="font-semibold text-primary">Besoin spécifique ?</p>
              <p className="mt-1">
                Nous sourçons pour vous les matériaux hors-catalogue auprès de nos partenaires industriels.
              </p>
              <p className="mt-2 font-semibold text-accent">Appeler le dépôt</p>
              <p>+212 6 61 35 03 36</p>
            </div>
          </aside>

          {/* Contenu principal */}
          <div className="space-y-4">
            {/* Barre de recherche */}
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Rechercher une référence (ex: vis, groupe électrogène, tube PVC...)"
                className="w-full rounded-sm border border-light-gray bg-light px-12 py-4 text-sm text-primary outline-none placeholder:text-gray-400 focus:border-accent focus:ring-2 focus:ring-accent/20"
              />
            </div>

            <div className="flex items-center justify-between text-xs text-secondary">
              <span>
                {filteredProducts.length} référence{filteredProducts.length > 1 ? 's' : ''} trouvée
                {filteredProducts.length > 1 ? 's' : ''}
                {apiProducts.length > 0 && ' (données temps réel)'}
              </span>
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  className="text-xs font-bold text-red-500 hover:text-red-700"
                >
                  Réinitialiser
                </button>
              )}
            </div>

            {/* Grille produits */}
            {loading ? (
              <p className="py-10 text-center text-sm text-secondary">Chargement des produits en cours...</p>
            ) : filteredProducts.length > 0 ? (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {filteredProducts.map((p) => (
                  <ProductCard
                    key={`${p.name}-${p.unit}`}
                    product={p}
                    onContactClick={() => navigate('/contact')}
                  />
                ))}
              </div>
            ) : (
              <div className="space-y-3 rounded-lg bg-white p-6 text-sm text-secondary shadow-sm">
                <p>Aucun produit ne correspond à votre recherche.</p>
                <button
                  type="button"
                  onClick={() => {
                    setSearchTerm('');
                    setActiveCategory('Tous');
                  }}
                  className="text-sm font-bold text-accent underline"
                >
                  Voir tout le catalogue
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};


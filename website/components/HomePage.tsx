import React from 'react';
import { PRODUCTS, STATS } from '../constants';
import type { Product, ProductCategory } from '../types';
import { ArrowRight, CheckCircle2, ShieldCheck, Truck, TimerReset, Building2, Boxes } from './icons';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';

const API_BASE_URL = (import.meta as any).env.VITE_API_BASE_URL || 'https://bf4investapp-production.up.railway.app/api';
const PUBLIC_PRODUCTS_URL = `${API_BASE_URL}/public/produits`;

const DEFAULT_CATEGORY: ProductCategory = 'Matériaux de Construction';

const mapCategorieToProductCategory = (categorie?: string): ProductCategory => {
  if (!categorie) return DEFAULT_CATEGORY;
  const normalized = categorie.trim().toLowerCase();
  const mapping: Record<string, ProductCategory> = {
    'tous': 'Tous',
    'boulonnerie et visserie': 'Boulonnerie et Visserie',
    'outillage industriel': 'Outillage Industriel',
    'aciers et fils': 'Aciers et Fils',
    'tubes et canalisations': 'Tubes et Canalisations',
    'matériaux de construction': 'Matériaux de Construction',
    'materiaux de construction': 'Matériaux de Construction',
    'signalisation et epi': 'Signalisation et EPI',
  };
  return mapping[normalized] ?? DEFAULT_CATEGORY;
};

const HeroSection: React.FC = () => {
  const navigate = useNavigate();

  return (
    <section className="relative overflow-hidden pt-8">
      <div className="absolute inset-0 grid-luxe opacity-35" />
      <div className="section-shell relative z-10 grid gap-8 py-12 md:grid-cols-[1.2fr,0.8fr] md:py-24">
        <div>
          <p className="section-kicker">Supply Partner Premium - Maroc</p>
          <h1 className="mt-4 max-w-3xl font-display text-3xl leading-tight text-primary sm:text-4xl md:text-6xl">
            Le partenaire de confiance pour les achats BTP à haute exigence.
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-relaxed text-ink-soft">
            BF4 Invest accompagne les entreprises de construction, d’industrie et de génie civil avec une offre fiable:
            disponibilité réelle, conformité technique, et exécution logistique maîtrisée sur tout le Maroc.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <button type="button" onClick={() => navigate('/contact')} className="btn-primary">
              Demander une offre stratégique
              <ArrowRight className="h-4 w-4" />
            </button>
            <button type="button" onClick={() => navigate('/produits')} className="btn-secondary">
              Explorer le catalogue en temps réel
            </button>
          </div>
          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-3 text-sm text-slate-600">
            <span className="inline-flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-success" />
              Devis sous 4 heures ouvrées
            </span>
            <span className="inline-flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-success" />
              Stock synchronisé avec l’application
            </span>
            <span className="inline-flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-success" />
              Accompagnement technique expert
            </span>
          </div>
        </div>

        <div className="glass-surface rounded-3xl p-6">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">Niveau de service</p>
          <h3 className="mt-3 font-display text-2xl text-primary">Un standard opérationnel de référence</h3>
          <div className="mt-6 space-y-4">
            {[
              { icon: ShieldCheck, title: 'Conformité technique', text: 'Produits tracés, documents techniques disponibles.' },
              { icon: Truck, title: 'Logistique chantier', text: 'Livraison nationale planifiée selon vos contraintes terrain.' },
              { icon: TimerReset, title: 'Réactivité commerciale', text: 'Équipe disponible pour vos priorités critiques.' },
            ].map((item) => (
              <div key={item.title} className="card-premium p-4">
                <div className="flex items-start gap-3">
                  <item.icon className="mt-0.5 h-5 w-5 text-accent" />
                  <div>
                    <p className="text-sm font-semibold text-primary">{item.title}</p>
                    <p className="mt-1 text-xs text-ink-soft">{item.text}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};

const ProofSection: React.FC = () => (
  <section className="section-shell py-6 md:py-8">
    <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-4">
      {STATS.map((stat, idx) => (
        <motion.article
          key={stat.label}
          initial={{ opacity: 0, y: 16 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ delay: idx * 0.08 }}
          viewport={{ once: true }}
          className="card-premium p-5"
        >
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">{stat.label}</p>
          <p className="mt-3 text-3xl font-extrabold text-primary">
            {stat.value}
            {stat.suffix}
          </p>
        </motion.article>
      ))}
    </div>
  </section>
);

const CapabilitiesSection: React.FC = () => {
  const navigate = useNavigate();
  const capabilities = [
    {
      icon: Building2,
      title: 'Approvisionnement multisectoriel',
      description: 'Boulonnerie, aciers, tubes, outillage et consommables techniques pour BTP et industrie.',
    },
    {
      icon: Boxes,
      title: 'Stock réel exploitable',
      description: 'Vos équipes consultent des références disponibles en temps réel, sans approximations commerciales.',
    },
    {
      icon: Truck,
      title: 'Exécution logistique maîtrisée',
      description: 'Livraisons cadencées, suivi opérationnel et coordination adaptée à vos rythmes de chantier.',
    },
  ];

  return (
    <section className="section-shell py-10 md:py-14">
      <div className="mb-8">
        <p className="section-kicker">Ce qui nous différencie</p>
        <h2 className="section-title">Une infrastructure commerciale conçue pour les décideurs exigeants</h2>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        {capabilities.map((cap) => (
          <article key={cap.title} className="card-premium p-6">
            <cap.icon className="h-6 w-6 text-accent" />
            <h3 className="mt-4 text-lg font-semibold text-primary">{cap.title}</h3>
            <p className="mt-2 text-sm leading-relaxed text-ink-soft">{cap.description}</p>
          </article>
        ))}
      </div>

      <div className="mt-8 card-premium flex flex-col items-start justify-between gap-4 p-6 md:flex-row md:items-center">
        <div>
          <p className="text-sm font-semibold text-primary">Vous avez un besoin prioritaire ou un chantier critique ?</p>
          <p className="mt-1 text-sm text-ink-soft">
            Notre équipe construit une réponse technique et financière sur mesure pour votre contexte.
          </p>
        </div>
        <button type="button" onClick={() => navigate('/contact')} className="btn-accent">
          Parler à un expert BF4
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </section>
  );
};

const ProductsTeaserSection: React.FC = () => {
  const navigate = useNavigate();
  const [teaserProducts, setTeaserProducts] = React.useState<Product[]>(PRODUCTS.slice(0, 3));

  React.useEffect(() => {
    const loadProducts = async () => {
      try {
        const res = await fetch(PUBLIC_PRODUCTS_URL);
        if (!res.ok) return;
        const data = await res.json();
        if (!Array.isArray(data)) return;

        const mapped: Product[] = data.slice(0, 3).map((item: any) => ({
          name: item.designation || item.refArticle || 'Produit BF4 Invest',
          ref: item.refArticle,
          unit: item.unite || 'U',
          price: typeof item.prixVentePondereHT === 'number' ? item.prixVentePondereHT : 'Sur demande',
          category: mapCategorieToProductCategory(item.categorie),
          imageUrl:
            item.imageBase64 && item.imageContentType
              ? `data:${item.imageContentType};base64,${item.imageBase64}`
              : undefined,
        }));

        if (mapped.length > 0) setTeaserProducts(mapped);
      } catch {
        // Fallback silencieux sur catalogue statique
      }
    };

    loadProducts();
  }, []);

  return (
    <section className="section-shell pb-14">
      <div className="mb-7">
        <p className="section-kicker">Aperçu catalogue</p>
        <h2 className="section-title">Une visibilité immédiate sur les références clés</h2>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {teaserProducts.map((p) => (
          <article key={`${p.name}-${p.unit}`} className="card-premium p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">{p.category}</p>
            <h3 className="mt-3 min-h-[48px] text-sm font-semibold text-primary">{p.name}</h3>
            <p className="mt-2 text-xs text-ink-soft">
              {typeof p.price === 'number' ? `${p.price.toFixed(2)} MAD HT` : 'Tarif sur devis'} - {p.unit}
            </p>
          </article>
        ))}
      </div>
      <div className="mt-6">
        <button type="button" onClick={() => navigate('/produits')} className="btn-secondary">
          Voir tout le catalogue
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </section>
  );
};

export const HomePage: React.FC = () => {
  return (
    <div>
      <HeroSection />
      <ProofSection />
      <CapabilitiesSection />
      <ProductsTeaserSection />
    </div>
  );
};


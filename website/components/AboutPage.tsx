import React, { useRef, useState, useEffect } from 'react';
import { Shield, Diamond, BrainCircuit, Check, Truck, Users, Building } from './icons';
import { useNavigate } from 'react-router-dom';

const useOnScreen = (options: IntersectionObserverInit) => {
  const ref = useRef<HTMLDivElement | null>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setIsVisible(true);
        observer.unobserve(entry.target);
      }
    }, options);

    if (ref.current) {
      observer.observe(ref.current);
    }

    return () => {
      if (ref.current) {
        observer.unobserve(ref.current);
      }
    };
  }, [options]);

  return [ref, isVisible] as const;
};

export const AboutPage: React.FC = () => {
  const navigate = useNavigate();
  const [ref, isVisible] = useOnScreen({ threshold: 0.1 });

  return (
    <section className="bg-light py-16">
      <div className="mx-auto max-w-6xl px-6 md:px-10">
        <div className="mb-12 grid gap-8 md:grid-cols-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-accent">Depuis 2009</p>
            <h2 className="mt-3 text-3xl font-extrabold text-primary md:text-4xl">
              L'Expertise BTP.
              <br />
              La Puissance Logistique.
            </h2>
            <p className="mt-4 text-sm text-secondary md:text-base">
              BF4 Invest n'est pas un simple fournisseur. Nous sommes le partenaire stratégique des constructeurs qui
              façonnent le Maroc de demain.
            </p>
          </div>
          <div className="space-y-4 text-sm text-secondary md:text-base">
            <p>
              Fondée à Meknès, BF4 Invest s'est construite sur un constat simple : les grands chantiers souffrent trop
              souvent de ruptures de stock et de matériaux non conformes.
            </p>
            <p>
              Nous avons décidé de changer la donne. En investissant massivement dans nos capacités de stockage et en
              nouant des partenariats directs avec les usines (Sonasid, LafargeHolcim), nous avons créé un hub
              logistique capable de servir les projets les plus exigeants, du génie civil aux complexes résidentiels.
            </p>
          </div>
        </div>

        <div
          ref={ref}
          className={`grid gap-6 md:grid-cols-3 transition-opacity duration-700 ${
            isVisible ? 'opacity-100' : 'opacity-0'
          }`}
        >
          <div className="rounded-xl bg-white p-6 shadow-sm">
            <Shield className="h-8 w-8 text-accent" />
            <h3 className="mt-4 text-sm font-semibold text-primary">Conformité totale</h3>
            <p className="mt-2 text-xs text-secondary">
              Pas de compromis sur la sécurité. Aciers certifiés NM, Ciments normes ISO. Chaque livraison est
              accompagnée de ses certificats d'origine.
            </p>
          </div>
          <div className="rounded-xl bg-white p-6 shadow-sm">
            <Truck className="h-8 w-8 text-accent" />
            <h3 className="mt-4 text-sm font-semibold text-primary">Logistique JIT</h3>
            <p className="mt-2 text-xs text-secondary">
              \"Just-In-Time\". Notre flotte de camions et nos partenaires logistiques assurent des livraisons cadencées
              pour ne jamais arrêter vos équipes.
            </p>
          </div>
          <div className="rounded-xl bg-white p-6 shadow-sm">
            <BrainCircuit className="h-8 w-8 text-accent" />
            <h3 className="mt-4 text-sm font-semibold text-primary">Expertise technique</h3>
            <p className="mt-2 text-xs text-secondary">
              Nos commerciaux sont des techniciens. Ils comprennent vos plans, vos contraintes de ferraillage et vos
              besoins en hydraulique.
            </p>
          </div>
        </div>

        <div className="mt-12 rounded-xl bg-primary px-8 py-10 text-white md:flex md:items-center md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-emerald-400">
              Rejoignez les leaders du BTP
            </p>
            <h3 className="mt-3 text-xl font-extrabold md:text-2xl">
              Ouvrez un compte professionnel BF4 Invest et bénéficiez de tarifs préférentiels.
            </h3>
          </div>
          <button
            type="button"
            onClick={() => navigate('/contact')}
            className="mt-6 rounded bg-white px-8 py-3 text-xs font-bold uppercase tracking-[0.2em] text-primary shadow-lg md:mt-0"
          >
            Contacter la direction
          </button>
        </div>
      </div>
    </section>
  );
};


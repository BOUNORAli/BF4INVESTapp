import React from 'react';
import { ShieldCheck, Truck, BadgeCheck, Users, ArrowRight } from './icons';
import { useNavigate } from 'react-router-dom';

export const AboutPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <section className="section-shell py-12 md:py-20">
      <p className="section-kicker">À propos de BF4 Invest</p>
      <h2 className="section-title">Une culture de fiabilité au service des professionnels du BTP</h2>
      <p className="section-subtitle">
        Depuis Meknès, BF4 Invest accompagne des acteurs publics et privés dans leurs approvisionnements stratégiques.
        Notre mission est simple: sécuriser vos achats techniques avec un haut niveau de transparence, de disponibilité
        et d’exécution.
      </p>

      <div className="mt-10 grid gap-6 md:grid-cols-[1.1fr,0.9fr]">
        <article className="card-premium p-6 md:p-7">
          <h3 className="font-display text-2xl text-primary">Notre approche</h3>
          <div className="mt-5 space-y-4 text-sm leading-relaxed text-ink-soft">
            <p>
              Nous construisons chaque collaboration sur la même exigence que vos chantiers: fiabilité opérationnelle,
              conformité technique et engagement sur les délais annoncés.
            </p>
            <p>
              Nos équipes combinent expertise produit, lecture des contraintes terrain et coordination logistique pour
              garantir des livraisons pertinentes, au bon moment, avec le bon niveau de service.
            </p>
            <p>
              Ce positionnement nous permet de devenir plus qu’un fournisseur: un partenaire de décision dans vos
              achats BTP et industriels.
            </p>
          </div>
        </article>

        <article className="card-premium p-6 md:p-7">
          <h3 className="font-display text-2xl text-primary">Ce que vous obtenez</h3>
          <div className="mt-5 space-y-4">
            {[
              {
                icon: ShieldCheck,
                title: 'Conformité et traçabilité',
                text: 'Spécifications claires, documentation disponible et processus contrôlés.',
              },
              {
                icon: Truck,
                title: 'Performance logistique',
                text: 'Organisation des livraisons selon votre planning chantier.',
              },
              {
                icon: BadgeCheck,
                title: 'Engagement qualité',
                text: 'Sélection des références selon des critères stricts de performance.',
              },
              {
                icon: Users,
                title: 'Accompagnement dédié',
                text: 'Un interlocuteur commercial réactif pour vos besoins prioritaires.',
              },
            ].map((item) => (
              <div key={item.title} className="rounded-xl border border-[color:var(--color-border-subtle)] bg-[color:var(--color-surface-soft)] p-4">
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
        </article>
      </div>

      <div className="mt-8 card-premium flex flex-col items-start justify-between gap-4 p-6 md:flex-row md:items-center">
        <div>
          <p className="text-sm font-semibold text-primary">Discutons de vos besoins d’approvisionnement</p>
          <p className="mt-1 text-sm text-ink-soft">
            Notre équipe vous propose une réponse concrète, adaptée à votre activité et à vos objectifs budgétaires.
          </p>
        </div>
        <button type="button" onClick={() => navigate('/contact')} className="btn-accent">
          Contacter BF4 Invest
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
    </section>
  );
};


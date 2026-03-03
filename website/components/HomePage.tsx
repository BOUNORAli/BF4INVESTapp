import React from 'react';
import { STATS } from '../constants';
import { Clock, Check, Truck, Shield } from './icons';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';

const AnimatedCounter: React.FC<{ target: number; suffix: string }> = ({ target, suffix }) => {
  const [count, setCount] = React.useState(0);
  const [isVisible, setIsVisible] = React.useState(false);
  const ref = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(entry.target);
        }
      },
      { threshold: 0.5 }
    );
    if (ref.current) observer.observe(ref.current);
    return () => {
      if (ref.current) observer.unobserve(ref.current);
    };
  }, []);

  React.useEffect(() => {
    if (!isVisible) return;
    let start = 0;
    const duration = 2500;
    const step = (timestamp: number) => {
      if (!start) start = timestamp;
      const progress = Math.min((timestamp - start) / duration, 1);
      const ease = 1 - Math.pow(1 - progress, 4);
      setCount(Math.floor(ease * target));
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [isVisible, target]);

  return (
    <div ref={ref} className="text-3xl font-extrabold text-white">
      {count}
      {suffix}
    </div>
  );
};

const HeroSection: React.FC = () => {
  const navigate = useNavigate();

  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-primary via-primary to-secondary text-white">
      <div className="absolute inset-0 opacity-20 bg-dots" />
      <div className="mx-auto flex max-w-6xl flex-col gap-10 px-6 py-20 md:flex-row md:items-center md:px-10">
        <div className="relative z-10 max-w-xl space-y-6">
          <p className="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-1 text-xs font-semibold uppercase tracking-[0.25em]">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            Stock Réel à Meknès
          </p>
          <h1 className="text-3xl font-extrabold leading-tight md:text-4xl lg:text-5xl">
            Boulonnerie &amp; Outillage industriel
            <br />
            pour vos chantiers critiques.
          </h1>
          <p className="text-sm text-slate-200 md:text-base">
            Importateur direct Turquie &amp; Europe. Disponibilité immédiate, conformité DIN/ISO et logistique JIT pour vos
            projets BTP et industriels partout au Maroc.
          </p>
          <div className="flex flex-wrap items-center gap-4">
            <button
              type="button"
              onClick={() => navigate('/produits')}
              className="px-8 py-3 text-xs font-bold uppercase tracking-[0.2em] text-white bg-accent hover:bg-accent-hover shadow-xl shadow-accent/20"
            >
              Consulter le stock
            </button>
            <button
              type="button"
              onClick={() => navigate('/contact')}
              className="px-8 py-3 text-xs font-bold uppercase tracking-[0.2em] border border-white/30 text-white hover:bg-white hover:text-primary transition-colors"
            >
              Demander un devis (4h)
            </button>
          </div>
        </div>
        <div className="relative z-10 grid flex-1 gap-4 md:grid-cols-2">
          {STATS.map((stat, idx) => (
            <motion.div
              key={stat.label}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
              viewport={{ once: true }}
              className="rounded-xl bg-white/5 p-5 backdrop-blur"
            >
              <p className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-300">{stat.label}</p>
              <AnimatedCounter target={stat.value} suffix={stat.suffix} />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

const TrustBar: React.FC = () => (
  <section className="border-y border-light-gray bg-light">
    <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4 md:px-10">
      {[
        { icon: Shield, title: 'Qualité', desc: 'Import Direct Turquie' },
        { icon: Clock, title: 'Réactivité', desc: 'Devis sous 4 Heures' },
        { icon: Truck, title: 'Logistique', desc: 'Livraison Nationale' },
        { icon: Check, title: 'Garantie', desc: 'Stock Réel 100%' },
      ].map((item) => (
        <div key={item.title} className="flex items-center gap-3 text-xs text-secondary">
          <item.icon className="h-5 w-5 text-accent" />
          <div>
            <div className="font-semibold text-primary">{item.title}</div>
            <div className="text-[11px]">{item.desc}</div>
          </div>
        </div>
      ))}
    </div>
  </section>
);

export const HomePage: React.FC = () => {
  return (
    <div className="bg-background-light">
      <HeroSection />
      <TrustBar />
    </div>
  );
};


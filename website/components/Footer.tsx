import React from 'react';
import { Logo, Phone, Mail, MapPin, Linkedin, Facebook, ArrowUpRight } from './icons';
import { NAV_LINKS } from '../constants';
import { Link } from 'react-router-dom';

const APP_URL = (import.meta as any).env.VITE_APP_URL || 'https://bf4invest-app.vercel.app';

export const Footer: React.FC = () => {

  return (
    <footer className="mt-16 border-t border-[color:var(--color-border-subtle)] bg-primary text-slate-200">
      <div className="section-shell grid gap-8 py-12 md:grid-cols-[1.7fr,1.1fr,1.1fr,1fr]">
        <div className="space-y-4 md:pr-6">
          <div className="flex items-center gap-3">
            <Logo className="h-9 w-28" light />
            <span className="text-xs uppercase tracking-[0.2em] text-slate-300">Supply Partner BTP</span>
          </div>
          <p className="text-sm text-slate-300">
            BF4 Invest accompagne les entreprises du BTP et de l'industrie avec une approche premium: stock réel,
            pilotage commercial exigeant et exécution logistique fiable.
          </p>
          <a
            href={`${APP_URL}/login`}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-accent"
          >
            Accéder à l'Espace Pro
            <ArrowUpRight className="h-4 w-4" />
          </a>
        </div>

        <div>
          <h3 className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Navigation</h3>
          <nav className="mt-4 space-y-2 text-sm">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                className="block text-slate-300 transition-colors hover:text-white"
              >
                {link.name}
              </Link>
            ))}
          </nav>
        </div>

        <div>
          <h3 className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Accès rapide</h3>
          <nav className="mt-4 space-y-2 text-sm">
            <Link to="/produits" className="block text-slate-300 transition-colors hover:text-white">
              Catalogue produits
            </Link>
            <Link to="/contact" className="block text-slate-300 transition-colors hover:text-white">
              Demande de devis
            </Link>
            <a
              href={`${APP_URL}/login`}
              target="_blank"
              rel="noopener noreferrer"
              className="block text-slate-300 transition-colors hover:text-white"
            >
              Espace Pro
            </a>
          </nav>
        </div>

        <div className="space-y-3 text-sm">
          <h3 className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Informations B2B</h3>
          <div className="flex items-start gap-2">
            <MapPin className="mt-0.5 h-4 w-4 text-accent" />
            <p>Quartier Industriel, Meknès, Maroc</p>
          </div>
          <div className="flex items-start gap-2">
            <Phone className="mt-0.5 h-4 w-4 text-accent" />
            <p>+212 6 61 35 03 36</p>
          </div>
          <div className="flex items-start gap-2">
            <Mail className="mt-0.5 h-4 w-4 text-accent" />
            <p>bf4invest@gmail.com</p>
          </div>
          <p className="text-xs text-slate-400">
            RC: 54287 &nbsp;·&nbsp; ICE: 002889872000062 &nbsp;·&nbsp; IF: 2435421
          </p>
        </div>
      </div>
      <div className="border-t border-slate-800">
        <div className="section-shell flex flex-col items-start justify-between gap-3 py-4 text-xs text-slate-500 md:flex-row md:items-center">
          <p>© {new Date().getFullYear()} BF4 Invest SARL AU. Tous droits réservés.</p>
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-700 text-slate-300 hover:border-accent hover:text-accent"
            >
              <Linkedin className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              className="flex h-7 w-7 items-center justify-center rounded-full border border-slate-700 text-slate-300 hover:border-accent hover:text-accent"
            >
              <Facebook className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>
    </footer>
  );
};


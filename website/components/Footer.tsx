import React from 'react';
import { Logo, Phone, Mail, MapPin, Linkedin, Facebook } from './icons';
import { NAV_LINKS } from '../constants';
import { Link } from 'react-router-dom';

const APP_URL = (import.meta as any).env.VITE_APP_URL || 'https://bf4invest-app.vercel.app';

export const Footer: React.FC = () => {

  return (
    <footer className="mt-10 bg-primary text-slate-200">
      <div className="mx-auto grid max-w-6xl gap-8 px-6 py-10 md:grid-cols-[2fr,1.3fr,1.3fr] md:px-10">
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <Logo className="h-8 w-24" light />
          </div>
          <p className="text-sm text-slate-300">
            Partenaire industriel de premier plan à Meknès. Spécialiste de la boulonnerie certifiée, de l'outillage
            professionnel et du négoce d'acier.
          </p>
        </div>

        <div>
          <h3 className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Navigation</h3>
          <nav className="mt-3 space-y-1 text-sm">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                className="block text-slate-300 hover:text-white"
              >
                {link.name}
              </Link>
            ))}
            <Link to="/produits" className="block text-slate-300 hover:text-white">
              Boulonnerie technique
            </Link>
            <Link to="/produits" className="block text-slate-300 hover:text-white">
              Outillage de chantier
            </Link>
            <a
              href={`${APP_URL}/login`}
              target="_blank"
              rel="noopener noreferrer"
              className="block text-slate-300 hover:text-white"
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
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4 text-xs text-slate-500 md:px-10">
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


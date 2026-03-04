import React, { useState, useEffect } from 'react';
import { NAV_LINKS } from '../constants';
import { Menu, X, Phone, Clock3, FileText, Lock, ArrowUpRight } from './icons';
import { motion, AnimatePresence } from 'motion/react';
import { useLocation, useNavigate } from 'react-router-dom';

const API_BASE_URL = (import.meta as any).env.VITE_API_BASE_URL || 'https://bf4investapp-production.up.railway.app/api';
const LOGO_URL = `${API_BASE_URL}/settings/logo`;
const APP_URL = (import.meta as any).env.VITE_APP_URL || 'https://bf4invest-app.vercel.app';

const TopBar: React.FC = () => (
  <div className="hidden border-b border-slate-700/70 bg-primary text-xs text-slate-200 md:block">
    <div className="section-shell flex items-center justify-between py-2">
      <div className="flex items-center gap-2">
        <Clock3 className="h-3.5 w-3.5 text-accent" />
        <span>Service commercial ouvert du lundi au samedi</span>
      </div>
      <div className="flex items-center gap-5">
        <span className="hidden lg:block">Livraison nationale et logistique chantier sous 24h à 72h</span>
        <span className="flex items-center gap-2">
          <Phone className="h-3.5 w-3.5 text-accent" />
          +212 6 61 35 03 36
        </span>
      </div>
    </div>
  </div>
);

export const Header: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 6);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const goTo = (path: string, e?: React.MouseEvent) => {
    if (e) e.preventDefault();
    navigate(path);
    setIsOpen(false);
    window.scrollTo(0, 0);
  };

  const isActive = (href: string) => {
    if (href === '/') return location.pathname === '/' || location.pathname === '';
    return location.pathname.startsWith(href);
  };

  return (
    <header className="fixed inset-x-0 top-0 z-40">
      <TopBar />
      <div
        className={`transition-all ${
          isScrolled
            ? 'border-b border-[color:var(--color-border-subtle)] bg-white/95 shadow-lg backdrop-blur'
            : 'bg-white/95'
        }`}
      >
        <div className="section-shell flex items-center justify-between py-3 md:py-4">
          <button
            type="button"
            onClick={(e) => goTo('/', e)}
            className="flex items-center gap-3"
            aria-label="Retour accueil BF4 Invest"
          >
            <div className="flex h-10 w-28 items-center justify-center rounded-lg bg-white/80 p-1 shadow-sm">
              <img
                src={LOGO_URL}
                alt="Logo BF4 Invest"
                className="max-h-full max-w-full object-contain"
                onError={(ev) => {
                  (ev.currentTarget as HTMLImageElement).style.display = 'none';
                }}
              />
            </div>
            <div className="hidden text-left lg:block">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-accent">BF4 Invest</p>
              <p className="text-xs text-slate-600">Négoce BTP & solutions chantier</p>
            </div>
          </button>

          <nav className="hidden items-center gap-8 lg:flex">
            {NAV_LINKS.map((link) => (
              <button
                key={link.href}
                type="button"
                onClick={(e) => goTo(link.href, e)}
                className={`relative text-xs font-bold uppercase tracking-[0.16em] transition-colors ${
                  isActive(link.href) ? 'text-primary' : 'text-slate-500 hover:text-primary'
                }`}
              >
                {link.name}
                {isActive(link.href) && <span className="absolute -bottom-1 left-0 h-0.5 w-full bg-accent" />}
              </button>
            ))}
          </nav>

          <div className="hidden items-center gap-3 md:flex">
            <button type="button" onClick={(e) => goTo('/contact', e)} className="btn-primary">
              <FileText className="h-4 w-4" />
              Demander un devis
            </button>
            <button
              type="button"
              onClick={() => window.open(`${APP_URL}/login`, '_blank', 'noopener,noreferrer')}
              className="btn-secondary"
            >
              <Lock className="h-4 w-4" />
              Espace Pro
              <ArrowUpRight className="h-4 w-4" />
            </button>
          </div>

          <button
            type="button"
            onClick={() => setIsOpen((prev) => !prev)}
            className="rounded-xl border border-[color:var(--color-border-subtle)] bg-white p-2.5 text-primary shadow-sm md:hidden"
            aria-label="Ouvrir le menu"
          >
            {isOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {isOpen && (
          <motion.nav
            initial={{ opacity: 0, y: -14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -14 }}
            className="glass-surface mx-4 mt-2 rounded-2xl border border-[color:var(--color-border-subtle)] p-5 shadow-xl md:hidden"
          >
            <ul className="flex flex-col gap-3">
              {NAV_LINKS.map((link) => (
                <li key={link.href}>
                  <button
                    type="button"
                    onClick={(e) => goTo(link.href, e)}
                    className={`w-full rounded-lg px-3 py-2 text-left text-sm font-semibold ${
                      isActive(link.href) ? 'bg-slate-100 text-primary' : 'text-slate-600'
                    }`}
                  >
                    {link.name}
                  </button>
                </li>
              ))}
            </ul>
            <div className="mt-5 space-y-2">
              <button type="button" onClick={(e) => goTo('/contact', e)} className="btn-primary w-full">
                <FileText className="h-4 w-4" />
                Demander un devis
              </button>
              <button
                type="button"
                onClick={() => {
                  window.open(`${APP_URL}/login`, '_blank', 'noopener,noreferrer');
                  setIsOpen(false);
                }}
                className="btn-secondary w-full"
              >
                <Lock className="h-4 w-4" />
                Espace Pro
                <ArrowUpRight className="h-4 w-4" />
              </button>
            </div>
          </motion.nav>
        )}
      </AnimatePresence>
    </header>
  );
};


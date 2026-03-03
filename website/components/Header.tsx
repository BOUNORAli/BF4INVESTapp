import React, { useState, useEffect } from 'react';
import { NAV_LINKS } from '../constants';
import { Menu, X, Phone, Clock, Logo, FileText, Lock } from './icons';
import { motion, AnimatePresence } from 'motion/react';
import { useLocation, useNavigate } from 'react-router-dom';

const API_BASE_URL = (import.meta as any).env.VITE_API_BASE_URL || 'https://bf4investapp-production.up.railway.app/api';
const LOGO_URL = `${API_BASE_URL}/settings/logo`;
const APP_URL = (import.meta as any).env.VITE_APP_URL || 'https://bf4invest-app.vercel.app';

const TopBar: React.FC = () => (
  <div className="hidden w-full items-center justify-between bg-primary px-6 py-2 text-xs text-white md:flex">
    <div className="flex items-center gap-2">
      <Clock className="h-4 w-4" />
      <span>Meknès: 08:00 - 18:00</span>
    </div>
    <div className="flex items-center gap-4">
      <div className="flex items-center gap-2">
        <Phone className="h-4 w-4" />
        <span>+212 6 61 35 03 36</span>
      </div>
      <span className="hidden md:inline-block">Importateur direct BTP &amp; Industrie</span>
    </div>
  </div>
);

export const Header: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 10);
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
    if (href === '/') {
      return location.pathname === '/' || location.pathname === '';
    }
    return location.pathname.startsWith(href);
  };

  return (
    <header className="fixed inset-x-0 top-0 z-30">
      <TopBar />
      <div
        className={`flex items-center justify-between px-4 py-3 md:px-10 md:py-4 transition-all ${
          isScrolled ? 'bg-background-light/95 shadow-sm backdrop-blur' : 'bg-background-light'
        }`}
      >
        {/* Logo BF4 INVEST provenant du backend */}
        <button type="button" onClick={(e) => goTo('/', e)} className="flex items-center gap-3">
          <div className="flex h-10 w-28 items-center justify-center md:h-12 md:w-32">
            <img
              src={LOGO_URL}
              alt="BF4 Invest"
              className="max-h-full max-w-full object-contain"
              onError={(ev) => {
                // Fallback vers le logo vectoriel local
                (ev.currentTarget as HTMLImageElement).style.display = 'none';
              }}
            />
            {/* Fallback SVG si le logo HTTP ne charge pas */}
            <Logo className="h-8 w-24 md:h-10 md:w-32" aria-hidden="true" />
          </div>
        </button>

        {/* Navigation desktop */}
        <nav className="hidden items-center gap-8 md:flex">
          <div className="flex items-center gap-6">
            {NAV_LINKS.map((link) => (
              <button
                key={link.href}
                type="button"
                onClick={(e) => goTo(link.href, e)}
                className={`text-[11px] font-bold uppercase tracking-widest transition-all relative ${
                  isActive(link.href) ? 'text-accent' : 'text-primary hover:text-accent'
                }`}
              >
                {link.name}
                {isActive(link.href) && <span className="absolute -bottom-1 left-0 h-0.5 w-full bg-accent" />}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={(e) => goTo('/contact', e)}
            className="flex items-center gap-2 rounded-sm bg-primary px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-white shadow-md transition-all hover:bg-accent hover:shadow-lg"
          >
            <FileText className="h-4 w-4" />
            Demander un Devis
          </button>
          <button
            type="button"
            onClick={() => {
              window.open(`${APP_URL}/login`, '_blank', 'noopener,noreferrer');
            }}
            className="flex items-center gap-2 rounded-sm border border-primary px-6 py-3 text-[10px] font-bold uppercase tracking-widest text-primary shadow-sm transition-all hover:bg-primary hover:text-white hover:shadow-lg"
          >
            <Lock className="h-4 w-4" />
            Espace Pro
          </button>
        </nav>

        {/* Actions mobile */}
        <div className="flex items-center gap-3 md:hidden">
          <button
            type="button"
            onClick={(e) => goTo('/contact', e)}
            className="rounded bg-accent p-2 text-white shadow-md"
            aria-label="Demander un devis"
          >
            <FileText className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => setIsOpen(!isOpen)}
            className="p-2 text-primary"
            aria-label="Ouvrir le menu"
          >
            {isOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>
      </div>

      {/* Menu mobile */}
      <AnimatePresence>
        {isOpen && (
          <motion.nav
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="bg-background-light px-6 pb-6 pt-2 shadow-md md:hidden"
          >
            <ul className="flex flex-col gap-4">
              {NAV_LINKS.map((link) => (
                <li key={link.href}>
                  <button
                    type="button"
                    onClick={(e) => goTo(link.href, e)}
                    className={`text-lg font-extrabold uppercase ${
                      isActive(link.href) ? 'text-accent' : 'text-primary'
                    }`}
                  >
                    {link.name}
                  </button>
                </li>
              ))}
            </ul>
            <button
              type="button"
              onClick={(e) => goTo('/contact', e)}
              className="mt-6 w-full bg-accent py-4 text-xl font-bold uppercase text-white shadow-xl"
            >
              Demander un Devis
            </button>
            <button
              type="button"
              onClick={() => {
                window.open(`${APP_URL}/login`, '_blank', 'noopener,noreferrer');
                setIsOpen(false);
              }}
              className="mt-3 w-full rounded bg-primary py-4 text-xl font-bold uppercase text-white shadow-xl"
            >
              Espace Pro
            </button>
          </motion.nav>
        )}
      </AnimatePresence>
    </header>
  );
};


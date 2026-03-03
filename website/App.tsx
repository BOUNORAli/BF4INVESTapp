import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { HomePage } from './components/HomePage';
import { AboutPage } from './components/AboutPage';
import { ProductsPage } from './components/ProductsPage';
import { ContactPage } from './components/ContactPage';
import { Footer } from './components/Footer';
import { ChevronUp } from './components/icons';
import { ThemeContext, type Theme } from './types';
import { AnimatePresence, motion } from 'motion/react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';

const ScrollToTopButton: React.FC = () => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const toggleVisibility = () => {
      setIsVisible(window.pageYOffset > 300);
    };

    window.addEventListener('scroll', toggleVisibility);
    return () => {
      window.removeEventListener('scroll', toggleVisibility);
    };
  }, []);

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  if (!isVisible) return null;

  return (
    <button
      type="button"
      onClick={scrollToTop}
      className="fixed bottom-8 right-8 z-40 flex h-11 w-11 items-center justify-center rounded-full bg-primary text-white shadow-lg transition-colors hover:bg-slate-800"
      aria-label="Remonter en haut de la page"
    >
      <ChevronUp className="h-5 w-5" />
    </button>
  );
};

const AppContent: React.FC = () => {
  const location = useLocation();

  const [theme, setTheme] = useState<Theme>(() => {
    if (typeof window !== 'undefined' && window.localStorage) {
      const storedPrefs = window.localStorage.getItem('theme') as Theme | null;
      if (storedPrefs) return storedPrefs;
      const userMedia = window.matchMedia('(prefers-color-scheme: dark)');
      if (userMedia.matches) return 'dark';
    }
    return 'light';
  });

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove(theme === 'dark' ? 'light' : 'dark');
    root.classList.add(theme);
    window.localStorage.setItem('theme', theme);
  }, [theme]);

  // SEO: Dynamic Page Title based on route
  useEffect(() => {
    const path = location.pathname;

    if (path === '/' || path === '') {
      document.title = 'BF4 Invest - Négoce BTP Premium au Maroc';
    } else if (path.startsWith('/a-propos')) {
      document.title = 'À propos - BF4 Invest';
    } else if (path.startsWith('/produits')) {
      document.title = 'Catalogue Produits - BF4 Invest';
    } else if (path.startsWith('/contact')) {
      document.title = 'Demande de Devis - BF4 Invest';
    } else {
      document.title = 'BF4 Invest';
    }
  }, [location.pathname]);

  // Remonter en haut à chaque changement de route
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);

  const toggleTheme = () => {
    setTheme(theme === 'light' ? 'dark' : 'light');
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <div className="min-h-screen bg-background-light text-primary">
        <Header />
        <main className="pt-24 md:pt-28">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
            >
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/a-propos" element={<AboutPage />} />
                <Route path="/produits" element={<ProductsPage />} />
                <Route path="/contact" element={<ContactPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </motion.div>
          </AnimatePresence>
        </main>
        <Footer />
        <ScrollToTopButton />
      </div>
    </ThemeContext.Provider>
  );
};

const App: React.FC = () => {
  return <AppContent />;
};

export default App;


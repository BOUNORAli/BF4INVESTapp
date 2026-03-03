import React, { useState } from 'react';
import { FAQ_ITEMS } from '../constants';
import { MapPin, Phone, Mail, ChevronDown, ArrowRight, Check, Clock } from './icons';

const FORM_ENDPOINT = 'https://formspree.io/f/mjkpydkb';

export const ContactPage: React.FC = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    company: '',
    phone: '',
    subject: '',
    message: '',
  });
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle');
  const [openFaq, setOpenFaq] = useState<number | null>(null);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!FORM_ENDPOINT || FORM_ENDPOINT.includes('VOTRE_CODE')) {
      setStatus('error');
      return;
    }
    setStatus('submitting');
    try {
      const response = await fetch(FORM_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(formData),
      });
      if (response.ok) {
        setStatus('success');
        setFormData({ name: '', email: '', company: '', phone: '', subject: '', message: '' });
        setTimeout(() => setStatus('idle'), 5000);
      } else {
        setStatus('error');
      }
    } catch {
      setStatus('error');
    }
  };

  return (
    <section className="bg-light py-12">
      <div className="mx-auto max-w-6xl px-6 md:px-10">
        {/* Titre */}
        <header className="mb-8">
          <p className="text-xs font-semibold uppercase tracking-[0.25em] text-accent">
            Service client &amp; commercial
          </p>
          <h2 className="mt-2 text-2xl font-extrabold text-primary md:text-3xl">Parlons de votre projet</h2>
          <p className="mt-2 text-sm text-secondary md:text-base">
            Une question technique ? Une demande de prix ? Nos équipes vous répondent sous 24 heures ouvrées.
          </p>
        </header>

        <div className="grid gap-10 md:grid-cols-[1.2fr,1.5fr]">
          {/* Colonne informations */}
          <aside className="space-y-6 text-sm text-secondary">
            <div className="rounded-xl bg-white p-5 shadow-sm">
              <h3 className="mb-3 text-xs font-semibold uppercase tracking-[0.25em] text-primary">
                Coordonnées
              </h3>
              <div className="space-y-3">
                <div className="flex items-start gap-3">
                  <Phone className="mt-0.5 h-4 w-4 text-accent" />
                  <div>
                    <p className="text-xs font-semibold text-primary">Service commercial</p>
                    <p>+212 6 61 35 03 36</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <Mail className="mt-0.5 h-4 w-4 text-accent" />
                  <div>
                    <p className="text-xs font-semibold text-primary">Email</p>
                    <p>bf4invest@gmail.com</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <MapPin className="mt-0.5 h-4 w-4 text-accent" />
                  <div>
                    <p className="text-xs font-semibold text-primary">Siège &amp; Dépôt</p>
                    <p>Hamria, Meknès, Maroc</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-xl bg-white p-5 shadow-sm">
              <h3 className="mb-3 text-xs font-semibold uppercase tracking-[0.25em] text-primary">
                Horaires d'ouverture
              </h3>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span>Lundi - Vendredi</span>
                  <span className="font-medium">08:00 - 18:00</span>
                </div>
                <div className="flex items-center justify-between">
                  <span>Samedi</span>
                  <span className="font-medium">08:00 - 13:00</span>
                </div>
                <div className="flex items-center justify-between text-secondary">
                  <span>Dimanche</span>
                  <span className="font-medium">Fermé</span>
                </div>
              </div>
            </div>
          </aside>

          {/* Colonne formulaire */}
          <div className="space-y-8">
            <form
              onSubmit={handleSubmit}
              className="space-y-4 rounded-xl bg-white p-6 shadow-sm"
            >
              <h3 className="text-sm font-semibold text-primary">Formulaire de contact</h3>
              <p className="text-xs text-secondary">
                Les champs marqués d'un astérisque (*) sont obligatoires.
              </p>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-primary" htmlFor="name">
                    Nom complet *
                  </label>
                  <input
                    id="name"
                    name="name"
                    required
                    value={formData.name}
                    onChange={handleChange}
                    className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-primary" htmlFor="company">
                    Société
                  </label>
                  <input
                    id="company"
                    name="company"
                    value={formData.company}
                    onChange={handleChange}
                    className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-primary" htmlFor="email">
                    Email *
                  </label>
                  <input
                    id="email"
                    type="email"
                    name="email"
                    required
                    value={formData.email}
                    onChange={handleChange}
                    className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-primary" htmlFor="phone">
                    Téléphone
                  </label>
                  <input
                    id="phone"
                    name="phone"
                    value={formData.phone}
                    onChange={handleChange}
                    className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-primary" htmlFor="subject">
                  Objet de la demande *
                </label>
                <select
                  id="subject"
                  name="subject"
                  required
                  value={formData.subject}
                  onChange={handleChange}
                  className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                >
                  <option value="">Sélectionnez...</option>
                  <option value="devis">Demande de devis</option>
                  <option value="technique">Question technique</option>
                  <option value="commande">Suivi de commande</option>
                  <option value="autre">Autre demande</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-primary" htmlFor="message">
                  Message *
                </label>
                <textarea
                  id="message"
                  name="message"
                  required
                  rows={4}
                  value={formData.message}
                  onChange={handleChange}
                  className="w-full rounded-sm border border-light-gray px-3 py-2 text-sm outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
                />
              </div>

              <button
                type="submit"
                disabled={status === 'submitting'}
                className="inline-flex items-center gap-2 rounded-sm bg-accent px-6 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-white shadow-md hover:bg-accent-hover disabled:opacity-60"
              >
                {status === 'submitting' ? 'Transmission...' : 'Envoyer'}
                {status !== 'submitting' && <ArrowRight className="h-3.5 w-3.5" />}
              </button>

              {status === 'success' && (
                <p className="mt-2 flex items-center gap-2 text-xs text-emerald-600">
                  <Check className="h-3.5 w-3.5" />
                  Votre demande a bien été reçue. Nos équipes reviennent vers vous sous 24h.
                </p>
              )}
              {status === 'error' && (
                <p className="mt-2 flex items-center gap-2 text-xs text-red-600">
                  Une erreur est survenue lors de l'envoi. Merci de nous contacter par téléphone.
                </p>
              )}
            </form>

            {/* FAQ */}
            <section className="rounded-xl bg-white p-6 shadow-sm">
              <h3 className="mb-4 text-sm font-semibold text-primary">Questions fréquentes</h3>
              <div className="divide-y divide-light-gray">
                {FAQ_ITEMS.map((item, index) => {
                  const isOpen = openFaq === index;
                  return (
                    <div key={item.question}>
                      <button
                        type="button"
                        onClick={() => setOpenFaq(isOpen ? null : index)}
                        className="flex w-full items-center justify-between py-3 text-left text-xs font-semibold text-primary"
                      >
                        {item.question}
                        <ChevronDown
                          className={`h-4 w-4 transition-transform ${isOpen ? 'rotate-180' : ''}`}
                        />
                      </button>
                      {isOpen && (
                        <p className="pb-3 text-xs text-secondary">{item.answer}</p>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>
  );
};


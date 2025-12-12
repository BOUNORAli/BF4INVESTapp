import { Component, inject, signal, computed, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { StoreService } from '../../services/store.service';

interface SearchResult {
  id: string;
  type: 'bc' | 'invoice-sale' | 'invoice-purchase' | 'product' | 'client' | 'supplier';
  title: string;
  subtitle: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Résultats de recherche</h1>
          <p class="text-slate-500 text-sm mt-1">
            {{ resultsCount() }} résultat{{ resultsCount() > 1 ? 's' : '' }} pour "{{ query() }}"
          </p>
        </div>
      </div>

      @if (resultsCount() === 0) {
        <!-- Aucun résultat -->
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-12 text-center">
          <svg class="w-16 h-16 mx-auto mb-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
          </svg>
          <h3 class="text-lg font-semibold text-slate-800 mb-2">Aucun résultat trouvé</h3>
          <p class="text-sm text-slate-500 mb-6">Essayez avec d'autres mots-clés</p>
          <button (click)="router.navigate(['/dashboard'])" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition">
            Retour au Dashboard
          </button>
        </div>
      } @else {
        <!-- Résultats groupés par catégorie -->
        @for (category of groupedResults(); track category.type) {
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
            <!-- En-tête de catégorie -->
            <div class="px-6 py-4 bg-slate-50 border-b border-slate-200">
              <div class="flex items-center gap-3">
                <div [class]="getCategoryIconClass(category.type)">
                  <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="getCategoryIcon(category.type)"></path>
                  </svg>
                </div>
                <h2 class="text-lg font-bold text-slate-800">{{ getCategoryLabel(category.type) }}</h2>
                <span class="text-sm text-slate-500">({{ category.results.length }})</span>
              </div>
            </div>

            <!-- Liste des résultats -->
            <div class="divide-y divide-slate-100">
              @for (result of category.results; track result.id) {
                <div 
                  (click)="navigateToResult(result)"
                  class="px-6 py-4 hover:bg-slate-50 transition-colors cursor-pointer flex items-start gap-4 group"
                  [attr.data-item-id]="result.id">
                  <!-- Icône -->
                  <div class="mt-0.5 shrink-0">
                    <div [class]="getResultIconClass(result.type)">
                      <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="result.icon"></path>
                      </svg>
                    </div>
                  </div>

                  <!-- Contenu -->
                  <div class="flex-1 min-w-0">
                    <div class="font-semibold text-slate-800 text-base group-hover:text-blue-600 transition-colors mb-1" 
                         [innerHTML]="highlightText(result.title, query())"></div>
                    <div class="text-sm text-slate-500" 
                         [innerHTML]="highlightText(result.subtitle, query())"></div>
                  </div>

                  <!-- Flèche -->
                  <div class="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity pt-1">
                    <svg class="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                    </svg>
                  </div>
                </div>
              }
            </div>
          </div>
        }
      }
    </div>
  `
})
export class SearchComponent implements OnInit {
  store = inject(StoreService);
  router = inject(Router);
  route = inject(ActivatedRoute);
  sanitizer = inject(DomSanitizer);

  query = signal('');
  results = signal<SearchResult[]>([]);

  resultsCount = computed(() => this.results().length);

  groupedResults = computed(() => {
    const results = this.results();
    const grouped = new Map<string, SearchResult[]>();

    results.forEach(result => {
      if (!grouped.has(result.type)) {
        grouped.set(result.type, []);
      }
      grouped.get(result.type)!.push(result);
    });

    return Array.from(grouped.entries()).map(([type, results]) => ({
      type,
      results
    }));
  });

  ngOnInit() {
    // Récupérer le query parameter
    this.route.queryParams.subscribe(params => {
      const q = params['q'] || '';
      this.query.set(q);
      if (q) {
        this.performSearch(q);
      }
    });
  }

  performSearch(query: string) {
    const results: SearchResult[] = [];
    const lowerQuery = query.toLowerCase();

    // Recherche dans les BCs
    this.store.bcs().forEach(bc => {
      const clientName = this.store.getClientName(bc.clientId).toLowerCase();
      const supplierName = this.store.getSupplierName(bc.supplierId).toLowerCase();
      if (bc.number.toLowerCase().includes(lowerQuery) ||
          clientName.includes(lowerQuery) ||
          supplierName.includes(lowerQuery)) {
        results.push({
          id: bc.id,
          type: 'bc',
          title: bc.number,
          subtitle: `${this.store.getClientName(bc.clientId)} / ${this.store.getSupplierName(bc.supplierId)}`,
          route: `/bc/edit/${bc.id}`,
          icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z'
        });
      }
    });

    // Recherche dans les Factures Vente
    this.store.invoices()
      .filter(inv => inv.type === 'sale')
      .forEach(inv => {
        const clientName = this.store.getClientName(inv.partnerId || '').toLowerCase();
        if (inv.number.toLowerCase().includes(lowerQuery) ||
            clientName.includes(lowerQuery)) {
          results.push({
            id: inv.id,
            type: 'invoice-sale',
            title: inv.number,
            subtitle: `Client: ${this.store.getClientName(inv.partnerId || '')} - ${inv.amountTTC.toLocaleString('fr-FR')} MAD`,
            route: `/invoices/sales`,
            icon: 'M5 10l7-7m0 0l7 7m-7-7v18'
          });
        }
      });

    // Recherche dans les Factures Achat
    this.store.invoices()
      .filter(inv => inv.type === 'purchase')
      .forEach(inv => {
        const supplierName = this.store.getSupplierName(inv.partnerId || '').toLowerCase();
        if (inv.number.toLowerCase().includes(lowerQuery) ||
            supplierName.includes(lowerQuery)) {
          results.push({
            id: inv.id,
            type: 'invoice-purchase',
            title: inv.number,
            subtitle: `Fournisseur: ${this.store.getSupplierName(inv.partnerId || '')} - ${inv.amountTTC.toLocaleString('fr-FR')} MAD`,
            route: `/invoices/purchase`,
            icon: 'M19 14l-7 7m0 0l-7-7m7 7V3'
          });
        }
      });

    // Recherche dans les Produits
    this.store.products().forEach(product => {
      if (product.name.toLowerCase().includes(lowerQuery) ||
          product.ref.toLowerCase().includes(lowerQuery)) {
        results.push({
          id: product.id,
          type: 'product',
          title: product.name,
          subtitle: `Ref: ${product.ref} - ${product.priceSellHT.toLocaleString('fr-FR')} MAD HT`,
          route: `/products`,
          icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4'
        });
      }
    });

    // Recherche dans les Clients
    this.store.clients().forEach(client => {
      if (client.name.toLowerCase().includes(lowerQuery) ||
          client.ice.toLowerCase().includes(lowerQuery) ||
          (client.email && client.email.toLowerCase().includes(lowerQuery))) {
        results.push({
          id: client.id,
          type: 'client',
          title: client.name,
          subtitle: `ICE: ${client.ice}${client.email ? ` - ${client.email}` : ''}`,
          route: `/clients`,
          icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z'
        });
      }
    });

    // Recherche dans les Fournisseurs
    this.store.suppliers().forEach(supplier => {
      if (supplier.name.toLowerCase().includes(lowerQuery) ||
          supplier.ice.toLowerCase().includes(lowerQuery) ||
          (supplier.email && supplier.email.toLowerCase().includes(lowerQuery))) {
        results.push({
          id: supplier.id,
          type: 'supplier',
          title: supplier.name,
          subtitle: `ICE: ${supplier.ice}${supplier.email ? ` - ${supplier.email}` : ''}`,
          route: `/clients`,
          icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z'
        });
      }
    });

    this.results.set(results);
  }

  navigateToResult(result: SearchResult) {
    this.router.navigate([result.route]).then(() => {
      setTimeout(() => {
        const element = document.querySelector(`[data-item-id="${result.id}"]`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          element.classList.add('ring-2', 'ring-blue-500', 'ring-offset-2');
          setTimeout(() => {
            element.classList.remove('ring-2', 'ring-blue-500', 'ring-offset-2');
          }, 2000);
        }
      }, 300);
    });
  }

  highlightText(text: string, query: string): SafeHtml {
    if (!query) return this.sanitizer.bypassSecurityTrustHtml(text);
    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escapedQuery})`, 'gi');
    const highlighted = text.replace(regex, '<mark class="bg-yellow-200 text-yellow-900 px-0.5 rounded">$1</mark>');
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  getCategoryLabel(type: string): string {
    const labels: Record<string, string> = {
      'bc': 'Commandes (BC)',
      'invoice-sale': 'Factures Vente',
      'invoice-purchase': 'Factures Achat',
      'product': 'Produits',
      'client': 'Clients',
      'supplier': 'Fournisseurs'
    };
    return labels[type] || type;
  }

  getCategoryIcon(type: string): string {
    const icons: Record<string, string> = {
      'bc': 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
      'invoice-sale': 'M5 10l7-7m0 0l7 7m-7-7v18',
      'invoice-purchase': 'M19 14l-7 7m0 0l-7-7m7 7V3',
      'product': 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4',
      'client': 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z',
      'supplier': 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z'
    };
    return icons[type] || '';
  }

  getCategoryIconClass(type: string): string {
    const classes: Record<string, string> = {
      'bc': 'w-8 h-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center',
      'invoice-sale': 'w-8 h-8 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center',
      'invoice-purchase': 'w-8 h-8 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center',
      'product': 'w-8 h-8 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center',
      'client': 'w-8 h-8 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center',
      'supplier': 'w-8 h-8 rounded-lg bg-pink-100 text-pink-600 flex items-center justify-center'
    };
    return classes[type] || 'w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center';
  }

  getResultIconClass(type: string): string {
    const classes: Record<string, string> = {
      'bc': 'w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center',
      'invoice-sale': 'w-10 h-10 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center',
      'invoice-purchase': 'w-10 h-10 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center',
      'product': 'w-10 h-10 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center',
      'client': 'w-10 h-10 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center',
      'supplier': 'w-10 h-10 rounded-lg bg-pink-100 text-pink-600 flex items-center justify-center'
    };
    return classes[type] || 'w-10 h-10 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center';
  }
}


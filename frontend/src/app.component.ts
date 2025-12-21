
import { Component, inject, signal, effect, OnInit, OnDestroy, HostListener, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from './services/auth.service';
import { StoreService, BC, Invoice, Product, Client, Supplier } from './services/store.service';
import { filter } from 'rxjs/operators';

interface SearchResult {
  id: string;
  type: 'bc' | 'invoice-sale' | 'invoice-purchase' | 'product' | 'client' | 'supplier';
  title: string;
  subtitle: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule, FormsModule],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  store = inject(StoreService);
  router = inject(Router);
  sanitizer = inject(DomSanitizer);

  // État du menu mobile
  isMobileMenuOpen = signal(false);
  
  // État du panneau de notifications
  isNotificationsOpen = signal(false);

  // État de la recherche globale
  globalSearchTerm = signal('');
  searchResults = signal<SearchResult[]>([]);
  isSearchOpen = signal(false);
  isMobileSearchOpen = signal(false);
  selectedResultIndex = signal(-1);
  private searchDebounceTimer: any = null;

  constructor() {
    // Fermer le menu mobile et notifs lors d'un changement de page
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.isMobileMenuOpen.set(false);
      this.isNotificationsOpen.set(false);
      this.isSearchOpen.set(false);
      this.isMobileSearchOpen.set(false);
      this.globalSearchTerm.set('');
    });

    // Focus automatique sur l'input de recherche mobile quand le modal s'ouvre
    effect(() => {
      if (this.isMobileSearchOpen()) {
        setTimeout(() => {
          const input = document.querySelector('.mobile-search-input') as HTMLInputElement;
          if (input) {
            input.focus();
          }
        }, 100);
      }
    });
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen.update(v => !v);
  }

  closeMobileMenu() {
    this.isMobileMenuOpen.set(false);
  }

  toggleNotifications() {
    this.isNotificationsOpen.update(v => !v);
  }

  closeNotifications() {
    this.isNotificationsOpen.set(false);
  }

  viewNotificationHistory() {
    this.closeNotifications();
    this.router.navigate(['/notifications']);
  }

  async markAsRead(notificationId: string) {
    await this.store.markNotificationAsRead(notificationId);
  }

  async refreshAllData() {
    await this.store.refreshAllData();
  }

  private reminderCheckInterval: any = null;

  async ngOnInit() {
    // Load notifications on app start
    await this.store.loadNotifications(false);
    
    // Poll for new notifications every 30 seconds
    setInterval(() => {
      this.store.loadNotifications(false);
    }, 30000);

    // Vérifier les rappels au démarrage
    await this.store.checkPaymentReminders();
    
    // Vérifier les rappels toutes les heures
    this.reminderCheckInterval = setInterval(() => {
      this.store.checkPaymentReminders();
    }, 3600000); // 1 heure = 3600000 ms
  }

  ngOnDestroy() {
    if (this.reminderCheckInterval) {
      clearInterval(this.reminderCheckInterval);
    }
  }

  // Recherche globale
  onSearchInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const query = input.value.trim();
    this.globalSearchTerm.set(query);

    // Debounce la recherche (300ms)
    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
    }

    if (query.length === 0) {
      this.searchResults.set([]);
      this.isSearchOpen.set(false);
      return;
    }

    this.searchDebounceTimer = setTimeout(() => {
      this.performGlobalSearch(query);
    }, 300);
  }

  performGlobalSearch(query: string) {
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

    // Limiter à 50 résultats maximum, groupés par type
    const limitedResults = this.limitResultsByCategory(results, 10);
    this.searchResults.set(limitedResults);
    this.isSearchOpen.set(limitedResults.length > 0);
    this.selectedResultIndex.set(-1);
  }

  private limitResultsByCategory(results: SearchResult[], maxPerCategory: number): SearchResult[] {
    const categoryCounts: Record<string, number> = {};
    const limited: SearchResult[] = [];

    for (const result of results) {
      const category = result.type;
      const count = categoryCounts[category] || 0;
      
      if (count < maxPerCategory && limited.length < 50) {
        limited.push(result);
        categoryCounts[category] = count + 1;
      }
    }

    return limited;
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

  highlightText(text: string, query: string): SafeHtml {
    if (!query) return this.sanitizer.bypassSecurityTrustHtml(text);
    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escapedQuery})`, 'gi');
    const highlighted = text.replace(regex, '<mark class="bg-yellow-200 text-yellow-900 px-0.5 rounded">$1</mark>');
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  navigateToResult(result: SearchResult) {
    this.router.navigate([result.route]).then(() => {
      this.isSearchOpen.set(false);
      this.globalSearchTerm.set('');
      this.searchResults.set([]);
      
      // Scroll vers l'élément si possible (après un court délai pour laisser le DOM se charger)
      setTimeout(() => {
        const element = document.querySelector(`[data-item-id="${result.id}"]`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          // Highlight temporaire
          element.classList.add('ring-2', 'ring-blue-500', 'ring-offset-2');
          setTimeout(() => {
            element.classList.remove('ring-2', 'ring-blue-500', 'ring-offset-2');
          }, 2000);
        }
      }, 300);
    });
  }

  onSearchKeyDown(event: KeyboardEvent) {
    const results = this.searchResults();
    const query = this.globalSearchTerm().trim();

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (results.length > 0) {
        this.selectedResultIndex.update(idx => {
          const newIdx = idx < results.length - 1 ? idx + 1 : idx;
          // Scroll automatique vers l'élément sélectionné
          setTimeout(() => this.scrollToSelectedResult(newIdx), 50);
          return newIdx;
        });
      }
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (results.length > 0) {
        this.selectedResultIndex.update(idx => {
          const newIdx = idx > 0 ? idx - 1 : -1;
          // Scroll automatique vers l'élément sélectionné
          if (newIdx >= 0) {
            setTimeout(() => this.scrollToSelectedResult(newIdx), 50);
          }
          return newIdx;
        });
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const selectedIdx = this.selectedResultIndex();
      // Si un élément est explicitement sélectionné, naviguer vers lui
      if (selectedIdx >= 0 && selectedIdx < results.length) {
        this.navigateToResult(results[selectedIdx]);
      } 
      // Sinon, si on a une recherche et des résultats, rediriger vers la page de recherche
      else if (query && results.length > 0) {
        this.router.navigate(['/search'], { queryParams: { q: query } });
        this.isSearchOpen.set(false);
      }
      // Si on a une recherche mais aucun résultat, rediriger quand même vers la page de recherche
      else if (query) {
        this.router.navigate(['/search'], { queryParams: { q: query } });
        this.isSearchOpen.set(false);
      }
    } else if (event.key === 'Escape') {
      this.isSearchOpen.set(false);
      this.globalSearchTerm.set('');
    }
  }

  scrollToSelectedResult(index: number) {
    // Utiliser un timeout pour s'assurer que le DOM est mis à jour après le changement d'index
    setTimeout(() => {
      const scrollContainer = document.querySelector('.search-results-container');
      if (!scrollContainer) return;

      // Récupérer tous les éléments de résultats
      const resultItems = Array.from(scrollContainer.querySelectorAll('[data-item-id]')) as HTMLElement[];
      if (index >= 0 && index < resultItems.length) {
        const selectedItem = resultItems[index];
        
        // Utiliser scrollIntoView pour scroller l'élément dans la vue
        // avec block: 'nearest' pour ne scroller que si nécessaire
        selectedItem.scrollIntoView({ 
          behavior: 'smooth', 
          block: 'nearest',
          inline: 'nearest'
        });
      }
    }, 50);
  }

  closeSearch() {
    this.isSearchOpen.set(false);
  }

  closeMobileSearch() {
    this.isMobileSearchOpen.set(false);
    this.globalSearchTerm.set('');
    this.searchResults.set([]);
    this.selectedResultIndex.set(-1);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.global-search-container')) {
      this.isSearchOpen.set(false);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onGlobalKeyDown(event: KeyboardEvent) {
    // Ctrl+K ou Cmd+K pour focuser la recherche
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      const searchInput = document.querySelector('.global-search-input') as HTMLInputElement;
      if (searchInput) {
        searchInput.focus();
        searchInput.select();
      }
    }
  }

  getGroupedResults(): { type: string; results: SearchResult[] }[] {
    const results = this.searchResults();
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
  }

  getResultIndex(categoryType: string, categoryIndex: number): number {
    const grouped = this.getGroupedResults();
    let index = 0;
    
    for (const category of grouped) {
      if (category.type === categoryType) {
        return index + categoryIndex;
      }
      index += category.results.length;
    }
    
    return -1;
  }

  getResultIconClass(type: string): string {
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
}

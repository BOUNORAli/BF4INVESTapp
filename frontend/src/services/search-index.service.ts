import { Injectable, signal, computed } from '@angular/core';
import type { BC, Invoice, Product, Client, Supplier } from './store.service';

export interface SearchIndexEntry {
  id: string;
  type: 'bc' | 'invoice-sale' | 'invoice-purchase' | 'product' | 'client' | 'supplier';
  title: string;
  subtitle: string;
  route: string;
  keywords: string[]; // Mots-clés indexés pour recherche rapide
}

/**
 * Service d'indexation pour recherche ultra-rapide
 * Utilise un index inversé en mémoire pour recherche O(1)
 */
@Injectable({
  providedIn: 'root'
})
export class SearchIndexService {
  // Index inversé: mot-clé -> Set d'IDs
  private invertedIndex = new Map<string, Set<string>>();
  
  // Map d'ID -> Entry complète
  private entries = new Map<string, SearchIndexEntry>();
  
  // Index par type pour recherche catégorisée
  private entriesByType = new Map<string, Set<string>>();

  /**
   * Indexe un élément pour la recherche
   */
  indexEntry(entry: SearchIndexEntry): void {
    if (!entry || !entry.id || !entry.type) {
      console.warn('Tentative d\'indexation d\'une entrée invalide:', entry);
      return;
    }
    
    try {
      // Supprimer l'ancienne entrée si elle existe
      this.removeEntry(entry.id);

      // Ajouter l'entrée
      this.entries.set(entry.id, entry);

      // Indexer par type
      if (!this.entriesByType.has(entry.type)) {
        this.entriesByType.set(entry.type, new Set());
      }
      this.entriesByType.get(entry.type)!.add(entry.id);

      // Indexer tous les mots-clés (filtrer les mots-clés vides)
      if (entry.keywords && entry.keywords.length > 0) {
        entry.keywords.forEach(keyword => {
          if (keyword && keyword.trim().length > 0) {
            const normalized = this.normalizeKeyword(keyword);
            if (normalized.length > 0) {
              if (!this.invertedIndex.has(normalized)) {
                this.invertedIndex.set(normalized, new Set());
              }
              this.invertedIndex.get(normalized)!.add(entry.id);
            }
          }
        });
      }
    } catch (error) {
      console.warn('Erreur lors de l\'indexation d\'une entrée:', entry.id, error);
    }
  }

  /**
   * Supprime une entrée de l'index
   */
  removeEntry(id: string): void {
    const entry = this.entries.get(id);
    if (!entry) return;

    // Retirer de l'index inversé
    entry.keywords.forEach(keyword => {
      const normalized = this.normalizeKeyword(keyword);
      const ids = this.invertedIndex.get(normalized);
      if (ids) {
        ids.delete(id);
        if (ids.size === 0) {
          this.invertedIndex.delete(normalized);
        }
      }
    });

    // Retirer du type
    const typeSet = this.entriesByType.get(entry.type);
    if (typeSet) {
      typeSet.delete(id);
    }

    // Retirer de la map principale
    this.entries.delete(id);
  }

  /**
   * Recherche dans l'index
   */
  search(query: string, maxResults: number = 50, maxPerCategory: number = 10): SearchIndexEntry[] {
    if (!query || query.trim().length === 0) {
      return [];
    }

    const normalizedQuery = this.normalizeKeyword(query);
    const queryWords = normalizedQuery.split(/\s+/).filter(w => w.length > 0);

    if (queryWords.length === 0) {
      return [];
    }

    // Trouver tous les IDs correspondants
    const matchingIds = new Map<string, number>(); // ID -> score de correspondance

    queryWords.forEach(word => {
      // Recherche exacte
      const exactMatch = this.invertedIndex.get(word);
      if (exactMatch) {
        exactMatch.forEach(id => {
          matchingIds.set(id, (matchingIds.get(id) || 0) + 10);
        });
      }

      // Recherche partielle (préfixe)
      for (const [keyword, ids] of this.invertedIndex.entries()) {
        if (keyword.startsWith(word) || word.startsWith(keyword)) {
          ids.forEach(id => {
            matchingIds.set(id, (matchingIds.get(id) || 0) + 5);
          });
        }
      }
    });

    // Convertir en entrées et trier par score
    const results: Array<{ entry: SearchIndexEntry; score: number }> = [];
    for (const [id, score] of matchingIds.entries()) {
      const entry = this.entries.get(id);
      if (entry) {
        results.push({ entry, score });
      }
    }

    // Trier par score décroissant
    results.sort((a, b) => b.score - a.score);

    // Limiter par catégorie
    return this.limitResultsByCategory(
      results.map(r => r.entry),
      maxPerCategory,
      maxResults
    );
  }

  /**
   * Limite les résultats par catégorie
   */
  private limitResultsByCategory(
    results: SearchIndexEntry[],
    maxPerCategory: number,
    maxTotal: number
  ): SearchIndexEntry[] {
    const categoryCounts = new Map<string, number>();
    const limited: SearchIndexEntry[] = [];

    for (const result of results) {
      if (limited.length >= maxTotal) break;

      const count = categoryCounts.get(result.type) || 0;
      if (count < maxPerCategory) {
        limited.push(result);
        categoryCounts.set(result.type, count + 1);
      }
    }

    return limited;
  }

  /**
   * Normalise un mot-clé pour l'indexation
   */
  private normalizeKeyword(keyword: string): string {
    return keyword
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Supprimer les accents
      .trim();
  }

  /**
   * Indexe tous les BCs
   */
  indexBCs(bcs: BC[], getClientName: (id: string) => string, getSupplierName: (id: string) => string): void {
    if (!bcs || bcs.length === 0) return;
    
    bcs.forEach(bc => {
      try {
        const clientName = bc.clientId ? getClientName(bc.clientId) : 'Inconnu';
        const supplierName = bc.supplierId ? getSupplierName(bc.supplierId) : 'Inconnu';
        
        this.indexEntry({
          id: bc.id,
          type: 'bc',
          title: bc.number || 'BC',
          subtitle: `${clientName} / ${supplierName}`,
          route: `/bc/edit/${bc.id}`,
          keywords: [
            bc.number || '',
            clientName,
            supplierName,
            bc.date || '',
            ...(bc.lieuLivraison ? [bc.lieuLivraison] : []),
            ...(bc.conditionLivraison ? [bc.conditionLivraison] : [])
          ].filter(k => k && k.length > 0)
        });
      } catch (error) {
        console.warn('Erreur lors de l\'indexation d\'un BC:', bc.id, error);
      }
    });
  }

  /**
   * Indexe toutes les factures
   */
  indexInvoices(
    invoices: Invoice[],
    getClientName: (id: string) => string,
    getSupplierName: (id: string) => string
  ): void {
    if (!invoices || invoices.length === 0) return;
    
    invoices.forEach(inv => {
      try {
        const partnerName = inv.type === 'sale' 
          ? (inv.partnerId ? getClientName(inv.partnerId) : 'Inconnu')
          : (inv.partnerId ? getSupplierName(inv.partnerId) : 'Inconnu');
        
        this.indexEntry({
          id: inv.id,
          type: inv.type === 'sale' ? 'invoice-sale' : 'invoice-purchase',
          title: inv.number || 'Facture',
          subtitle: inv.type === 'sale'
            ? `Client: ${partnerName} - ${(inv.amountTTC || 0).toLocaleString('fr-FR')} MAD`
            : `Fournisseur: ${partnerName} - ${(inv.amountTTC || 0).toLocaleString('fr-FR')} MAD`,
          route: inv.type === 'sale' ? '/invoices/sales' : '/invoices/purchase',
          keywords: [
            inv.number || '',
            partnerName,
            inv.date || '',
            inv.dueDate || '',
            (inv.amountTTC || 0).toString(),
            (inv.amountHT || 0).toString(),
            ...(inv.bcReference ? [inv.bcReference] : []),
            ...(inv.paymentMode ? [inv.paymentMode] : [])
          ].filter(k => k && k.length > 0)
        });
      } catch (error) {
        console.warn('Erreur lors de l\'indexation d\'une facture:', inv.id, error);
      }
    });
  }

  /**
   * Indexe tous les produits
   */
  indexProducts(products: Product[]): void {
    if (!products || products.length === 0) return;
    
    products.forEach(product => {
      try {
        this.indexEntry({
          id: product.id,
          type: 'product',
          title: product.name || 'Produit',
          subtitle: `Ref: ${product.ref || 'N/A'} - ${(product.priceSellHT || 0).toLocaleString('fr-FR')} MAD HT`,
          route: '/products',
          keywords: [
            product.name || '',
            product.ref || '',
            product.unit || '',
            (product.priceSellHT || 0).toString(),
            (product.priceBuyHT || 0).toString(),
            ...(product.stock !== undefined ? [product.stock.toString()] : [])
          ].filter(k => k && k.length > 0)
        });
      } catch (error) {
        console.warn('Erreur lors de l\'indexation d\'un produit:', product.id, error);
      }
    });
  }

  /**
   * Indexe tous les clients
   */
  indexClients(clients: Client[]): void {
    if (!clients || clients.length === 0) return;
    
    clients.forEach(client => {
      try {
        this.indexEntry({
          id: client.id,
          type: 'client',
          title: client.name || 'Client',
          subtitle: `ICE: ${client.ice || 'N/A'}${client.email ? ` - ${client.email}` : ''}`,
          route: '/clients',
          keywords: [
            client.name || '',
            client.ice || '',
            ...(client.email ? [client.email] : []),
            ...(client.phone ? [client.phone] : []),
            ...(client.address ? [client.address] : []),
            ...(client.referenceClient ? [client.referenceClient] : [])
          ].filter(k => k && k.length > 0)
        });
      } catch (error) {
        console.warn('Erreur lors de l\'indexation d\'un client:', client.id, error);
      }
    });
  }

  /**
   * Indexe tous les fournisseurs
   */
  indexSuppliers(suppliers: Supplier[]): void {
    if (!suppliers || suppliers.length === 0) return;
    
    suppliers.forEach(supplier => {
      try {
        this.indexEntry({
          id: supplier.id,
          type: 'supplier',
          title: supplier.name || 'Fournisseur',
          subtitle: `ICE: ${supplier.ice || 'N/A'}${supplier.email ? ` - ${supplier.email}` : ''}`,
          route: '/clients', // Route vers la page partenaires (clients et fournisseurs)
          keywords: [
            supplier.name || '',
            supplier.ice || '',
            ...(supplier.email ? [supplier.email] : []),
            ...(supplier.phone ? [supplier.phone] : []),
            ...(supplier.address ? [supplier.address] : []),
            ...(supplier.referenceFournisseur ? [supplier.referenceFournisseur] : [])
          ].filter(k => k && k.length > 0)
        });
      } catch (error) {
        console.warn('Erreur lors de l\'indexation d\'un fournisseur:', supplier.id, error);
      }
    });
  }

  /**
   * Vide tout l'index
   */
  clear(): void {
    this.invertedIndex.clear();
    this.entries.clear();
    this.entriesByType.clear();
  }

  /**
   * Obtient les statistiques de l'index
   */
  getStats(): { totalEntries: number; totalKeywords: number; entriesByType: Record<string, number> } {
    const entriesByType: Record<string, number> = {};
    for (const [type, set] of this.entriesByType.entries()) {
      entriesByType[type] = set.size;
    }

    return {
      totalEntries: this.entries.size,
      totalKeywords: this.invertedIndex.size,
      entriesByType
    };
  }
}


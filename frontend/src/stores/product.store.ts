import { Injectable, inject, signal } from '@angular/core';
import { ProductService } from '../services/product.service';
import type { Product } from '../services/store.service';

/**
 * Store spécialisé pour la gestion des produits
 * Centralise l'état et les opérations liées aux produits
 */
@Injectable({
  providedIn: 'root'
})
export class ProductStore {
  private productService = inject(ProductService);

  // État
  readonly products = signal<Product[]>([]);
  readonly loading = signal<boolean>(false);

  // Computed
  readonly productsCount = () => this.products().length;

  /**
   * Charge tous les produits depuis l'API
   */
  async loadProducts(): Promise<void> {
    try {
      this.loading.set(true);
      const products = await this.productService.getProducts();
      this.products.set(products);
    } catch (error) {
      console.error('Error loading products:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Ajoute ou met à jour un produit dans le store
   */
  upsertProduct(product: Product): void {
    this.products.update(list => {
      const index = list.findIndex(p => p.id === product.id);
      if (index >= 0) {
        const updated = [...list];
        updated[index] = product;
        return updated;
      }
      return [...list, product];
    });
  }

  /**
   * Supprime un produit du store
   */
  removeProduct(productId: string): void {
    this.products.update(list => list.filter(p => p.id !== productId));
  }

  /**
   * Trouve un produit par ID
   */
  findProductById(id: string): Product | undefined {
    return this.products().find(p => p.id === id);
  }

  /**
   * Trouve un produit par référence
   */
  findProductByRef(ref: string): Product | undefined {
    return this.products().find(p => p.ref === ref);
  }
}


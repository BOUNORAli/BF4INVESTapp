import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { StoreService } from '../../services/store.service';

interface AuditLog {
  id: string;
  userId: string;
  userName: string;
  action: string; // CREATE, UPDATE, DELETE
  entityType: string; // Client, Supplier, BandeCommande, etc.
  entityId: string;
  oldValue: any;
  newValue: any;
  ipAddress: string;
  userAgent: string;
  timestamp: string;
}

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="p-4 md:p-6 lg:p-8">
      <!-- Header -->
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-slate-800">Journal d'Activité</h1>
        <p class="text-slate-500 mt-1">Historique de toutes les actions effectuées dans l'application</p>
      </div>

      <!-- Filtres -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-100 p-4 mb-6">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <!-- Filtre par utilisateur -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Utilisateur</label>
            <select [(ngModel)]="filterUser" (change)="applyFilters()" 
              class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
              <option value="">Tous les utilisateurs</option>
              @for (user of uniqueUsers(); track user) {
                <option [value]="user">{{ user }}</option>
              }
            </select>
          </div>

          <!-- Filtre par type d'entité -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Type</label>
            <select [(ngModel)]="filterEntityType" (change)="applyFilters()" 
              class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
              <option value="">Tous les types</option>
              <option value="BandeCommande">Bons de Commande</option>
              <option value="FactureVente">Factures Vente</option>
              <option value="FactureAchat">Factures Achat</option>
              <option value="Client">Clients</option>
              <option value="Fournisseur">Fournisseurs</option>
              <option value="PrevisionPaiement">Prévisions de Paiement</option>
              <option value="Paiement">Paiements</option>
              <option value="Produit">Produits</option>
              <option value="Charge">Charges</option>
            </select>
          </div>

          <!-- Filtre par action -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Action</label>
            <select [(ngModel)]="filterAction" (change)="applyFilters()" 
              class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
              <option value="">Toutes les actions</option>
              <option value="CREATE">Création</option>
              <option value="UPDATE">Modification</option>
              <option value="DELETE">Suppression</option>
            </select>
          </div>

          <!-- Filtre par date -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Date</label>
            <input type="date" [(ngModel)]="filterDate" (change)="applyFilters()"
              class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
          </div>
        </div>

        <!-- Filtre par Document -->
        <div class="mt-4 pt-4 border-t border-slate-100">
          <h3 class="text-sm font-semibold text-slate-700 mb-3">Filtrer par Document</h3>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <!-- Type de document -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1">Type de document</label>
              <select [(ngModel)]="filterDocumentType" (change)="onDocumentTypeChange()" 
                class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
                <option value="">Sélectionner un type</option>
                <option value="BandeCommande">Bon de Commande</option>
                <option value="FactureVente">Facture Vente</option>
                <option value="FactureAchat">Facture Achat</option>
              </select>
            </div>

            <!-- Numéro du document -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1">Numéro</label>
              <input type="text" [(ngModel)]="filterDocumentNumber" 
                [attr.list]="filterDocumentType ? 'document-numbers-' + filterDocumentType : null"
                (input)="onDocumentNumberChange()"
                placeholder="Saisir ou sélectionner un numéro"
                class="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500">
              @if (filterDocumentType) {
                <datalist [id]="'document-numbers-' + filterDocumentType">
                  @for (number of getDocumentNumbers(); track number) {
                    <option [value]="number">{{ number }}</option>
                  }
                </datalist>
              }
            </div>

            <!-- Bouton Effacer filtre document -->
            <div class="flex items-end">
              <button (click)="clearDocumentFilter()" 
                class="w-full px-4 py-2 bg-slate-100 text-slate-600 rounded-lg text-sm font-medium hover:bg-slate-200 transition-colors">
                Effacer
              </button>
            </div>
          </div>
        </div>

        <!-- Boutons d'action -->
        <div class="flex items-center gap-3 mt-4 pt-4 border-t border-slate-100">
          <button (click)="loadAuditLogs()" 
            class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors flex items-center gap-2"
            [disabled]="loading()">
            <svg class="w-4 h-4" [class.animate-spin]="loading()" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
            Actualiser
          </button>
          <button (click)="clearFilters()" 
            class="px-4 py-2 bg-slate-100 text-slate-600 rounded-lg text-sm font-medium hover:bg-slate-200 transition-colors">
            Effacer les filtres
          </button>
          <span class="ml-auto text-sm text-slate-500">
            {{ filteredLogs().length }} entrée(s)
          </span>
        </div>
      </div>

      <!-- Liste des logs -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
        @if (loading()) {
          <div class="p-8 text-center">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-600 border-r-transparent"></div>
            <p class="mt-3 text-slate-500">Chargement...</p>
          </div>
        } @else if (filteredLogs().length === 0) {
          <div class="p-8 text-center">
            <svg class="w-16 h-16 mx-auto text-slate-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
            <p class="text-slate-500">Aucune activité enregistrée</p>
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead class="bg-slate-50 border-b border-slate-100">
                <tr>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Date/Heure</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Utilisateur</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Action</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Type</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Détails</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (log of filteredLogs(); track log.id) {
                  <tr class="hover:bg-slate-50/50 transition-colors cursor-pointer" (click)="selectLog(log)">
                    <td class="px-4 py-3 whitespace-nowrap">
                      <div class="text-sm font-medium text-slate-800">{{ formatDate(log.timestamp) }}</div>
                      <div class="text-xs text-slate-500">{{ formatTime(log.timestamp) }}</div>
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex items-center gap-2">
                        <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold">
                          {{ getInitials(log.userName || log.userId) }}
                        </div>
                        <span class="text-sm text-slate-700">{{ log.userName || log.userId }}</span>
                      </div>
                    </td>
                    <td class="px-4 py-3">
                      <span [class]="getActionBadgeClass(log.action)" class="px-2 py-1 rounded-full text-xs font-semibold">
                        {{ getActionLabel(log.action) }}
                      </span>
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex items-center gap-2">
                        <span [class]="getEntityIconClass(log.entityType)" class="w-6 h-6 rounded flex items-center justify-center">
                          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="getEntityIcon(log.entityType)"></path>
                          </svg>
                        </span>
                        <span class="text-sm text-slate-700">{{ getEntityLabel(log.entityType) }}</span>
                      </div>
                    </td>
                    <td class="px-4 py-3">
                      <div class="text-sm text-slate-600 max-w-xs truncate" [title]="getDetails(log)">
                        {{ getDetails(log) }}
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <!-- Modal Détails -->
      @if (selectedLog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center" aria-modal="true">
          <div (click)="closeDetails()" class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col">
            <div class="flex items-center justify-between p-6 border-b border-slate-100 bg-gradient-to-r from-blue-50 to-indigo-50">
              <div>
                <h2 class="text-xl font-bold text-slate-800">Détails de l'Activité</h2>
                <p class="text-sm text-slate-600 mt-1">{{ getEntityLabel(selectedLog()!.entityType) }} - {{ getActionLabel(selectedLog()!.action) }}</p>
              </div>
              <button (click)="closeDetails()" class="text-slate-400 hover:text-slate-600 transition">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <!-- Onglets -->
            @if (selectedLog(); as log) {
              <div class="border-b border-slate-200 bg-slate-50/50">
                <div class="flex gap-1 px-6">
                  <button (click)="activeDetailsTab.set('resume')" 
                    [class]="activeDetailsTab() === 'resume' ? 'border-b-2 border-blue-600 text-blue-600 font-semibold' : 'text-slate-600 hover:text-slate-800'"
                    class="px-4 py-3 text-sm transition-colors">
                    Résumé
                  </button>
                  @if (log.action === 'UPDATE' && log.oldValue) {
                    <button (click)="activeDetailsTab.set('before')" 
                      [class]="activeDetailsTab() === 'before' ? 'border-b-2 border-amber-600 text-amber-600 font-semibold' : 'text-slate-600 hover:text-slate-800'"
                      class="px-4 py-3 text-sm transition-colors">
                      Avant
                    </button>
                  }
                  @if (log.newValue || (log.action === 'DELETE' && log.oldValue)) {
                    <button (click)="activeDetailsTab.set('after')" 
                      [class]="activeDetailsTab() === 'after' ? 'border-b-2 border-emerald-600 text-emerald-600 font-semibold' : 'text-slate-600 hover:text-slate-800'"
                      class="px-4 py-3 text-sm transition-colors">
                      {{ log.action === 'UPDATE' ? 'Après' : 'Détails' }}
                    </button>
                  }
                </div>
              </div>
            }
            
            <div class="flex-1 overflow-y-auto p-6">
              @if (selectedLog(); as log) {
                <!-- Onglet Résumé -->
                @if (activeDetailsTab() === 'resume') {
                  <div class="space-y-6">
                    <!-- Informations générales -->
                    <div class="bg-slate-50 p-4 rounded-lg">
                      <h3 class="font-semibold text-slate-700 mb-3">Informations Générales</h3>
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                        <div><span class="text-slate-500">Date/Heure:</span> <span class="font-medium ml-2">{{ formatDate(log.timestamp) }} à {{ formatTime(log.timestamp) }}</span></div>
                        <div><span class="text-slate-500">Utilisateur:</span> <span class="font-medium ml-2">{{ log.userName || log.userId }}</span></div>
                        <div><span class="text-slate-500">Action:</span> <span [class]="getActionBadgeClass(log.action)" class="px-2 py-1 rounded-full text-xs font-semibold ml-2">{{ getActionLabel(log.action) }}</span></div>
                        <div><span class="text-slate-500">Type d'entité:</span> <span class="font-medium ml-2">{{ getEntityLabel(log.entityType) }}</span></div>
                      </div>
                    </div>

                    <!-- Ce qui s'est passé -->
                    <div class="bg-blue-50 p-4 rounded-lg border border-blue-200">
                      <h3 class="font-semibold text-blue-700 mb-3">Ce qui s'est passé</h3>
                      <p class="text-sm text-blue-800">{{ getActionSummary(log) }}</p>
                    </div>

                    <!-- Champs modifiés (pour UPDATE) -->
                    @if (log.action === 'UPDATE' && log.oldValue && log.newValue) {
                      <div class="bg-amber-50 p-4 rounded-lg border border-amber-200">
                        <h3 class="font-semibold text-amber-700 mb-3">Champs Modifiés</h3>
                        @if (getChangedFields(log); as changedFields) {
                          @if (changedFields.length > 0) {
                            <div class="space-y-2">
                              @for (field of changedFields; track field.key) {
                                <div class="bg-white p-3 rounded border border-amber-200">
                                  <div class="font-medium text-sm text-amber-900 mb-1">{{ field.label }}</div>
                                  <div class="text-xs text-slate-600">
                                    <span class="line-through text-red-600">{{ field.oldValue }}</span>
                                    <span class="mx-2">→</span>
                                    <span class="text-emerald-600 font-medium">{{ field.newValue }}</span>
                                  </div>
                                </div>
                              }
                            </div>
                          } @else {
                            <p class="text-sm text-amber-700">Aucun champ identifiable modifié</p>
                          }
                        }
                      </div>
                    }
                  </div>
                }

                <!-- Onglet Avant -->
                @if (activeDetailsTab() === 'before' && log.oldValue) {
                  <div class="space-y-4">
                    <div class="flex items-center justify-between mb-4">
                      <h3 class="font-semibold text-amber-700">Ancienne Valeur</h3>
                      <button (click)="copyToClipboard(formatValue(log.oldValue))" 
                        class="px-3 py-1.5 bg-amber-100 text-amber-700 rounded-lg text-sm font-medium hover:bg-amber-200 transition flex items-center gap-2">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
                        </svg>
                        Copier
                      </button>
                    </div>
                    <div class="bg-amber-50 p-4 rounded-lg border border-amber-200">
                      <div class="text-sm text-amber-800 whitespace-pre-wrap break-words font-mono bg-white p-4 rounded border border-amber-200 max-h-[60vh] overflow-y-auto">
                        {{ formatValue(log.oldValue) }}
                      </div>
                    </div>
                  </div>
                }

                <!-- Onglet Après / Détails -->
                @if (activeDetailsTab() === 'after') {
                  <div class="space-y-4">
                    <div class="flex items-center justify-between mb-4">
                      <h3 class="font-semibold text-emerald-700">{{ log.action === 'UPDATE' ? 'Nouvelle Valeur' : (log.action === 'DELETE' ? 'Valeur Supprimée' : 'Détails') }}</h3>
                      @if (log.newValue || (log.action === 'DELETE' && log.oldValue)) {
                        <button (click)="copyToClipboard(formatValue(log.newValue || log.oldValue))" 
                          class="px-3 py-1.5 bg-emerald-100 text-emerald-700 rounded-lg text-sm font-medium hover:bg-emerald-200 transition flex items-center gap-2">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
                          </svg>
                          Copier
                        </button>
                      }
                    </div>
                    <div class="bg-emerald-50 p-4 rounded-lg border border-emerald-200">
                      <div class="text-sm text-emerald-800 whitespace-pre-wrap break-words font-mono bg-white p-4 rounded border border-emerald-200 max-h-[60vh] overflow-y-auto">
                        {{ formatValue(log.newValue || (log.action === 'DELETE' ? log.oldValue : null)) }}
                      </div>
                    </div>
                  </div>
                }
              }
            </div>

            <div class="p-6 border-t border-slate-100 bg-slate-50/50">
              <button (click)="closeDetails()" class="w-full px-4 py-2 bg-slate-200 text-slate-700 font-medium rounded-lg hover:bg-slate-300 transition">
                Fermer
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class AuditComponent implements OnInit {
  private api = inject(ApiService);
  store = inject(StoreService);
  route = inject(ActivatedRoute);
  router = inject(Router);

  auditLogs = signal<AuditLog[]>([]);
  loading = signal(false);
  selectedLog = signal<AuditLog | null>(null);
  activeDetailsTab = signal<'resume' | 'before' | 'after'>('resume');
  
  filterUser = '';
  filterEntityType = '';
  filterAction = '';
  filterDate = '';
  filterDocumentType = '';
  filterDocumentNumber = '';
  filterEntityId = ''; // ID interne du document sélectionné

  uniqueUsers = computed(() => {
    const users = new Set<string>();
    this.auditLogs().forEach(log => {
      if (log.userName) users.add(log.userName);
      else if (log.userId) users.add(log.userId);
    });
    return Array.from(users).sort();
  });

  filteredLogs = computed(() => {
    let logs = this.auditLogs();
    
    if (this.filterUser) {
      logs = logs.filter(l => (l.userName || l.userId) === this.filterUser);
    }
    if (this.filterEntityType) {
      logs = logs.filter(l => l.entityType === this.filterEntityType);
    }
    if (this.filterAction) {
      logs = logs.filter(l => l.action === this.filterAction);
    }
    if (this.filterDate) {
      logs = logs.filter(l => {
        if (!l.timestamp) return false;
        const logDate = new Date(l.timestamp).toISOString().split('T')[0];
        return logDate === this.filterDate;
      });
    }
    if (this.filterEntityId) {
      logs = logs.filter(l => l.entityId === this.filterEntityId);
    }
    
    // Trier par date décroissante
    return logs.sort((a, b) => {
      const dateA = new Date(a.timestamp || 0).getTime();
      const dateB = new Date(b.timestamp || 0).getTime();
      return dateB - dateA;
    });
  });

  // Liste des numéros de documents disponibles selon le type
  getDocumentNumbers(): string[] {
    if (!this.filterDocumentType) return [];
    
    if (this.filterDocumentType === 'BandeCommande') {
      return this.store.bcs().map(bc => bc.number || bc.id).filter(Boolean);
    } else if (this.filterDocumentType === 'FactureVente') {
      return this.store.invoices().filter(inv => inv.type === 'sale').map(inv => inv.number).filter(Boolean);
    } else if (this.filterDocumentType === 'FactureAchat') {
      return this.store.invoices().filter(inv => inv.type === 'purchase').map(inv => inv.number).filter(Boolean);
    }
    return [];
  }

  ngOnInit() {
    // Vérifier les query params pour un filtre direct
    this.route.queryParams.subscribe(params => {
      if (params['entityType'] && params['entityId']) {
        this.filterDocumentType = params['entityType'];
        this.filterEntityId = params['entityId'];
        // Trouver le numéro du document
        this.resolveDocumentNumber();
        this.loadAuditLogs(params['entityType'], params['entityId']);
      } else {
        this.loadAuditLogs();
      }
    });
  }

  loadAuditLogs(entityType?: string, entityId?: string) {
    this.loading.set(true);
    let url = '/audit-logs';
    const params: any = {};
    
    if (entityType) params.entityType = entityType;
    if (entityId) params.entityId = entityId;
    
    if (Object.keys(params).length > 0) {
      const queryString = new URLSearchParams(params).toString();
      url += '?' + queryString;
    }
    
    this.api.get<AuditLog[]>(url).subscribe({
      next: (logs) => {
        this.auditLogs.set(logs);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erreur chargement logs:', err);
        this.loading.set(false);
      }
    });
  }

  onDocumentTypeChange() {
    this.filterDocumentNumber = '';
    this.filterEntityId = '';
    // Si on change le type, on recharge tous les logs
    if (!this.filterDocumentType) {
      this.loadAuditLogs();
    }
  }

  onDocumentNumberChange() {
    if (!this.filterDocumentNumber || !this.filterDocumentType) {
      this.filterEntityId = '';
      this.loadAuditLogs();
      return;
    }
    
    // Résoudre l'ID du document à partir du numéro
    let documentId: string | null = null;
    
    if (this.filterDocumentType === 'BandeCommande') {
      const bc = this.store.bcs().find(b => (b.number || b.id) === this.filterDocumentNumber);
      documentId = bc?.id || null;
    } else if (this.filterDocumentType === 'FactureVente') {
      const invoice = this.store.invoices().find(inv => inv.type === 'sale' && inv.number === this.filterDocumentNumber);
      documentId = invoice?.id || null;
    } else if (this.filterDocumentType === 'FactureAchat') {
      const invoice = this.store.invoices().find(inv => inv.type === 'purchase' && inv.number === this.filterDocumentNumber);
      documentId = invoice?.id || null;
    }
    
    if (documentId) {
      this.filterEntityId = documentId;
      this.loadAuditLogs(this.filterDocumentType, documentId);
    } else {
      this.filterEntityId = '';
      this.loadAuditLogs();
    }
  }

  resolveDocumentNumber() {
    if (!this.filterEntityId || !this.filterDocumentType) return;
    
    if (this.filterDocumentType === 'BandeCommande') {
      const bc = this.store.bcs().find(b => b.id === this.filterEntityId);
      this.filterDocumentNumber = bc?.number || bc?.id || '';
    } else if (this.filterDocumentType === 'FactureVente') {
      const invoice = this.store.invoices().find(inv => inv.id === this.filterEntityId && inv.type === 'sale');
      this.filterDocumentNumber = invoice?.number || '';
    } else if (this.filterDocumentType === 'FactureAchat') {
      const invoice = this.store.invoices().find(inv => inv.id === this.filterEntityId && inv.type === 'purchase');
      this.filterDocumentNumber = invoice?.number || '';
    }
  }

  clearDocumentFilter() {
    this.filterDocumentType = '';
    this.filterDocumentNumber = '';
    this.filterEntityId = '';
    // Mettre à jour l'URL pour supprimer les query params
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true
    });
    this.loadAuditLogs();
  }

  applyFilters() {
    // Les filtres sont appliqués via le computed signal
  }

  clearFilters() {
    this.filterUser = '';
    this.filterEntityType = '';
    this.filterAction = '';
    this.filterDate = '';
    this.clearDocumentFilter();
  }

  formatDate(timestamp: string): string {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  formatTime(timestamp: string): string {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  getInitials(name: string): string {
    if (!name) return '?';
    const parts = name.split(/[\s@]+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  getActionBadgeClass(action: string): string {
    switch (action) {
      case 'CREATE': return 'bg-emerald-100 text-emerald-700';
      case 'UPDATE': return 'bg-blue-100 text-blue-700';
      case 'DELETE': return 'bg-red-100 text-red-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  getActionLabel(action: string): string {
    switch (action) {
      case 'CREATE': return 'Création';
      case 'UPDATE': return 'Modification';
      case 'DELETE': return 'Suppression';
      default: return action;
    }
  }

  getEntityIconClass(entityType: string): string {
    switch (entityType) {
      case 'BandeCommande': return 'bg-purple-100 text-purple-600';
      case 'FactureVente': return 'bg-emerald-100 text-emerald-600';
      case 'FactureAchat': return 'bg-orange-100 text-orange-600';
      case 'Client': return 'bg-blue-100 text-blue-600';
      case 'Fournisseur': return 'bg-indigo-100 text-indigo-600';
      case 'PrevisionPaiement': return 'bg-amber-100 text-amber-600';
      case 'Paiement': return 'bg-teal-100 text-teal-600';
      case 'Produit': return 'bg-rose-100 text-rose-600';
      case 'Charge': return 'bg-yellow-100 text-yellow-700';
      default: return 'bg-slate-100 text-slate-600';
    }
  }

  getEntityIcon(entityType: string): string {
    switch (entityType) {
      case 'BandeCommande': return 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z';
      case 'FactureVente': return 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2';
      case 'FactureAchat': return 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2';
      case 'Client': return 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z';
      case 'Fournisseur': return 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4';
      case 'PrevisionPaiement': return 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z';
      case 'Paiement': return 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z';
      case 'Produit': return 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4';
      case 'Charge': return 'M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-2m4-5h-4m0 0l2-2m-2 2l2 2';
      default: return 'M4 6h16M4 12h16M4 18h16';
    }
  }

  getEntityLabel(entityType: string): string {
    switch (entityType) {
      case 'BandeCommande': return 'Bon de Commande';
      case 'FactureVente': return 'Facture Vente';
      case 'FactureAchat': return 'Facture Achat';
      case 'Client': return 'Client';
      case 'Fournisseur': return 'Fournisseur';
      case 'PrevisionPaiement': return 'Prévision de Paiement';
      case 'Paiement': return 'Paiement';
      case 'Produit': return 'Produit';
      case 'Charge': return 'Charge';
      default: return entityType;
    }
  }

  getDetails(log: AuditLog): string {
    // Extraire les informations pertinentes pour un résumé court
    const extractInfo = (value: any): { name?: string; number?: string; key?: string } => {
      if (!value) return {};
      let obj = value;
      if (typeof value === 'string') {
        try {
          obj = JSON.parse(value);
        } catch {
          return { name: value };
        }
      }
      if (typeof obj === 'object' && obj !== null) {
        return {
          name: obj.name || obj.designation || obj.libelle || obj.nomBeneficiaire,
          number: obj.number || obj.numeroBC || obj.numeroOV,
          key: obj.id || obj._id
        };
      }
      return {};
    };

    if (log.action === 'CREATE') {
      const info = extractInfo(log.newValue);
      if (info.number) return `Création: ${info.number}`;
      if (info.name) return `Création: ${info.name}`;
      return 'Création effectuée';
    } else if (log.action === 'UPDATE') {
      const oldInfo = extractInfo(log.oldValue);
      const newInfo = extractInfo(log.newValue);
      const changedFields: string[] = [];
      
      if (oldInfo.number !== newInfo.number && (oldInfo.number || newInfo.number)) {
        changedFields.push('numéro');
      }
      if (oldInfo.name !== newInfo.name && (oldInfo.name || newInfo.name)) {
        changedFields.push('nom');
      }
      
      // Essayer d'extraire d'autres champs communs
      if (log.oldValue && log.newValue) {
        const oldObj = typeof log.oldValue === 'string' ? (() => { try { return JSON.parse(log.oldValue); } catch { return null; } })() : log.oldValue;
        const newObj = typeof log.newValue === 'string' ? (() => { try { return JSON.parse(log.newValue); } catch { return null; } })() : log.newValue;
        
        if (oldObj && newObj && typeof oldObj === 'object' && typeof newObj === 'object') {
          const commonFields = ['montant', 'amountHT', 'amountTTC', 'date', 'statut', 'status', 'montantPrevu', 'datePrevue'];
          for (const field of commonFields) {
            if (oldObj[field] !== newObj[field] && (oldObj[field] !== undefined || newObj[field] !== undefined)) {
              changedFields.push(field);
            }
          }
        }
      }
      
      if (changedFields.length > 0) {
        return `Maj: ${changedFields.slice(0, 3).join(', ')}${changedFields.length > 3 ? '...' : ''}`;
      }
      return 'Modification effectuée';
    } else if (log.action === 'DELETE') {
      const info = extractInfo(log.oldValue);
      if (info.number) return `Suppression: ${info.number}`;
      if (info.name) return `Suppression: ${info.name}`;
      return 'Suppression effectuée';
    }
    
    // Fallback
    if (log.newValue && typeof log.newValue === 'string') {
      return log.newValue.length > 50 ? log.newValue.substring(0, 50) + '...' : log.newValue;
    }
    if (log.oldValue && typeof log.oldValue === 'string') {
      return log.oldValue.length > 50 ? log.oldValue.substring(0, 50) + '...' : log.oldValue;
    }
    return log.entityId ? `ID: ${log.entityId}` : '-';
  }

  getActionSummary(log: AuditLog): string {
    const entityLabel = this.getEntityLabel(log.entityType);
    const actionLabel = this.getActionLabel(log.action).toLowerCase();
    
    if (log.action === 'CREATE') {
      const info = this.extractInfo(log.newValue);
      if (info.number) {
        return `${entityLabel} ${info.number} a été créé${entityLabel.includes('Facture') ? 'e' : ''}.`;
      }
      if (info.name) {
        return `${entityLabel} "${info.name}" a été créé${entityLabel.includes('Facture') ? 'e' : ''}.`;
      }
      return `Un${entityLabel.startsWith('Bon') ? '' : 'e'} ${entityLabel.toLowerCase()} a été créé${entityLabel.includes('Facture') ? 'e' : ''}.`;
    } else if (log.action === 'UPDATE') {
      const changedFields = this.getChangedFields(log);
      if (changedFields && changedFields.length > 0) {
        const fieldsList = changedFields.slice(0, 3).map(f => f.label).join(', ');
        return `${entityLabel} a été modifié${entityLabel.includes('Facture') ? 'e' : ''}. ${fieldsList} ${changedFields.length > 3 ? 'et autres' : ''} ${changedFields.length > 1 ? 'ont été mis à jour' : 'a été mis à jour'}.`;
      }
      return `${entityLabel} a été modifié${entityLabel.includes('Facture') ? 'e' : ''}.`;
    } else if (log.action === 'DELETE') {
      const info = this.extractInfo(log.oldValue);
      if (info.number) {
        return `${entityLabel} ${info.number} a été supprimé${entityLabel.includes('Facture') ? 'e' : ''}.`;
      }
      if (info.name) {
        return `${entityLabel} "${info.name}" a été supprimé${entityLabel.includes('Facture') ? 'e' : ''}.`;
      }
      return `Un${entityLabel.startsWith('Bon') ? '' : 'e'} ${entityLabel.toLowerCase()} a été supprimé${entityLabel.includes('Facture') ? 'e' : ''}.`;
    }
    return `Action ${actionLabel} effectuée sur ${entityLabel.toLowerCase()}.`;
  }

  extractInfo(value: any): { name?: string; number?: string; key?: string } {
    if (!value) return {};
    let obj = value;
    if (typeof value === 'string') {
      try {
        obj = JSON.parse(value);
      } catch {
        return { name: value };
      }
    }
    if (typeof obj === 'object' && obj !== null) {
      return {
        name: obj.name || obj.designation || obj.libelle || obj.nomBeneficiaire || obj.raisonSociale,
        number: obj.number || obj.numeroBC || obj.numeroOV,
        key: obj.id || obj._id
      };
    }
    return {};
  }

  getChangedFields(log: AuditLog): Array<{ key: string; label: string; oldValue: string; newValue: string }> | null {
    if (log.action !== 'UPDATE' || !log.oldValue || !log.newValue) return null;
    
    let oldObj: any = log.oldValue;
    let newObj: any = log.newValue;
    
    if (typeof log.oldValue === 'string') {
      try {
        oldObj = JSON.parse(log.oldValue);
      } catch {
        return null;
      }
    }
    if (typeof log.newValue === 'string') {
      try {
        newObj = JSON.parse(log.newValue);
      } catch {
        return null;
      }
    }
    
    if (typeof oldObj !== 'object' || typeof newObj !== 'object' || !oldObj || !newObj) {
      return null;
    }
    
    const fieldLabels: Record<string, string> = {
      number: 'Numéro',
      numeroBC: 'Numéro BC',
      numeroOV: 'Numéro OV',
      name: 'Nom',
      designation: 'Désignation',
      libelle: 'Libellé',
      montant: 'Montant',
      amountHT: 'Montant HT',
      amountTTC: 'Montant TTC',
      montantPrevu: 'Montant prévu',
      date: 'Date',
      datePrevue: 'Date prévue',
      dueDate: 'Date d\'échéance',
      statut: 'Statut',
      status: 'Statut',
      partnerId: 'Partenaire',
      supplierId: 'Fournisseur',
      clientId: 'Client',
      rib: 'RIB',
      banque: 'Banque',
      ice: 'ICE',
      phone: 'Téléphone',
      email: 'Email',
      address: 'Adresse'
    };
    
    const changedFields: Array<{ key: string; label: string; oldValue: string; newValue: string }> = [];
    const allKeys = new Set([...Object.keys(oldObj), ...Object.keys(newObj)]);
    
    // Ignorer les champs techniques
    const ignoredFields = ['id', '_id', 'createdAt', 'updatedAt', 'timestamp', 'userId', 'userName', 'ipAddress', 'userAgent'];
    
    for (const key of allKeys) {
      if (ignoredFields.includes(key)) continue;
      
      const oldVal = oldObj[key];
      const newVal = newObj[key];
      
      if (oldVal !== newVal && (oldVal !== undefined || newVal !== undefined)) {
        const label = fieldLabels[key] || key.charAt(0).toUpperCase() + key.slice(1);
        const formatVal = (val: any): string => {
          if (val === null || val === undefined) return '-';
          if (typeof val === 'object') return JSON.stringify(val);
          if (typeof val === 'boolean') return val ? 'Oui' : 'Non';
          return String(val);
        };
        
        changedFields.push({
          key,
          label,
          oldValue: formatVal(oldVal),
          newValue: formatVal(newVal)
        });
      }
    }
    
    return changedFields.length > 0 ? changedFields : null;
  }

  async copyToClipboard(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      // Optionnel: afficher un toast (si disponible)
      // this.store.showToast('Copié dans le presse-papiers', 'success');
    } catch (err) {
      console.error('Erreur lors de la copie:', err);
    }
  }

  selectLog(log: AuditLog) {
    this.selectedLog.set(log);
    this.activeDetailsTab.set('resume');
  }

  closeDetails() {
    this.selectedLog.set(null);
    this.activeDetailsTab.set('resume');
  }

  formatValue(value: any): string {
    if (!value) return 'N/A';
    if (typeof value === 'string') {
      // Essayer de parser si c'est du JSON
      try {
        const parsed = JSON.parse(value);
        return JSON.stringify(parsed, null, 2);
      } catch {
        return value;
      }
    }
    if (typeof value === 'object') {
      return JSON.stringify(value, null, 2);
    }
    return String(value);
  }
}




import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
          <div class="relative bg-white rounded-2xl shadow-2xl max-w-3xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col">
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
            
            <div class="flex-1 overflow-y-auto p-6 space-y-6">
              @if (selectedLog(); as log) {
                <!-- Informations générales -->
                <div class="bg-slate-50 p-4 rounded-lg">
                  <h3 class="font-semibold text-slate-700 mb-3">Informations Générales</h3>
                  <div class="space-y-2 text-sm">
                    <div><span class="text-slate-500">Date/Heure:</span> <span class="font-medium">{{ formatDate(log.timestamp) }} à {{ formatTime(log.timestamp) }}</span></div>
                    <div><span class="text-slate-500">Utilisateur:</span> <span class="font-medium">{{ log.userName || log.userId }}</span></div>
                    <div><span class="text-slate-500">Action:</span> <span [class]="getActionBadgeClass(log.action)" class="px-2 py-1 rounded-full text-xs font-semibold ml-2">{{ getActionLabel(log.action) }}</span></div>
                    <div><span class="text-slate-500">Type d'entité:</span> <span class="font-medium">{{ getEntityLabel(log.entityType) }}</span></div>
                  </div>
                </div>

                <!-- Détails de l'action -->
                <div class="space-y-4">
                  @if (log.action === 'UPDATE' && log.oldValue) {
                    <div class="bg-amber-50 p-4 rounded-lg border border-amber-200">
                      <h3 class="font-semibold text-amber-700 mb-3">Ancienne Valeur</h3>
                      <div class="text-sm text-amber-800 whitespace-pre-wrap break-words font-mono bg-white p-3 rounded border border-amber-200 max-h-48 overflow-y-auto">
                        {{ formatValue(log.oldValue) }}
                      </div>
                    </div>
                  }
                  
                  @if (log.newValue) {
                    <div class="bg-emerald-50 p-4 rounded-lg border border-emerald-200">
                      <h3 class="font-semibold text-emerald-700 mb-3">{{ log.action === 'UPDATE' ? 'Nouvelle Valeur' : 'Détails' }}</h3>
                      <div class="text-sm text-emerald-800 whitespace-pre-wrap break-words font-mono bg-white p-3 rounded border border-emerald-200 max-h-48 overflow-y-auto">
                        {{ formatValue(log.newValue) }}
                      </div>
                    </div>
                  }
                  
                  @if (log.action === 'DELETE' && log.oldValue) {
                    <div class="bg-red-50 p-4 rounded-lg border border-red-200">
                      <h3 class="font-semibold text-red-700 mb-3">Valeur Supprimée</h3>
                      <div class="text-sm text-red-800 whitespace-pre-wrap break-words font-mono bg-white p-3 rounded border border-red-200 max-h-48 overflow-y-auto">
                        {{ formatValue(log.oldValue) }}
                      </div>
                    </div>
                  }
                </div>
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

  auditLogs = signal<AuditLog[]>([]);
  loading = signal(false);
  selectedLog = signal<AuditLog | null>(null);
  
  filterUser = '';
  filterEntityType = '';
  filterAction = '';
  filterDate = '';

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
    
    // Trier par date décroissante
    return logs.sort((a, b) => {
      const dateA = new Date(a.timestamp || 0).getTime();
      const dateB = new Date(b.timestamp || 0).getTime();
      return dateB - dateA;
    });
  });

  ngOnInit() {
    this.loadAuditLogs();
  }

  loadAuditLogs() {
    this.loading.set(true);
    this.api.get<AuditLog[]>('/audit-logs').subscribe({
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

  applyFilters() {
    // Les filtres sont appliqués via le computed signal
  }

  clearFilters() {
    this.filterUser = '';
    this.filterEntityType = '';
    this.filterAction = '';
    this.filterDate = '';
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
      default: return entityType;
    }
  }

  getDetails(log: AuditLog): string {
    if (log.newValue && typeof log.newValue === 'string') {
      return log.newValue;
    }
    if (log.oldValue && typeof log.oldValue === 'string') {
      return log.oldValue;
    }
    return log.entityId ? `ID: ${log.entityId}` : '-';
  }

  selectLog(log: AuditLog) {
    this.selectedLog.set(log);
  }

  closeDetails() {
    this.selectedLog.set(null);
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




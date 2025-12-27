
import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StoreService } from '../../services/store.service';
import { ApiService } from '../../services/api.service';

interface ImportLog {
  id: string;
  fileName: string;
  details: string;
  success: boolean;
  successCount: number;
  errorCount: number;
  createdAt: string;
  // For display
  name?: string;
  time?: string;
}

interface ImportResult {
  totalRows: number;
  successCount: number;
  errorCount: number;
  errors: string[];
  warnings: string[];
}

@Component({
  selector: 'app-import',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-8 fade-in-up max-w-5xl mx-auto pb-10">
      
      <div class="text-center py-8">
        <h1 class="text-3xl font-bold text-slate-800 font-display">Importer des données</h1>
        <p class="text-slate-500 mt-2">Migrez vos données historiques via fichiers Excel (.xlsx)</p>
      </div>

      <!-- Import Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        
        <!-- Products Import -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-lg transition-shadow duration-300">
          <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
            <h3 class="font-bold text-slate-800 flex items-center gap-2">
              <span class="p-2 bg-blue-100 text-blue-600 rounded-lg">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg>
              </span>
              Catalogue Produits
            </h3>
            <span class="text-xs font-medium text-slate-400 bg-white px-2 py-1 rounded border border-slate-200">.xlsx</span>
          </div>
          <div class="p-8">
            <div class="border-2 border-dashed border-slate-300 rounded-xl bg-slate-50 h-48 flex flex-col items-center justify-center text-slate-400 hover:bg-blue-50 hover:border-blue-400 hover:text-blue-500 transition-all cursor-pointer group relative overflow-hidden" 
                 (click)="fileInputProducts.click()"
                 (dragover)="onDragOver($event)"
                 (dragleave)="onDragLeave($event)"
                 (drop)="onDrop($event, 'produits')">
               
               @if (isImporting() && currentType() === 'produits') {
                 <div class="absolute inset-0 bg-white/90 z-10 flex flex-col items-center justify-center">
                    <div class="w-2/3 bg-slate-200 rounded-full h-2 mb-2">
                       <div class="bg-blue-600 h-2 rounded-full transition-all duration-300" [style.width.%]="progress()"></div>
                    </div>
                    <span class="text-xs font-bold text-blue-600">Traitement... {{ progress() }}%</span>
                 </div>
               } @else {
                 <svg class="w-12 h-12 mb-3 group-hover:scale-110 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                 <span class="text-sm font-medium">Glissez votre fichier ici</span>
                 <span class="text-xs mt-1 opacity-70">ou cliquez pour parcourir</span>
               }
               
               <input #fileInputProducts type="file" accept=".xlsx,.xls" (change)="onFileSelected($event, 'produits')" class="hidden">
            </div>
            <div class="mt-6 flex justify-between items-center">
               <button type="button" (click)="downloadTemplate('produits')" class="text-xs text-blue-600 font-medium hover:underline flex items-center gap-1">
                 <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path></svg>
                 Télécharger modèle
               </button>
               @if (selectedFiles['produits']) {
                 <span class="text-xs text-slate-400">{{ selectedFiles['produits']?.name }}</span>
               }
            </div>
          </div>
        </div>

        <!-- BC Import -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-lg transition-shadow duration-300">
          <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
            <h3 class="font-bold text-slate-800 flex items-center gap-2">
              <span class="p-2 bg-emerald-100 text-emerald-600 rounded-lg">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
              </span>
              Historique Commandes (BC)
            </h3>
            <span class="text-xs font-medium text-slate-400 bg-white px-2 py-1 rounded border border-slate-200">.xlsx</span>
          </div>
          <div class="p-8">
            <div class="border-2 border-dashed border-slate-300 rounded-xl bg-slate-50 h-48 flex flex-col items-center justify-center text-slate-400 hover:bg-emerald-50 hover:border-emerald-400 hover:text-emerald-500 transition-all cursor-pointer group relative overflow-hidden"
                 (click)="fileInputBC.click()"
                 (dragover)="onDragOver($event)"
                 (dragleave)="onDragLeave($event)"
                 (drop)="onDrop($event, 'bc')">
               
               @if (isImporting() && currentType() === 'bc') {
                 <div class="absolute inset-0 bg-white/90 z-10 flex flex-col items-center justify-center">
                    <div class="w-2/3 bg-slate-200 rounded-full h-2 mb-2">
                       <div class="bg-emerald-600 h-2 rounded-full transition-all duration-300" [style.width.%]="progress()"></div>
                    </div>
                    <span class="text-xs font-bold text-emerald-600">Chargement... {{ progress() }}%</span>
                 </div>
               } @else {
                 <svg class="w-12 h-12 mb-3 group-hover:scale-110 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                 <span class="text-sm font-medium">Glissez votre fichier ici</span>
                 <span class="text-xs mt-1 opacity-70">ou cliquez pour parcourir</span>
               }
               
               <input #fileInputBC type="file" accept=".xlsx,.xls" (change)="onFileSelected($event, 'bc')" class="hidden">
            </div>
            <div class="mt-6 flex justify-between items-center">
               <button type="button" (click)="downloadTemplate('bc')" class="text-xs text-blue-600 font-medium hover:underline flex items-center gap-1">
                 <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path></svg>
                 Télécharger modèle
               </button>
               @if (selectedFiles['bc']) {
                 <span class="text-xs text-slate-400">{{ selectedFiles['bc']?.name }}</span>
               }
            </div>
          </div>
        </div>

        <!-- Operations Comptables Import -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-lg transition-shadow duration-300">
          <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
            <h3 class="font-bold text-slate-800 flex items-center gap-2">
              <span class="p-2 bg-purple-100 text-purple-600 rounded-lg">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
              </span>
              Opérations Comptables
            </h3>
            <span class="text-xs font-medium text-slate-400 bg-white px-2 py-1 rounded border border-slate-200">.xlsx</span>
          </div>
          <div class="p-8">
            <div class="border-2 border-dashed border-slate-300 rounded-xl bg-slate-50 h-48 flex flex-col items-center justify-center text-slate-400 hover:bg-purple-50 hover:border-purple-400 hover:text-purple-500 transition-all cursor-pointer group relative overflow-hidden"
                 (click)="fileInputOperations.click()"
                 (dragover)="onDragOver($event)"
                 (dragleave)="onDragLeave($event)"
                 (drop)="onDrop($event, 'operations')">
               
               @if (isImporting() && currentType() === 'operations') {
                 <div class="absolute inset-0 bg-white/90 z-10 flex flex-col items-center justify-center">
                    <div class="w-2/3 bg-slate-200 rounded-full h-2 mb-2">
                       <div class="bg-purple-600 h-2 rounded-full transition-all duration-300" [style.width.%]="progress()"></div>
                    </div>
                    <span class="text-xs font-bold text-purple-600">Traitement... {{ progress() }}%</span>
                 </div>
               } @else {
                 <svg class="w-12 h-12 mb-3 group-hover:scale-110 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                 <span class="text-sm font-medium">Glissez votre fichier ici</span>
                 <span class="text-xs mt-1 opacity-70">ou cliquez pour parcourir</span>
               }
               
               <input #fileInputOperations type="file" accept=".xlsx,.xls" (change)="onFileSelected($event, 'operations')" class="hidden">
            </div>
            <div class="mt-6 flex justify-between items-center">
               <button type="button" (click)="downloadTemplate('operations')" class="text-xs text-blue-600 font-medium hover:underline flex items-center gap-1">
                 <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path></svg>
                 Télécharger modèle
               </button>
               @if (selectedFiles['operations']) {
                 <span class="text-xs text-slate-400">{{ selectedFiles['operations']?.name }}</span>
               }
            </div>
          </div>
        </div>

      </div>

      <!-- Logs Section -->
      <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
        <h3 class="text-sm font-bold text-slate-800 mb-4 uppercase tracking-wide">Journal des imports récents</h3>
        <div class="space-y-3">
          
          @if (logs().length === 0) {
            <div class="text-center py-4 text-slate-400 text-sm">Aucun historique récent.</div>
          }

          @for (log of logs(); track log.id) {
             <div class="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100 animate-[fadeIn_0.3s_ease-out]">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full flex items-center justify-center" [class]="log.success ? 'bg-emerald-100 text-emerald-600' : 'bg-red-100 text-red-600'">
                    @if (log.success) {
                       <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                    } @else {
                       <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    }
                  </div>
                  <div>
                    <p class="text-sm font-bold text-slate-700">{{ log.fileName || log.name }}</p>
                    <p class="text-xs text-slate-500">{{ log.details }}</p>
                  </div>
                </div>
                <div class="flex flex-col items-end gap-1">
                  <span class="text-xs text-slate-400">{{ log.time }}</span>
                  @if (log.successCount !== undefined || log.errorCount !== undefined) {
                    <span class="text-xs text-slate-500">
                      <span class="text-emerald-600">✓ {{ log.successCount || 0 }}</span>
                      @if (log.errorCount > 0) {
                        <span class="text-red-600 ml-2">✗ {{ log.errorCount }}</span>
                      }
                    </span>
                  }
                </div>
             </div>
          }

        </div>
      </div>

    </div>
  `
})
export class ImportComponent implements OnInit {
  store = inject(StoreService);
  api = inject(ApiService);

  isImporting = signal(false);
  currentType = signal<string>('');
  progress = signal(0);
  selectedFiles: Record<string, File | null> = {
    'produits': null,
    'bc': null,
    'operations': null
  };
  
  logs = signal<ImportLog[]>([]);

  async ngOnInit() {
    await this.loadImportHistory();
  }

  async loadImportHistory() {
    try {
      const backendLogs = await this.api.get<ImportLog[]>('/import/history').toPromise() || [];
      const mapped = backendLogs.map(log => this.mapImportLog(log));
      this.logs.set(mapped);
    } catch (error) {
      console.error('Error loading import history:', error);
    }
  }

  private mapImportLog(backendLog: any): ImportLog {
    const date = new Date(backendLog.createdAt);
    return {
      ...backendLog,
      name: backendLog.fileName,
      time: date.toLocaleString('fr-FR', { 
        day: '2-digit', 
        month: '2-digit', 
        year: 'numeric',
        hour: '2-digit', 
        minute: '2-digit' 
      })
    };
  }

  onFileSelected(event: Event, type: string) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFiles[type] = input.files[0];
      this.uploadFile(input.files[0], type);
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    if (event.currentTarget instanceof HTMLElement) {
      event.currentTarget.classList.add('border-blue-500', 'bg-blue-50');
    }
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    if (event.currentTarget instanceof HTMLElement) {
      event.currentTarget.classList.remove('border-blue-500', 'bg-blue-50');
    }
  }

  onDrop(event: DragEvent, type: string) {
    event.preventDefault();
    event.stopPropagation();
    if (event.currentTarget instanceof HTMLElement) {
      event.currentTarget.classList.remove('border-blue-500', 'bg-blue-50');
    }
    
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      const file = files[0];
      if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
        this.selectedFiles[type] = file;
        this.uploadFile(file, type);
      } else {
        this.store.showToast('Format de fichier non supporté. Utilisez .xlsx ou .xls', 'error');
      }
    }
  }

  async uploadFile(file: File, type: string) {
    if (this.isImporting()) return;

    this.isImporting.set(true);
    this.currentType.set(type);
    this.progress.set(0);

    try {
      // Simulate progress (real progress would require XMLHttpRequest with upload events)
      const progressInterval = setInterval(() => {
        this.progress.update(p => {
          if (p >= 90) {
            clearInterval(progressInterval);
            return 90;
          }
          return p + Math.floor(Math.random() * 10) + 5;
        });
      }, 200);

      // Utiliser le bon endpoint selon le type
      let endpoint = '/import/excel';
      if (type === 'produits') {
        endpoint = '/import/produits';
      } else if (type === 'operations') {
        endpoint = '/import/operations';
      }
      const result = await this.api.uploadFile(endpoint, file).toPromise() as ImportResult;
      
      clearInterval(progressInterval);
      this.progress.set(100);

      // Process result
      const details: string[] = [];
      if (result.successCount > 0) {
        details.push(`${result.successCount} ligne(s) importée(s) avec succès`);
      }
      if (result.errorCount > 0) {
        details.push(`${result.errorCount} erreur(s) détectée(s)`);
      }
      if (result.warnings.length > 0) {
        details.push(`${result.warnings.length} avertissement(s)`);
      }

      const success = result.errorCount === 0 && result.successCount > 0;
      const detailText = details.length > 0 ? details.join(', ') : 'Import effectué';

      // Reload import history from backend
      await this.loadImportHistory();

      if (success) {
        this.store.showToast('Importation réussie ! Données mises à jour.', 'success');
        // Reload data
        if (type === 'produits') {
          await this.store.loadProducts();
        } else if (type === 'operations') {
          // Les opérations comptables sont stockées séparément, pas besoin de recharger
          // Mais on peut recharger le dashboard pour mettre à jour les KPIs
          await this.store.loadDashboardKPIs();
        } else {
          await this.store.loadBCs();
          await this.store.loadInvoices();
          await this.store.loadDashboardKPIs();
        }
      } else {
        this.store.showToast(`Import terminé avec ${result.errorCount} erreur(s)`, 'error');
      }

      // Show errors if any
      if (result.errors.length > 0) {
        const errorDetails = result.errors.slice(0, 5).join('; ');
        this.store.showToast(`Erreurs: ${errorDetails}`, 'error');
      }

      // Reset
      setTimeout(() => {
        this.isImporting.set(false);
        this.progress.set(0);
        this.selectedFiles[type] = null;
      }, 1000);

    } catch (error: any) {
      this.progress.set(0);
      this.isImporting.set(false);
      this.store.showToast('Erreur lors de l\'import: ' + (error.message || 'Erreur inconnue'), 'error');
      
      // Reload import history from backend (will include error if backend logged it)
      await this.loadImportHistory();
    }
  }

  async downloadTemplate(type: string) {
    try {
      // Utiliser le bon endpoint selon le type
      let endpoint = '/import/template';
      let filename = 'Modele_Import_BF4Invest.xlsx';
      if (type === 'produits') {
        endpoint = '/import/template/produits';
        filename = 'Modele_Catalogue_Produits.xlsx';
      } else if (type === 'operations') {
        endpoint = '/import/template/operations';
        filename = 'Modele_Operations_Comptables.xlsx';
      }
      
      // Télécharger le modèle depuis le backend
      const blob = await this.api.downloadFile(endpoint).toPromise();
      
      if (blob) {
        // Créer un lien de téléchargement
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        
        this.store.showToast('Modèle téléchargé avec succès', 'success');
      }
    } catch (error: any) {
      this.store.showToast('Erreur lors du téléchargement du modèle: ' + (error.message || 'Erreur inconnue'), 'error');
    }
  }
}

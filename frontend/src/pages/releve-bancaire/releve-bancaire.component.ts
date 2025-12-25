import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ReleveBancaireService, ImportResult } from '../../services/releve-bancaire.service';
import { StoreService } from '../../services/store.service';
import { ApiService } from '../../services/api.service';
import type { TransactionBancaire } from '../../models/types';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-releve-bancaire',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 border-b border-slate-200/60 pb-6">
        <div>
          <h1 class="text-2xl md:text-3xl font-bold text-slate-800 font-display">Relevé Bancaire</h1>
          <p class="text-slate-500 mt-2 text-sm">Import et gestion des transactions bancaires pour le calcul TVA au règlement</p>
        </div>
        <div class="flex gap-3">
          <button (click)="loadTransactions()" class="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition font-medium">
            Actualiser
          </button>
        </div>
      </div>

      <!-- Upload Section Excel -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-bold text-slate-800 mb-4">Importer un relevé bancaire (Excel)</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Mois</label>
            <select [(ngModel)]="selectedMois" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              @for (m of moisList; track m.value) {
                <option [value]="m.value">{{ m.label }}</option>
              }
            </select>
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Année</label>
            <input type="number" [(ngModel)]="selectedAnnee" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div class="flex items-end">
            <input #fileInput type="file" accept=".xlsx,.xls" (change)="onFileSelected($event)" class="hidden">
            <button (click)="fileInput.click()" [disabled]="importing()" class="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-medium disabled:opacity-50">
              @if (importing()) {
                Import en cours...
              } @else {
                Sélectionner fichier Excel
              }
            </button>
          </div>
        </div>
        @if (selectedFile()) {
          <div class="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
            <span class="text-sm text-slate-700">{{ selectedFile()?.name }}</span>
            <div class="flex gap-2">
              <button (click)="importFile()" [disabled]="importing() || !selectedMois || !selectedAnnee" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition font-medium disabled:opacity-50">
                @if (importing()) {
                  Import...
                } @else {
                  Importer
                }
              </button>
              <button (click)="selectedFile.set(null)" class="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
                Annuler
              </button>
            </div>
          </div>
        }
      </div>

      <!-- Upload Section PDF -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <h2 class="text-lg font-bold text-slate-800 mb-4">Stocker un relevé bancaire (PDF)</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Mois</label>
            <select [(ngModel)]="selectedMois" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              @for (m of moisList; track m.value) {
                <option [value]="m.value">{{ m.label }}</option>
              }
            </select>
          </div>
          <div>
            <label class="block text-sm font-semibold text-slate-700 mb-1">Année</label>
            <input type="number" [(ngModel)]="selectedAnnee" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
          </div>
          <div class="flex items-end">
            <input #pdfFileInput type="file" accept=".pdf" (change)="onPdfFileSelected($event)" class="hidden">
            <button (click)="pdfFileInput.click()" [disabled]="uploadingPdf()" class="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition font-medium disabled:opacity-50">
              @if (uploadingPdf()) {
                Upload en cours...
              } @else {
                Sélectionner fichier PDF
              }
            </button>
          </div>
        </div>
        @if (selectedPdfFile()) {
          <div class="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
            <span class="text-sm text-slate-700">{{ selectedPdfFile()?.name }}</span>
            <div class="flex gap-2">
              <button (click)="uploadPdfFile()" [disabled]="uploadingPdf() || !selectedMois || !selectedAnnee" class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition font-medium disabled:opacity-50">
                @if (uploadingPdf()) {
                  Upload...
                } @else {
                  Uploader
                }
              </button>
              <button (click)="selectedPdfFile.set(null)" class="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 transition font-medium">
                Annuler
              </button>
            </div>
          </div>
        }
        @if (uploadedPdfFiles().length > 0) {
          <div class="mt-4 space-y-2">
            <h3 class="text-sm font-semibold text-slate-700">Fichiers PDF stockés</h3>
            @for (pdfFile of uploadedPdfFiles(); track pdfFile.id) {
              <div class="flex items-center justify-between p-3 bg-indigo-50 rounded-lg border border-indigo-100">
                <div class="flex items-center gap-3">
                  <svg class="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
                  <div>
                    <span class="text-sm font-medium text-slate-700">{{ pdfFile.filename }}</span>
                    <p class="text-xs text-slate-500">{{ pdfFile.mois }}/{{ pdfFile.annee }} - {{ formatDate(pdfFile.uploadedAt) }}</p>
                  </div>
                </div>
                <button (click)="downloadPdfFile(pdfFile.fichierId)" class="px-3 py-1.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition text-sm font-medium">
                  Télécharger
                </button>
              </div>
            }
          </div>
        }
      </div>

      <!-- Mapping Section -->
      @if (transactions().length > 0) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
          <div class="flex justify-between items-center mb-4">
            <h2 class="text-lg font-bold text-slate-800">Transactions importées</h2>
            <button (click)="mapperTransactions()" [disabled]="mapping() || !selectedMois || !selectedAnnee" class="px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 transition font-medium disabled:opacity-50">
              @if (mapping()) {
                Mapping...
              } @else {
                Mapper automatiquement
              }
            </button>
          </div>
          <div class="text-sm text-slate-600 mb-4">
            <span class="font-semibold">{{ transactions().length }}</span> transactions,
            <span class="font-semibold text-emerald-600">{{ mappedCount() }}</span> mappées,
            <span class="font-semibold text-amber-600">{{ unmappedCount() }}</span> non mappées
          </div>
        </div>
      }

      <!-- Transactions Table -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <h2 class="text-lg font-bold text-slate-800">Liste des Transactions</h2>
          <div class="flex gap-2">
            <select [(ngModel)]="filterMapped" (change)="loadTransactions()" class="px-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 outline-none">
              <option [value]="null">Toutes</option>
              <option [value]="true">Mappées</option>
              <option [value]="false">Non mappées</option>
            </select>
          </div>
        </div>
        @if (loading()) {
          <div class="p-12 text-center text-slate-500">
            <p>Chargement...</p>
          </div>
        } @else if (transactions().length === 0) {
          <div class="p-12 text-center text-slate-500">
            <p>Aucune transaction trouvée. Importez un relevé bancaire pour commencer.</p>
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm min-w-[1000px]">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Date</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Libellé</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Débit</th>
                  <th class="px-4 py-3 text-right font-semibold text-slate-700">Crédit</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Référence</th>
                  <th class="px-4 py-3 text-left font-semibold text-slate-700">Facture liée</th>
                  <th class="px-4 py-3 text-center font-semibold text-slate-700">Statut</th>
                  <th class="px-4 py-3 text-center font-semibold text-slate-700">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (transaction of transactions(); track transaction.id) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-4 py-3">{{ formatDate(transaction.dateOperation) }}</td>
                    <td class="px-4 py-3 max-w-xs truncate" [title]="transaction.libelle">{{ transaction.libelle }}</td>
                    <td class="px-4 py-3 text-right">{{ transaction.debit | number:'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right">{{ transaction.credit | number:'1.2-2' }}</td>
                    <td class="px-4 py-3">{{ transaction.reference || '-' }}</td>
                    <td class="px-4 py-3">
                      @if (transaction.factureVenteId) {
                        <span class="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded">Vente</span>
                      } @else if (transaction.factureAchatId) {
                        <span class="text-xs px-2 py-1 bg-orange-100 text-orange-700 rounded">Achat</span>
                      } @else {
                        <span class="text-slate-400">-</span>
                      }
                    </td>
                    <td class="px-4 py-3 text-center">
                      @if (transaction.mapped) {
                        <span class="px-2 py-1 bg-emerald-100 text-emerald-700 rounded-full text-xs font-semibold">Mappée</span>
                      } @else {
                        <span class="px-2 py-1 bg-amber-100 text-amber-700 rounded-full text-xs font-semibold">Non mappée</span>
                      }
                    </td>
                    <td class="px-4 py-3">
                      <button (click)="linkManually(transaction)" [disabled]="transaction.mapped" class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-full transition disabled:opacity-50" title="Lier manuellement">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"></path></svg>
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `
})
export class ReleveBancaireComponent implements OnInit {
  private releveService = inject(ReleveBancaireService);
  private store = inject(StoreService);
  private apiService = inject(ApiService);

  transactions = signal<TransactionBancaire[]>([]);
  selectedFile = signal<File | null>(null);
  selectedMois: number = new Date().getMonth() + 1;
  selectedAnnee: number = new Date().getFullYear();
  filterMapped: boolean | null = null;
  importing = signal(false);
  mapping = signal(false);
  loading = signal(false);
  
  // PDF upload
  selectedPdfFile = signal<File | null>(null);
  uploadingPdf = signal(false);
  uploadedPdfFiles = signal<Array<{ id: string; fichierId: string; filename: string; url?: string; mois: number; annee: number; uploadedAt: string }>>([]);

  moisList = [
    { value: 1, label: 'Janvier' }, { value: 2, label: 'Février' }, { value: 3, label: 'Mars' },
    { value: 4, label: 'Avril' }, { value: 5, label: 'Mai' }, { value: 6, label: 'Juin' },
    { value: 7, label: 'Juillet' }, { value: 8, label: 'Août' }, { value: 9, label: 'Septembre' },
    { value: 10, label: 'Octobre' }, { value: 11, label: 'Novembre' }, { value: 12, label: 'Décembre' }
  ];

  mappedCount = computed(() => 
    this.transactions().filter(t => t.mapped).length
  );

  unmappedCount = computed(() => 
    this.transactions().filter(t => !t.mapped).length
  );

  ngOnInit() {
    this.loadTransactions();
    this.loadUploadedPdfFiles();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile.set(input.files[0]);
    }
  }

  importFile() {
    const file = this.selectedFile();
    if (!file || !this.selectedMois || !this.selectedAnnee) return;

    this.importing.set(true);
    this.releveService.importReleve(file, this.selectedMois, this.selectedAnnee).subscribe({
      next: (result: ImportResult) => {
        this.importing.set(false);
        if (result.errorCount === 0) {
          this.store.showToast(`Import réussi: ${result.successCount} transactions importées`, 'success');
          this.selectedFile.set(null);
          this.loadTransactions();
        } else {
          this.store.showToast(`Import terminé avec ${result.errorCount} erreur(s)`, 'info');
          if (result.errors && result.errors.length > 0) {
            console.error('Erreurs d\'import:', result.errors);
          }
        }
      },
      error: (err) => {
        this.importing.set(false);
        this.store.showToast('Erreur lors de l\'import', 'error');
      }
    });
  }

  mapperTransactions() {
    if (!this.selectedMois || !this.selectedAnnee) return;

    this.mapping.set(true);
    this.releveService.mapperTransactions(this.selectedMois, this.selectedAnnee).subscribe({
      next: (stats) => {
        this.mapping.set(false);
        this.store.showToast(
          `Mapping terminé: ${stats.mapped} transactions mappées, ${stats.paiementsCrees} paiements créés`,
          'success'
        );
        this.loadTransactions();
      },
      error: (err) => {
        this.mapping.set(false);
        this.store.showToast('Erreur lors du mapping', 'error');
      }
    });
  }

  loadTransactions() {
    this.loading.set(true);
    const params: any = {};
    if (this.selectedMois) params.mois = this.selectedMois;
    if (this.selectedAnnee) params.annee = this.selectedAnnee;
    if (this.filterMapped !== null) params.mapped = this.filterMapped;

    this.releveService.getTransactions(params).subscribe({
      next: (data) => {
        this.transactions.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.store.showToast('Erreur lors du chargement', 'error');
        this.loading.set(false);
      }
    });
  }

  linkManually(transaction: TransactionBancaire) {
    // TODO: Implémenter la liaison manuelle avec sélection de facture
    this.store.showToast('Fonctionnalité de liaison manuelle à implémenter', 'info');
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('fr-FR');
  }

  // PDF file upload methods
  onPdfFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      // Vérifier la taille (10MB max)
      if (file.size > 10 * 1024 * 1024) {
        this.store.showToast('Le fichier est trop volumineux (max 10MB)', 'error');
        return;
      }
      // Vérifier que c'est un PDF
      if (file.type !== 'application/pdf') {
        this.store.showToast('Seuls les fichiers PDF sont acceptés', 'error');
        return;
      }
      this.selectedPdfFile.set(file);
    }
  }

  async uploadPdfFile() {
    const file = this.selectedPdfFile();
    if (!file || !this.selectedMois || !this.selectedAnnee) return;

    this.uploadingPdf.set(true);
    try {
      const result = await firstValueFrom(
        this.releveService.uploadPdfReleve(
          file,
          this.selectedMois,
          this.selectedAnnee
        )
      );

      if (result) {
        // Recharger la liste des fichiers
        await this.loadUploadedPdfFiles();
        this.selectedPdfFile.set(null);
        this.store.showToast('Fichier PDF uploadé avec succès', 'success');
      }
    } catch (error: any) {
      console.error('Erreur upload PDF:', error);
      this.store.showToast('Erreur lors de l\'upload du fichier PDF', 'error');
    } finally {
      this.uploadingPdf.set(false);
    }
  }

  private ensurePdfExtension(filename: string): string {
    if (!filename) return 'releve-bancaire.pdf';
    const lower = filename.toLowerCase();
    if (lower.endsWith('.pdf')) {
      return filename;
    }
    return filename + '.pdf';
  }

  async downloadPdfFile(fileId: string) {
    try {
      const isGridFs = /^[a-fA-F0-9]{24}$/.test(fileId);
      if (isGridFs) {
        const blob = await firstValueFrom(this.apiService.downloadFileFromGridFS(fileId));
        const urlBlob = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = urlBlob;
        const pdfFile = this.uploadedPdfFiles().find(f => f.fichierId === fileId);
        a.download = this.ensurePdfExtension(pdfFile?.filename || 'releve-bancaire');
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(urlBlob);
        document.body.removeChild(a);
        return;
      }

      // Toujours obtenir une URL signée fraîche depuis le backend pour Cloudinary
      const response = await firstValueFrom(this.apiService.getReleveFileUrl(fileId));
      const url = response.url;
      
      if (!url) {
        throw new Error('URL non disponible');
      }
      
      const pdfFile = this.uploadedPdfFiles().find(f => f.fichierId === fileId);
      const filename = this.ensurePdfExtension(pdfFile?.filename || 'releve-bancaire');
      
      // Pour Cloudinary, on doit télécharger via fetch pour pouvoir définir le nom
      const blobResponse = await fetch(url);
      const blob = await blobResponse.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(blobUrl);
      document.body.removeChild(a);
      
      this.store.showToast('Téléchargement démarré', 'success');
    } catch (error) {
      console.error('Erreur téléchargement PDF:', error);
      this.store.showToast('Erreur lors du téléchargement', 'error');
    }
  }

  async loadUploadedPdfFiles() {
    try {
      // Charger tous les fichiers PDF sans filtre par mois/année
      // pour afficher tous les relevés stockés
      const files = await firstValueFrom(this.releveService.getPdfFiles());
      if (files) {
        // Trier par date d'upload décroissante (plus récents en premier)
        const sortedFiles = files
          .map(f => ({
            id: f.id,
            filename: f.nomFichier,
            fichierId: f.fichierId,
            url: f.url,
            mois: f.mois,
            annee: f.annee,
            uploadedAt: f.uploadedAt
          }))
          .sort((a, b) => {
            const dateA = new Date(a.uploadedAt).getTime();
            const dateB = new Date(b.uploadedAt).getTime();
            return dateB - dateA; // Plus récent en premier
          });
        this.uploadedPdfFiles.set(sortedFiles);
      }
    } catch (error) {
      console.error('Erreur chargement fichiers PDF:', error);
    }
  }
}


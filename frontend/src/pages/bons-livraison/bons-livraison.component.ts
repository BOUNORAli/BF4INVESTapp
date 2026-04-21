import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { StoreService } from '../../services/store.service';
import { InvoiceService } from '../../services/invoice.service';
import { PartnerStore } from '../../stores/partner.store';
import { BCStore } from '../../stores/bc.store';
import type { Invoice, SaleInvoiceLinePayload } from '../../models/types';

interface LineDraft {
  produitRef: string;
  designation: string;
  unite: string;
  quantiteVendue: number;
  prixVenteUnitaireHT: number;
  tva: number;
}

@Component({
  selector: 'app-bons-livraison',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule],
  template: `
    <div class="space-y-6 fade-in-up max-w-6xl mx-auto pb-10 px-4">
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800">Bons de livraison</h1>
          <p class="text-sm text-slate-500 mt-1">
            Créer un BL sans facture ; facturer plus tard (seul ou regroupé sur une facture).
          </p>
        </div>
        <button type="button" (click)="toggleForm()"
          class="px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow font-medium">
          {{ formOpen() ? 'Fermer le formulaire' : 'Nouveau bon de livraison' }}
        </button>
      </div>

      @if (formOpen()) {
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4">
          <h2 class="font-semibold text-slate-800">Nouveau BL</h2>
          <div class="space-y-4">
            <div [formGroup]="blForm" class="space-y-4">
            <div class="grid md:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">Client *</label>
                <select formControlName="partnerId" class="w-full border rounded-lg px-3 py-2 text-sm">
                  <option value="">— Choisir —</option>
                  @for (c of store.clients(); track c.id) {
                    <option [value]="c.id">{{ c.name }}</option>
                  }
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-600 mb-1">Date du BL *</label>
                <input type="date" formControlName="dateBonLivraison" class="w-full border rounded-lg px-3 py-2 text-sm" />
              </div>
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-600 mb-1">BC (optionnel)</label>
              <select formControlName="bcId" class="w-full border rounded-lg px-3 py-2 text-sm">
                <option value="">— Aucune —</option>
                @for (bc of store.bcs(); track bc.id) {
                  <option [value]="bc.id">{{ bc.number || bc.id }}</option>
                }
              </select>
            </div>
            </div>
            <div class="border rounded-lg overflow-hidden">
              <table class="w-full text-sm">
                <thead class="bg-slate-50 text-xs uppercase text-slate-600">
                  <tr>
                    <th class="px-2 py-2 text-left">Réf.</th>
                    <th class="px-2 py-2 text-left">Désignation</th>
                    <th class="px-2 py-2 w-16">Qté</th>
                    <th class="px-2 py-2 w-24">PU HT</th>
                    <th class="px-2 py-2 w-16">TVA %</th>
                    <th class="px-2 py-2 w-10"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of lineDrafts(); track $index; let i = $index) {
                    <tr class="border-t">
                      <td class="px-2 py-1"><input [(ngModel)]="row.produitRef" [ngModelOptions]="{standalone: true}" class="w-full border rounded px-1 py-1 text-xs" /></td>
                      <td class="px-2 py-1"><input [(ngModel)]="row.designation" [ngModelOptions]="{standalone: true}" class="w-full border rounded px-1 py-1 text-xs" /></td>
                      <td class="px-2 py-1"><input type="number" [(ngModel)]="row.quantiteVendue" [ngModelOptions]="{standalone: true}" class="w-full border rounded px-1 py-1 text-xs" /></td>
                      <td class="px-2 py-1"><input type="number" [(ngModel)]="row.prixVenteUnitaireHT" [ngModelOptions]="{standalone: true}" class="w-full border rounded px-1 py-1 text-xs" /></td>
                      <td class="px-2 py-1"><input type="number" [(ngModel)]="row.tva" [ngModelOptions]="{standalone: true}" class="w-full border rounded px-1 py-1 text-xs" /></td>
                      <td class="px-1">
                        @if (lineDrafts().length > 1) {
                          <button type="button" (click)="removeLine(i)" class="text-red-500 text-xs">✕</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <button type="button" (click)="addLine()" class="text-sm text-blue-600 hover:underline">+ Ligne</button>
            <div class="flex gap-2">
              <button type="button" (click)="submitBl()" [disabled]="saving() || blForm.invalid" class="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm disabled:opacity-50">
                Enregistrer le BL
              </button>
            </div>
          </div>
        </div>
      }

      <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div class="p-4 border-b border-slate-100 flex flex-wrap gap-2 items-center justify-between bg-slate-50/50">
          <div class="flex gap-2 items-center">
            <button type="button" (click)="load()" class="text-sm px-3 py-1.5 border rounded-lg hover:bg-white">Actualiser</button>
            <button type="button" (click)="openGroupModal()"
              [disabled]="selectedIds().size < 2"
              class="text-sm px-3 py-1.5 bg-indigo-600 text-white rounded-lg disabled:opacity-40">
              Facturer la sélection ({{ selectedIds().size }})
            </button>
          </div>
          <span class="text-xs text-slate-500">{{ bls().length }} BL en attente</span>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-left min-w-[640px]">
            <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b">
              <tr>
                <th class="px-3 py-3 w-10"><input type="checkbox" [checked]="allSelected()" (change)="toggleSelectAll($event)" /></th>
                <th class="px-3 py-3">N° BL</th>
                <th class="px-3 py-3">Date</th>
                <th class="px-3 py-3">Client</th>
                <th class="px-3 py-3 text-right">TTC</th>
                <th class="px-3 py-3 text-center">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y">
              @for (bl of bls(); track bl.id) {
                <tr class="hover:bg-slate-50">
                  <td class="px-3 py-3">
                    <input type="checkbox" [checked]="selectedIds().has(bl.id)" (change)="toggleOne(bl.id, $event)" />
                  </td>
                  <td class="px-3 py-3 font-mono font-semibold">{{ bl.numeroBonLivraison || bl.number }}</td>
                  <td class="px-3 py-3 text-slate-600">{{ bl.dateBonLivraison || bl.date }}</td>
                  <td class="px-3 py-3">{{ store.getClientName(bl.partnerId || '') }}</td>
                  <td class="px-3 py-3 text-right font-medium">{{ bl.amountTTC | number:'1.2-2' }} MAD</td>
                  <td class="px-3 py-3 text-center space-x-1">
                    <button type="button" (click)="exportPdf(bl)" class="text-xs px-2 py-1 border rounded hover:bg-slate-50">PDF</button>
                    <button type="button" (click)="openSingleModal(bl)" class="text-xs px-2 py-1 bg-blue-600 text-white rounded">Facturer</button>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="px-6 py-8 text-center text-slate-500">Aucun bon de livraison en attente.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      @if (dateModalOpen()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" (click)="closeDateModal()">
          <div class="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4" (click)="$event.stopPropagation()">
            <h3 class="font-semibold text-slate-800">{{ dateModalTitle() }}</h3>
            <div>
              <label class="block text-xs font-semibold text-slate-600 mb-1">Date de facture</label>
              <input type="date" [(ngModel)]="factureDateStr" class="w-full border rounded-lg px-3 py-2 text-sm" />
            </div>
            <div class="flex justify-end gap-2">
              <button type="button" (click)="closeDateModal()" class="px-3 py-2 text-sm border rounded-lg">Annuler</button>
              <button type="button" (click)="confirmFactureDate()" [disabled]="facturing()" class="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg disabled:opacity-50">Valider</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class BonsLivraisonComponent implements OnInit {
  readonly store = inject(StoreService);
  private readonly invoiceService = inject(InvoiceService);
  private readonly fb = inject(FormBuilder);
  private readonly partnerStore = inject(PartnerStore);
  private readonly bcStore = inject(BCStore);

  readonly bls = signal<Invoice[]>([]);
  readonly formOpen = signal(false);
  readonly saving = signal(false);
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly dateModalOpen = signal(false);
  readonly dateModalTitle = signal('');
  readonly facturing = signal(false);
  factureDateStr = new Date().toISOString().split('T')[0];
  private dateModalMode: 'single' | 'group' | null = null;
  private singleBlId: string | null = null;

  lineDrafts = signal<LineDraft[]>([
    { produitRef: '', designation: '', unite: 'U', quantiteVendue: 1, prixVenteUnitaireHT: 0, tva: 20 },
  ]);

  blForm: FormGroup = this.fb.group({
    partnerId: ['', Validators.required],
    dateBonLivraison: [new Date().toISOString().split('T')[0], Validators.required],
    bcId: [''],
  });

  ngOnInit(): void {
    void this.partnerStore.loadClients().catch(() => undefined);
    void this.bcStore.loadBCs().catch(() => undefined);
    void this.load();
  }

  allSelected(): boolean {
    const list = this.bls();
    if (!list.length) {
      return false;
    }
    return list.every(b => this.selectedIds().has(b.id));
  }

  toggleSelectAll(ev: Event): void {
    const checked = (ev.target as HTMLInputElement).checked;
    const next = new Set<string>();
    if (checked) {
      this.bls().forEach(b => next.add(b.id));
    }
    this.selectedIds.set(next);
  }

  toggleOne(id: string, ev: Event): void {
    const checked = (ev.target as HTMLInputElement).checked;
    const next = new Set(this.selectedIds());
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.selectedIds.set(next);
  }

  toggleForm(): void {
    this.formOpen.update(v => !v);
  }

  addLine(): void {
    this.lineDrafts.update(rows => [
      ...rows,
      { produitRef: '', designation: '', unite: 'U', quantiteVendue: 1, prixVenteUnitaireHT: 0, tva: 20 },
    ]);
  }

  removeLine(i: number): void {
    this.lineDrafts.update(rows => rows.filter((_, idx) => idx !== i));
  }

  async load(): Promise<void> {
    try {
      const rows = await this.invoiceService.getBonsLivraison();
      this.bls.set(rows);
      this.selectedIds.set(new Set());
    } catch {
      this.store.showToast('Erreur chargement des BL', 'error');
    }
  }

  async submitBl(): Promise<void> {
    if (this.blForm.invalid) {
      this.store.showToast('Client et date requis', 'error');
      return;
    }
    const lignes: SaleInvoiceLinePayload[] = this.lineDrafts()
      .filter(r => r.produitRef?.trim() && (r.quantiteVendue ?? 0) > 0)
      .map(r => ({
        produitRef: r.produitRef.trim(),
        designation: (r.designation || r.produitRef).trim(),
        unite: r.unite || 'U',
        quantiteVendue: Number(r.quantiteVendue),
        prixVenteUnitaireHT: Number(r.prixVenteUnitaireHT) || 0,
        tva: r.tva != null ? Number(r.tva) : 20,
      }));
    if (!lignes.length) {
      this.store.showToast('Ajoutez au moins une ligne valide (réf. + quantité)', 'error');
      return;
    }
    const v = this.blForm.value;
    this.saving.set(true);
    try {
      await this.invoiceService.addBonLivraison({
        clientId: v.partnerId,
        dateBonLivraison: v.dateBonLivraison,
        bandeCommandeId: v.bcId || undefined,
        allocationVenteMode: v.bcId ? 'LINES' : undefined,
        lignes,
      });
      this.store.showToast('Bon de livraison créé', 'success');
      this.blForm.patchValue({ bcId: '' });
      this.lineDrafts.set([
        { produitRef: '', designation: '', unite: 'U', quantiteVendue: 1, prixVenteUnitaireHT: 0, tva: 20 },
      ]);
      this.formOpen.set(false);
      await this.load();
      await this.store.loadInvoices();
    } catch {
      this.store.showToast('Erreur à la création du BL', 'error');
    } finally {
      this.saving.set(false);
    }
  }

  async exportPdf(bl: Invoice): Promise<void> {
    try {
      this.store.showToast('Génération PDF…', 'info');
      const blob = await this.invoiceService.downloadBonDeLivraisonPDF(bl.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${bl.numeroBonLivraison || 'BL'}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      this.store.showToast('Erreur PDF', 'error');
    }
  }

  openSingleModal(bl: Invoice): void {
    this.dateModalMode = 'single';
    this.singleBlId = bl.id;
    this.dateModalTitle.set('Facturer ce bon');
    this.factureDateStr = new Date().toISOString().split('T')[0];
    this.dateModalOpen.set(true);
  }

  openGroupModal(): void {
    if (this.selectedIds().size < 2) {
      return;
    }
    this.dateModalMode = 'group';
    this.singleBlId = null;
    this.dateModalTitle.set('Facturer ' + this.selectedIds().size + ' bons sur une facture');
    this.factureDateStr = new Date().toISOString().split('T')[0];
    this.dateModalOpen.set(true);
  }

  closeDateModal(): void {
    this.dateModalOpen.set(false);
    this.dateModalMode = null;
    this.singleBlId = null;
  }

  async confirmFactureDate(): Promise<void> {
    if (!this.factureDateStr) {
      return;
    }
    this.facturing.set(true);
    try {
      if (this.dateModalMode === 'single' && this.singleBlId) {
        await this.invoiceService.facturerBonLivraison(this.singleBlId, this.factureDateStr);
        this.store.showToast('Facture créée depuis le BL', 'success');
      } else if (this.dateModalMode === 'group') {
        const ids = Array.from(this.selectedIds());
        await this.invoiceService.facturerBonsLivraisonGroupes(ids, this.factureDateStr);
        this.store.showToast('Facture groupée créée', 'success');
      }
      this.closeDateModal();
      await this.load();
      await this.store.loadInvoices();
    } catch {
      this.store.showToast('Erreur facturation', 'error');
    } finally {
      this.facturing.set(false);
    }
  }
}

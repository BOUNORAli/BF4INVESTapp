import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { StoreService } from '../../services/store.service';
import type { BC, LineItem, LigneVente } from '../../services/store.service';
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

interface BcLineDraft {
  produitRef: string;
  designation: string;
  unite: string;
  prixVenteUnitaireHT: number;
  tva: number;
  qtyBc: number;
  qtyAlreadyDelivered: number;
  qtyMax: number;
  qtyToDeliver: number;
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
                <select formControlName="partnerId" (change)="rebuildRowsFromBc()" class="w-full border rounded-lg px-3 py-2 text-sm">
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
              <select formControlName="bcId" (change)="rebuildRowsFromBc()" class="w-full border rounded-lg px-3 py-2 text-sm">
                <option value="">— Aucune (saisie libre) —</option>
                @for (bc of store.bcs(); track bc.id) {
                  <option [value]="bc.id">{{ bc.number || bc.id }}</option>
                }
              </select>
            </div>
            </div>

            @if (fromBc()) {
              <div class="rounded-lg border border-indigo-100 bg-indigo-50/60 px-3 py-2 text-sm text-indigo-900">
                Lignes chargées depuis la BC <span class="font-semibold">{{ selectedBcLabel() }}</span>.
                Quantités pré-remplies = <strong>restant</strong> (BC − déjà livré sur ce client). Vous pouvez réduire pour un BL partiel.
              </div>
              @if (bcHint()) {
                <div class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{{ bcHint() }}</div>
              }
              <div class="flex flex-wrap gap-2 items-center">
                <button type="button" (click)="setAllQtyToMax()" class="text-xs px-3 py-1.5 bg-white border border-indigo-200 rounded-lg hover:bg-indigo-50 text-indigo-800 font-medium">
                  Tout livrer (max restant)
                </button>
              </div>
              <div class="border rounded-lg overflow-x-auto">
                <table class="w-full text-sm min-w-[720px]">
                  <thead class="bg-slate-50 text-xs uppercase text-slate-600">
                    <tr>
                      <th class="px-2 py-2 text-left">Réf.</th>
                      <th class="px-2 py-2 text-left">Désignation</th>
                      <th class="px-2 py-2 text-center">Unité</th>
                      <th class="px-2 py-2 text-right">PU HT</th>
                      <th class="px-2 py-2 text-right">TVA %</th>
                      <th class="px-2 py-2 text-right">Qté BC</th>
                      <th class="px-2 py-2 text-right">Déjà livré</th>
                      <th class="px-2 py-2 text-right">Restant</th>
                      <th class="px-2 py-2 text-right">Qté à livrer</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of bcRows(); track trackBcRow($index, row); let i = $index) {
                      <tr class="border-t">
                        <td class="px-2 py-1.5 font-mono text-xs">{{ row.produitRef }}</td>
                        <td class="px-2 py-1.5">{{ row.designation }}</td>
                        <td class="px-2 py-1.5 text-center text-xs">{{ row.unite }}</td>
                        <td class="px-2 py-1.5 text-right">{{ row.prixVenteUnitaireHT | number:'1.2-2' }}</td>
                        <td class="px-2 py-1.5 text-right">{{ row.tva | number:'1.0-2' }}</td>
                        <td class="px-2 py-1.5 text-right text-slate-600">{{ row.qtyBc | number:'1.2-2' }}</td>
                        <td class="px-2 py-1.5 text-right text-amber-800">{{ row.qtyAlreadyDelivered | number:'1.2-2' }}</td>
                        <td class="px-2 py-1.5 text-right font-semibold text-emerald-800">{{ row.qtyMax | number:'1.2-2' }}</td>
                        <td class="px-2 py-1.5 text-right w-28">
                          <input type="number" class="w-full border rounded px-1 py-1 text-xs text-right"
                            [value]="row.qtyToDeliver"
                            (input)="onBcQtyInput(i, $any($event.target).value)" step="0.01" min="0" [attr.max]="row.qtyMax" />
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
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
            }

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

  readonly fromBc = signal(false);
  readonly bcRows = signal<BcLineDraft[]>([]);
  readonly bcHint = signal<string | null>(null);
  readonly selectedBcLabel = signal('');

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
    void this.store.loadInvoices().catch(() => undefined);
    void this.load();
  }

  trackBcRow(i: number, row: BcLineDraft): string {
    return `${row.produitRef}|${row.prixVenteUnitaireHT}|${row.tva}|${i}`;
  }

  private lineKey(ref: string, prixHt: number, tvaPct: number): string {
    return `${(ref || '').trim()}|${Number(prixHt)}|${Number(tvaPct)}`;
  }

  private tvaPercentFromLineItem(it: LineItem): number {
    const r = it.tvaRate;
    if (r == null || Number.isNaN(Number(r))) {
      return 20;
    }
    const n = Number(r);
    return n > 1 ? n : Math.round(n * 10000) / 100;
  }

  /**
   * Quantités déjà livrées / facturées pour ce BC + client (hors avoirs, hors facture agrégée issue de BL).
   */
  private computeDeliveredByLineKey(bcId: string, partnerId: string): Map<string, number> {
    const m = new Map<string, number>();
    const invoices = this.store.invoices();
    for (const inv of invoices) {
      if (inv.type !== 'sale' || inv.estAvoir) {
        continue;
      }
      if ((inv.bcId || '') !== bcId || (inv.partnerId || '') !== partnerId) {
        continue;
      }
      if (inv.bonLivraisonSourceIds && inv.bonLivraisonSourceIds.length > 0) {
        continue;
      }
      if (inv.statut === 'MERGE_DANS_FV') {
        continue;
      }
      for (const l of inv.lignes || []) {
        const ref = (l.produitRef || '').trim();
        const prix = Number(l.prixVenteUnitaireHT) || 0;
        const tva = l.tva != null ? Number(l.tva) : 20;
        const key = this.lineKey(ref, prix, tva);
        const q = Number(l.quantiteVendue) || 0;
        m.set(key, (m.get(key) || 0) + q);
      }
    }
    return m;
  }

  rebuildRowsFromBc(): void {
    this.bcHint.set(null);
    const partnerId = (this.blForm.get('partnerId')?.value || '').toString().trim();
    const bcId = (this.blForm.get('bcId')?.value || '').toString().trim();
    if (!partnerId || !bcId) {
      this.fromBc.set(false);
      this.bcRows.set([]);
      this.selectedBcLabel.set('');
      return;
    }
    const bc = this.store.bcs().find((b: BC) => b.id === bcId);
    if (!bc) {
      this.fromBc.set(false);
      this.bcRows.set([]);
      this.selectedBcLabel.set('');
      return;
    }
    this.selectedBcLabel.set(bc.number || bc.id);

    const aggs = new Map<
      string,
      { produitRef: string; designation: string; unite: string; prixVenteUnitaireHT: number; tva: number; qtyBc: number }
    >();

    const blocks = (bc.clientsVente || []).filter(cv => cv.clientId === partnerId);
    if (blocks.length > 0) {
      for (const cv of blocks) {
        for (const lv of cv.lignesVente || []) {
          this.mergeLigneVenteIntoAggs(aggs, lv);
        }
      }
    } else if (bc.clientId === partnerId && bc.items && bc.items.length > 0) {
      for (const it of bc.items) {
        const ref = (it.ref || '').trim();
        const prix = Number(it.priceSellHT) || 0;
        const tva = this.tvaPercentFromLineItem(it);
        const key = this.lineKey(ref, prix, tva);
        const q = Number(it.qtySell) || 0;
        const cur = aggs.get(key);
        if (!cur) {
          aggs.set(key, {
            produitRef: ref,
            designation: it.name || ref,
            unite: it.unit || 'U',
            prixVenteUnitaireHT: prix,
            tva,
            qtyBc: q,
          });
        } else {
          cur.qtyBc += q;
        }
      }
    }

    if (aggs.size === 0) {
      this.fromBc.set(false);
      this.bcRows.set([]);
      this.bcHint.set('Ce client n’a pas de lignes de vente dans cette BC (structure multi-client ou BC sans lignes).');
      return;
    }

    const delivered = this.computeDeliveredByLineKey(bcId, partnerId);
    const rows: BcLineDraft[] = [];
    for (const [, v] of aggs) {
      const key = this.lineKey(v.produitRef, v.prixVenteUnitaireHT, v.tva);
      const qtyAlready = delivered.get(key) || 0;
      const qtyMax = Math.max(0, Number((v.qtyBc - qtyAlready).toFixed(4)));
      rows.push({
        produitRef: v.produitRef,
        designation: v.designation,
        unite: v.unite,
        prixVenteUnitaireHT: v.prixVenteUnitaireHT,
        tva: v.tva,
        qtyBc: v.qtyBc,
        qtyAlreadyDelivered: qtyAlready,
        qtyMax,
        qtyToDeliver: qtyMax,
      });
    }
    this.fromBc.set(true);
    this.bcRows.set(rows);
  }

  private mergeLigneVenteIntoAggs(
    aggs: Map<string, { produitRef: string; designation: string; unite: string; prixVenteUnitaireHT: number; tva: number; qtyBc: number }>,
    lv: LigneVente
  ): void {
    const ref = (lv.produitRef || '').trim();
    const prix = Number(lv.prixVenteUnitaireHT) || 0;
    const tva = lv.tva != null && lv.tva !== undefined ? Number(lv.tva) : 20;
    const key = this.lineKey(ref, prix, tva);
    const q = Number(lv.quantiteVendue) || 0;
    const cur = aggs.get(key);
    if (!cur) {
      aggs.set(key, {
        produitRef: ref,
        designation: (lv.designation || ref).trim(),
        unite: lv.unite || 'U',
        prixVenteUnitaireHT: prix,
        tva,
        qtyBc: q,
      });
    } else {
      cur.qtyBc += q;
    }
  }

  setAllQtyToMax(): void {
    this.bcRows.update(rows => rows.map(r => ({ ...r, qtyToDeliver: r.qtyMax })));
  }

  onBcQtyInput(index: number, raw: string): void {
    const rows = this.bcRows();
    if (!rows[index]) {
      return;
    }
    const max = rows[index].qtyMax;
    const parsed = parseFloat(String(raw).replace(',', '.'));
    const q = Number.isFinite(parsed) ? Math.max(0, Math.min(parsed, max)) : 0;
    this.bcRows.update(arr => {
      const copy = [...arr];
      if (copy[index]) {
        copy[index] = { ...copy[index], qtyToDeliver: q };
      }
      return copy;
    });
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
    const next = !this.formOpen();
    this.formOpen.set(next);
    if (next) {
      void this.store.loadInvoices().then(() => this.rebuildRowsFromBc()).catch(() => this.rebuildRowsFromBc());
    }
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
    const v = this.blForm.value;
    let lignes: SaleInvoiceLinePayload[];

    if (this.fromBc() && v.bcId) {
      lignes = this.bcRows()
        .filter(r => (r.qtyToDeliver ?? 0) > 0)
        .map(r => ({
          produitRef: r.produitRef,
          designation: r.designation || r.produitRef,
          unite: r.unite || 'U',
          quantiteVendue: Number(r.qtyToDeliver),
          prixVenteUnitaireHT: Number(r.prixVenteUnitaireHT) || 0,
          tva: r.tva != null ? Number(r.tva) : 20,
        }));
      if (!lignes.length) {
        this.store.showToast('Indiquez au moins une quantité à livrer (> 0)', 'error');
        return;
      }
    } else {
      lignes = this.lineDrafts()
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
    }

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
      this.fromBc.set(false);
      this.bcRows.set([]);
      this.bcHint.set(null);
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

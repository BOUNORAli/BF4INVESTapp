import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10 max-w-3xl mx-auto">
      <div>
        <h1 class="text-2xl font-bold text-slate-800 font-display">Paramètres</h1>
        <p class="text-sm text-slate-500 mt-1">Configuration générale de l'application.</p>
      </div>

      <!-- Payment Modes Card -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-center">
          <div>
            <h2 class="text-lg font-bold text-slate-800">Modes de Paiement</h2>
            <p class="text-xs text-slate-500">Gérez les méthodes acceptées pour les factures.</p>
          </div>
        </div>
        
        <div class="p-6">
          <!-- Add New -->
           <div class="bg-slate-50 p-6 rounded-xl border border-slate-200 mb-8">
            <h3 class="text-base font-bold text-slate-700 mb-1">Ajouter un mode</h3>
            <p class="text-sm text-slate-500 mb-4">Le nouveau mode sera immédiatement disponible dans les formulaires.</p>
            <div class="flex gap-3">
              <input type="text" [(ngModel)]="newModeName" placeholder="Nouveau mode (ex: PayPal)" 
                     class="flex-1 px-4 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500/20 outline-none transition"
                     (keyup.enter)="addMode()">
              <button (click)="addMode()" [disabled]="!newModeName()" class="px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-bold hover:bg-blue-700 transition disabled:opacity-50">
                Ajouter
              </button>
            </div>
          </div>

          <!-- List -->
          <div class="space-y-3">
            @for (mode of store.paymentModes(); track mode.id) {
              <div class="flex items-center justify-between p-3 border border-slate-100 rounded-lg hover:bg-slate-50 transition group bg-white">
                <div class="flex items-center gap-3">
                  <div class="w-8 h-8 rounded-full flex items-center justify-center bg-slate-100 text-slate-500">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
                  </div>
                  <span class="font-medium text-slate-700" [class.line-through]="!mode.active" [class.text-slate-400]="!mode.active">{{ mode.name }}</span>
                  @if (!mode.active) {
                    <span class="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">Inactif</span>
                  }
                </div>
                <div class="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button (click)="store.togglePaymentMode(mode.id)" class="text-xs font-medium px-2 py-1 rounded transition-colors"
                    [class]="mode.active ? 'text-amber-600 hover:bg-amber-50' : 'text-emerald-600 hover:bg-emerald-50'">
                    {{ mode.active ? 'Désactiver' : 'Activer' }}
                  </button>
                  <button (click)="store.deletePaymentMode(mode.id)" class="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                  </button>
                </div>
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `
})
export class SettingsComponent {
  store = inject(StoreService);
  newModeName = signal('');

  addMode() {
    if (this.newModeName().trim()) {
      this.store.addPaymentMode(this.newModeName().trim());
      this.newModeName.set('');
    }
  }
}

import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService, ApiUser, CreateUserPayload, UpdateUserPayload, UserRole } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';

const ROLES: UserRole[] = ['ADMIN', 'COMMERCIAL', 'COMPTABLE', 'LECTEUR'];

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10 max-w-4xl mx-auto">
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Utilisateurs</h1>
          <p class="text-sm text-slate-500 mt-1">Gérez les comptes et les rôles d'accès.</p>
        </div>
        <button (click)="openCreate()" class="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center gap-2">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg>
          Nouvel utilisateur
        </button>
      </div>

      @if (loading()) {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-8 text-center text-slate-500">Chargement...</div>
      } @else {
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead class="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nom</th>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Email</th>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Rôle</th>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Statut</th>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Créé le</th>
                  <th class="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider w-24">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (u of users(); track u.id) {
                  <tr class="hover:bg-slate-50/50 transition">
                    <td class="px-6 py-4 font-medium text-slate-800">{{ u.name }}</td>
                    <td class="px-6 py-4 text-slate-600">{{ u.email }}</td>
                    <td class="px-6 py-4">
                      <span class="inline-flex px-2 py-0.5 text-xs font-medium rounded-full" [ngClass]="getRoleClass(u.role)">{{ u.role }}</span>
                    </td>
                    <td class="px-6 py-4">
                      @if (u.enabled) {
                        <span class="text-emerald-600 text-sm font-medium">Actif</span>
                      } @else {
                        <span class="text-slate-400 text-sm">Inactif</span>
                      }
                    </td>
                    <td class="px-6 py-4 text-slate-500 text-sm">{{ u.createdAt ? (u.createdAt | date:'dd/MM/yyyy') : '—' }}</td>
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-2">
                        <button (click)="openEdit(u)" class="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded transition" title="Modifier">
                          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg>
                        </button>
                        @if (u.email !== auth.currentUser()?.email) {
                          <button (click)="confirmDelete(u)" class="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition" title="Supprimer">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (users().length === 0) {
            <div class="p-8 text-center text-slate-500">Aucun utilisateur.</div>
          }
        </div>
      }

      <!-- Modal Créer / Modifier -->
      @if (showModal()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center p-4" aria-modal="true">
          <div (click)="closeModal()" class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-xl shadow-xl border border-slate-200 w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div class="sticky top-0 bg-white border-b border-slate-100 px-6 py-4 flex justify-between items-center">
              <h2 class="text-lg font-bold text-slate-800">{{ modalTitle() }}</h2>
              <button (click)="closeModal()" class="text-slate-400 hover:text-slate-600 transition">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>
            <form (ngSubmit)="submitUser()" class="p-6 space-y-4">
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Nom</label>
                <input type="text" [(ngModel)]="formName" name="formName" required
                  class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition">
              </div>
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Email</label>
                <input type="email" [(ngModel)]="formEmail" name="formEmail" required
                  [readonly]="!!editingId()"
                  class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition" [class.bg-slate-50]="!!editingId()">
              </div>
              <div>
                <label class="block text-sm font-semibold text-slate-700 mb-1">Rôle</label>
                <select [(ngModel)]="formRole" name="formRole" class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition bg-white">
                  @for (r of roles; track r) {
                    <option [value]="r">{{ r }}</option>
                  }
                </select>
              </div>
              @if (!editingId()) {
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Mot de passe</label>
                  <input type="password" [(ngModel)]="formPassword" name="formPassword" [required]="!editingId()" minlength="6"
                    class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition"
                    placeholder="Min. 6 caractères">
                </div>
              } @else {
                <div>
                  <label class="block text-sm font-semibold text-slate-700 mb-1">Nouveau mot de passe (optionnel)</label>
                  <input type="password" [(ngModel)]="formPassword" name="formPassword"
                    class="w-full px-4 py-2 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition"
                    placeholder="Laisser vide pour ne pas modifier">
                </div>
              }
              @if (editingId()) {
                <div class="flex items-center gap-2">
                  <input type="checkbox" [(ngModel)]="formEnabled" name="formEnabled" id="formEnabled" class="rounded border-slate-300">
                  <label for="formEnabled" class="text-sm font-medium text-slate-700">Compte actif</label>
                </div>
              }
              @if (modalError()) {
                <p class="text-sm text-red-600">{{ modalError() }}</p>
              }
              <div class="flex gap-3 pt-2">
                <button type="button" (click)="closeModal()" class="flex-1 px-4 py-2 border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 font-medium transition">Annuler</button>
                <button type="submit" class="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium transition">Enregistrer</button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Modal Confirmation suppression -->
      @if (userToDelete()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center p-4" aria-modal="true">
          <div (click)="userToDelete.set(null)" class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
          <div class="relative bg-white rounded-xl shadow-xl border border-slate-200 w-full max-w-sm p-6">
            <p class="text-slate-800 font-medium mb-4">Supprimer l'utilisateur <strong>{{ userToDelete()!.name }}</strong> ({{ userToDelete()!.email }}) ?</p>
            <div class="flex gap-3">
              <button (click)="userToDelete.set(null)" class="flex-1 px-4 py-2 border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 font-medium transition">Annuler</button>
              <button (click)="doDelete()" class="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 font-medium transition">Supprimer</button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class UsersComponent implements OnInit {
  private userService = inject(UserService);
  auth = inject(AuthService);

  users = signal<ApiUser[]>([]);
  loading = signal(true);
  showModal = signal(false);
  editingId = signal<string | null>(null);
  userToDelete = signal<ApiUser | null>(null);
  modalError = signal<string | null>(null);

  formName = '';
  formEmail = '';
  formPassword = '';
  formRole: UserRole = 'COMMERCIAL';
  formEnabled = true;

  readonly roles = ROLES;
  readonly modalTitle = computed(() => this.editingId() ? "Modifier l'utilisateur" : 'Nouvel utilisateur');

  ngOnInit() {
    this.loadUsers();
  }

  private loadUsers() {
    this.loading.set(true);
    this.userService.getUsers().subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  getRoleClass(role: UserRole): string {
    const map: Record<UserRole, string> = {
      ADMIN: 'bg-violet-100 text-violet-800',
      COMMERCIAL: 'bg-blue-100 text-blue-800',
      COMPTABLE: 'bg-amber-100 text-amber-800',
      LECTEUR: 'bg-slate-100 text-slate-700'
    };
    return map[role] ?? 'bg-slate-100 text-slate-600';
  }

  openCreate() {
    this.editingId.set(null);
    this.formName = '';
    this.formEmail = '';
    this.formPassword = '';
    this.formRole = 'COMMERCIAL';
    this.modalError.set(null);
    this.showModal.set(true);
  }

  openEdit(u: ApiUser) {
    this.editingId.set(u.id);
    this.formName = u.name;
    this.formEmail = u.email;
    this.formPassword = '';
    this.formRole = u.role;
    this.formEnabled = u.enabled;
    this.modalError.set(null);
    this.showModal.set(true);
  }

  closeModal() {
    this.showModal.set(false);
    this.editingId.set(null);
    this.modalError.set(null);
  }

  submitUser() {
    const id = this.editingId();
    if (id) {
      const payload: UpdateUserPayload = {
        name: this.formName.trim(),
        email: this.formEmail.trim(),
        role: this.formRole,
        enabled: this.formEnabled
      };
      if (this.formPassword.trim()) payload.password = this.formPassword;
      this.userService.updateUser(id, payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadUsers();
        },
        error: (err) => {
          this.modalError.set(err?.error?.message || 'Erreur lors de la mise à jour.');
        }
      });
    } else {
      if (!this.formPassword.trim() || this.formPassword.length < 6) {
        this.modalError.set('Le mot de passe doit contenir au moins 6 caractères.');
        return;
      }
      const payload: CreateUserPayload = {
        name: this.formName.trim(),
        email: this.formEmail.trim(),
        password: this.formPassword,
        role: this.formRole
      };
      this.userService.createUser(payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadUsers();
        },
        error: (err) => {
          this.modalError.set(err?.error?.message || 'Erreur lors de la création.');
        }
      });
    }
  }

  confirmDelete(u: ApiUser) {
    this.userToDelete.set(u);
  }

  doDelete() {
    const u = this.userToDelete();
    if (!u) return;
    this.userService.deleteUser(u.id).subscribe({
      next: () => {
        this.userToDelete.set(null);
        this.loadUsers();
      },
      error: () => {
        this.userToDelete.set(null);
      }
    });
  }
}

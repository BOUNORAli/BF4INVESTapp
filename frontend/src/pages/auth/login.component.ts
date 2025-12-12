
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="min-h-screen bg-slate-900 flex items-center justify-center p-4 relative overflow-hidden">
      
      <!-- Background Effects -->
      <div class="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-600/20 rounded-full blur-[120px]"></div>
      <div class="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-indigo-600/20 rounded-full blur-[120px]"></div>

      <div class="bg-white/5 backdrop-blur-xl border border-white/10 p-8 rounded-2xl w-full max-w-md shadow-2xl relative z-10 fade-in-up">
        
        <div class="text-center mb-8">
          <div class="w-16 h-16 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl mx-auto flex items-center justify-center shadow-lg shadow-blue-500/30 mb-4">
            <span class="text-3xl font-bold text-white">B</span>
          </div>
          <h1 class="text-2xl font-bold text-white font-display">Bienvenue</h1>
          <p class="text-slate-400 mt-2 text-sm">Connectez-vous à BF4 INVEST Manager</p>
        </div>

        <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="space-y-5">
          <div>
            <label class="block text-xs font-semibold text-slate-400 uppercase mb-2">Email Professionnel</label>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 12a4 4 0 10-8 0 4 4 0 008 0zm0 0v1.5a2.5 2.5 0 005 0V12a9 9 0 10-9 9m4.5-1.206a8.959 8.959 0 01-4.5 1.207"></path></svg>
              </span>
              <input type="email" formControlName="email" 
                class="w-full pl-10 pr-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
                placeholder="admin@bf4.com">
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-400 uppercase mb-2">Mot de passe</label>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
              </span>
              <input type="password" formControlName="password" 
                class="w-full pl-10 pr-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all" 
                placeholder="••••••••">
            </div>
          </div>

          <button type="submit" [disabled]="loginForm.invalid" 
            class="w-full py-3.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold rounded-xl shadow-lg shadow-blue-600/20 transition-all transform hover:scale-[1.02] disabled:opacity-50 disabled:cursor-not-allowed">
            Se connecter
          </button>
        </form>

        <div class="mt-6 text-center">
          <p class="text-xs text-slate-500">
            Mot de passe oublié ? <a href="#" class="text-blue-400 hover:text-blue-300 transition-colors">Contactez le support</a>
          </p>
        </div>
        
        <!-- Quick Fill for Demo -->
        <div class="mt-8 pt-6 border-t border-white/5 text-center">
          <p class="text-xs text-slate-600 mb-2">Compte de démonstration</p>
          <button type="button" (click)="fillDemo()" class="text-xs bg-white/5 hover:bg-white/10 text-slate-300 px-3 py-1.5 rounded-full transition-colors border border-white/5">
            Remplir Admin
          </button>
        </div>

      </div>
    </div>
  `
})
export class LoginComponent {
  fb = inject(FormBuilder);
  auth = inject(AuthService);
  store = inject(StoreService);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  onSubmit() {
    if (this.loginForm.valid) {
      const { email, password } = this.loginForm.value;
      const success = this.auth.login(email!, password!);
      if (success) {
        this.store.showToast('Connexion réussie', 'success');
      } else {
        this.store.showToast('Identifiants incorrects', 'error');
      }
    }
  }

  fillDemo() {
    this.loginForm.setValue({
      email: 'admin@bf4.com',
      password: 'password123'
    });
  }
}


import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { StoreService } from '../../services/store.service';
import { getApiBaseUrlDynamic, getPublicSiteUrlDynamic } from '../../config/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-sky-100 via-blue-50 to-indigo-50 flex items-center justify-center p-4 relative overflow-hidden">
      
      <!-- Nuages subtils en arrière-plan -->
      <div class="absolute top-20 left-10 w-32 h-20 bg-white/30 rounded-full blur-2xl opacity-60"></div>
      <div class="absolute top-40 right-20 w-40 h-24 bg-white/25 rounded-full blur-3xl opacity-50"></div>
      <div class="absolute bottom-32 left-1/4 w-36 h-22 bg-white/20 rounded-full blur-2xl opacity-40"></div>
      <div class="absolute bottom-20 right-1/3 w-28 h-18 bg-white/30 rounded-full blur-2xl opacity-50"></div>

      <!-- Lignes abstraites subtiles -->
      <svg class="absolute inset-0 w-full h-full opacity-10" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#0059B3" stroke-width="0.5"/>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#grid)"/>
        <path d="M0,200 Q200,100 400,200 T800,200" stroke="#0059B3" stroke-width="1" fill="none" opacity="0.3"/>
        <path d="M0,400 Q300,300 600,400 T1200,400" stroke="#2563EB" stroke-width="1" fill="none" opacity="0.2"/>
      </svg>

      <!-- Carte centrale avec gradient et glassmorphism -->
      <div class="bg-gradient-to-b from-blue-50/90 via-white/95 to-white backdrop-blur-xl border border-white/50 p-10 rounded-3xl w-full max-w-md shadow-2xl relative z-10 fade-in-up">
        <!-- Lien retour vers le site public -->
        <div class="mb-4 flex justify-center">
          <a [href]="publicSiteUrl" target="_blank" rel="noopener"
             class="text-xs text-slate-500 hover:text-[#0059B3] transition-colors flex items-center gap-1">
            <span class="text-base leading-none">←</span>
            <span>Retour au site public</span>
          </a>
        </div>

        <!-- Header avec logo et titre -->
        <div class="text-center mb-8">
          <div class="w-32 h-32 mx-auto mb-6 flex items-center justify-center">
            <img [src]="logoUrl" alt="BF4 Invest Logo" class="h-full w-auto object-contain max-w-full"
                 onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
            <div class="w-32 h-32 bg-gradient-to-br from-[#0059B3] to-[#2563EB] rounded-2xl flex items-center justify-center shadow-lg shadow-blue-500/30" style="display: none;">
              <span class="text-5xl font-bold text-white">B</span>
            </div>
          </div>
          <h1 class="text-3xl font-bold text-slate-800 font-display mb-2">Se connecter</h1>
          <p class="text-slate-600 text-sm leading-relaxed">
            Gérez vos commandes, factures et trésorerie<br/>en toute simplicité avec BF4 Invest Manager
          </p>
        </div>

        <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="space-y-6">
          <!-- Champ Email -->
          <div>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path>
                </svg>
              </span>
              <input type="email" formControlName="email" 
                class="w-full pl-12 pr-4 py-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0059B3] focus:border-transparent transition-all text-base" 
                placeholder="Email">
            </div>
            @if (loginForm.get('email')?.invalid && loginForm.get('email')?.touched) {
              <p class="text-xs text-red-500 mt-1.5 ml-1">Veuillez entrer une adresse email valide</p>
            }
          </div>

          <!-- Champ Mot de passe -->
          <div>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
                </svg>
              </span>
              <input [type]="showPassword() ? 'text' : 'password'" formControlName="password" 
                class="w-full pl-12 pr-12 py-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0059B3] focus:border-transparent transition-all text-base" 
                placeholder="Mot de passe">
              <button type="button" (click)="togglePasswordVisibility()" 
                class="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-slate-600 transition-colors">
                @if (showPassword()) {
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"></path>
                  </svg>
                } @else {
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                  </svg>
                }
              </button>
            </div>
            @if (loginForm.get('password')?.invalid && loginForm.get('password')?.touched) {
              <p class="text-xs text-red-500 mt-1.5 ml-1">Le mot de passe est requis</p>
            }
          </div>

          <!-- Lien Mot de passe oublié -->
          <div class="flex justify-end">
            <a href="#" class="text-sm text-slate-500 hover:text-[#0059B3] transition-colors">
              Mot de passe oublié ?
            </a>
          </div>

          <!-- Bouton de connexion -->
          <button type="submit" [disabled]="loginForm.invalid" 
            class="w-full py-4 bg-slate-800 hover:bg-slate-900 text-white font-semibold rounded-xl shadow-lg shadow-slate-800/20 transition-all transform hover:scale-[1.01] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100 text-base">
            Se connecter
          </button>
        </form>

      </div>
    </div>
  `
})
export class LoginComponent {
  fb = inject(FormBuilder);
  auth = inject(AuthService);
  store = inject(StoreService);

  showPassword = signal(false);
  
  // URL du logo depuis le backend
  logoUrl = getApiBaseUrlDynamic() + '/settings/logo';
  // URL du site public (vitrine)
  publicSiteUrl = getPublicSiteUrlDynamic();

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  togglePasswordVisibility() {
    this.showPassword.update(value => !value);
  }

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

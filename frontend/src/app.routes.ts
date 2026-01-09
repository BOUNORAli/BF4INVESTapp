
import { Routes, CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './services/auth.service';

const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  
  if (auth.isAuthenticated()) {
    return true;
  }
  
  return router.createUrlTree(['/login']);
};

export const routes: Routes = [
  { 
    path: 'login', 
    loadComponent: () => import('./pages/auth/login.component').then(m => m.LoginComponent)
  },
  { 
    path: '', 
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { 
        path: 'dashboard', 
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      { 
        path: 'bc', 
        loadComponent: () => import('./pages/bc/bc-list.component').then(m => m.BcListComponent)
      },
      { 
        path: 'bc/new', 
        loadComponent: () => import('./pages/bc/bc-form.component').then(m => m.BcFormComponent)
      },
      { 
        path: 'bc/edit/:id', 
        loadComponent: () => import('./pages/bc/bc-form.component').then(m => m.BcFormComponent)
      },
      { 
        path: 'invoices/purchase', 
        loadComponent: () => import('./pages/invoices/purchase-invoices.component').then(m => m.PurchaseInvoicesComponent)
      },
      { 
        path: 'invoices/sales', 
        loadComponent: () => import('./pages/invoices/sales-invoices.component').then(m => m.SalesInvoicesComponent)
      },
      { 
        path: 'products', 
        loadComponent: () => import('./pages/products/products.component').then(m => m.ProductsComponent)
      },
      { 
        path: 'clients', 
        loadComponent: () => import('./pages/partners/partners.component').then(m => m.PartnersComponent)
      },
      { 
        path: 'partners/situation/:type/:id', 
        loadComponent: () => import('./pages/partners/partner-situation.component').then(m => m.PartnerSituationComponent)
      },
      { 
        path: 'partners/situation/:type', 
        loadComponent: () => import('./pages/partners/partner-situation.component').then(m => m.PartnerSituationComponent)
      },
      { 
        path: 'import', 
        loadComponent: () => import('./pages/import/import.component').then(m => m.ImportComponent)
      },
      { 
        path: 'settings', 
        loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent)
      },
      { 
        path: 'search', 
        loadComponent: () => import('./pages/search/search.component').then(m => m.SearchComponent)
      },
      { 
        path: 'notifications', 
        loadComponent: () => import('./pages/notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      { 
        path: 'audit', 
        loadComponent: () => import('./pages/audit/audit.component').then(m => m.AuditComponent)
      },
      { 
        path: 'prevision', 
        loadComponent: () => import('./pages/prevision/prevision-tresorerie.component').then(m => m.PrevisionTresorerieComponent)
      },
      { 
        path: 'charges', 
        loadComponent: () => import('./pages/charges/charges.component').then(m => m.ChargesComponent)
      },
      { 
        path: 'historique-tresorerie', 
        loadComponent: () => import('./pages/historique-tresorerie/historique-tresorerie.component').then(m => m.HistoriqueTresorerieComponent)
      },
      { 
        path: 'ordres-virement', 
        loadComponent: () => import('./pages/ordres-virement/ordres-virement.component').then(m => m.OrdresVirementComponent)
      },
      { 
        path: 'tva', 
        loadComponent: () => import('./pages/tva/tva.component').then(m => m.TVAComponent)
      },
      { 
        path: 'comptabilite', 
        loadComponent: () => import('./pages/comptabilite/comptabilite.component').then(m => m.ComptabiliteComponent)
      },
      { 
        path: 'is', 
        loadComponent: () => import('./pages/is/is.component').then(m => m.ISComponent)
      },
      { 
        path: 'releve-bancaire', 
        loadComponent: () => import('./pages/releve-bancaire/releve-bancaire.component').then(m => m.ReleveBancaireComponent)
      }
    ]
  },
  { path: '**', redirectTo: '/dashboard', pathMatch: 'full' }
];

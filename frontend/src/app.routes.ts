
import { Routes, CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './services/auth.service';

import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { BcListComponent } from './pages/bc/bc-list.component';
import { BcFormComponent } from './pages/bc/bc-form.component';
import { PurchaseInvoicesComponent } from './pages/invoices/purchase-invoices.component';
import { SalesInvoicesComponent } from './pages/invoices/sales-invoices.component';
import { ProductsComponent } from './pages/products/products.component';
import { PartnersComponent } from './pages/partners/partners.component';
import { ImportComponent } from './pages/import/import.component';
import { LoginComponent } from './pages/auth/login.component';
import { SettingsComponent } from './pages/settings/settings.component';
import { SearchComponent } from './pages/search/search.component';
import { NotificationsComponent } from './pages/notifications/notifications.component';
import { AuditComponent } from './pages/audit/audit.component';
import { PrevisionTresorerieComponent } from './pages/prevision/prevision-tresorerie.component';
import { OrdresVirementComponent } from './pages/ordres-virement/ordres-virement.component';
import { HistoriqueTresorerieComponent } from './pages/historique-tresorerie/historique-tresorerie.component';
import { ChargesComponent } from './pages/charges/charges.component';
import { TVAComponent } from './pages/tva/tva.component';
import { ComptabiliteComponent } from './pages/comptabilite/comptabilite.component';
import { ISComponent } from './pages/is/is.component';

const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  
  if (auth.isAuthenticated()) {
    return true;
  }
  
  return router.createUrlTree(['/login']);
};

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { 
    path: '', 
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'bc', component: BcListComponent },
      { path: 'bc/new', component: BcFormComponent },
      { path: 'bc/edit/:id', component: BcFormComponent },
      { path: 'invoices/purchase', component: PurchaseInvoicesComponent },
      { path: 'invoices/sales', component: SalesInvoicesComponent },
      { path: 'products', component: ProductsComponent },
      { path: 'clients', component: PartnersComponent },
      { path: 'import', component: ImportComponent },
      { path: 'settings', component: SettingsComponent },
      { path: 'search', component: SearchComponent },
      { path: 'notifications', component: NotificationsComponent },
      { path: 'audit', component: AuditComponent },
      { path: 'prevision', component: PrevisionTresorerieComponent },
      { path: 'charges', component: ChargesComponent },
      { path: 'historique-tresorerie', component: HistoriqueTresorerieComponent },
      { path: 'ordres-virement', component: OrdresVirementComponent },
      { path: 'tva', component: TVAComponent },
      { path: 'comptabilite', component: ComptabiliteComponent },
      { path: 'is', component: ISComponent },
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];

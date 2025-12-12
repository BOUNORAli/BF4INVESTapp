import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { StoreService, Notification } from '../../services/store.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6 fade-in-up pb-10">
      
      <!-- Header -->
      <div class="flex flex-col md:flex-row justify-between items-start md:items-center pb-4 border-b border-slate-200 gap-4">
        <div>
          <h1 class="text-2xl font-bold text-slate-800 font-display">Historique des Notifications</h1>
          <p class="text-sm text-slate-500 mt-1">Consultez toutes vos notifications et alertes.</p>
        </div>
        <div class="flex gap-3">
          @if (store.unreadNotificationsCount() > 0) {
            <button (click)="markAllAsRead()" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-lg shadow-blue-600/20 font-medium transition flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
              Tout marquer comme lu
            </button>
          }
          <button (click)="refreshNotifications()" class="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 transition shadow-sm flex items-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>
            Actualiser
          </button>
        </div>
      </div>

      <!-- Filters -->
      <div class="flex gap-2">
        <button (click)="filter.set('all')" 
                [class.bg-blue-600]="filter() === 'all'"
                [class.text-white]="filter() === 'all'"
                [class.bg-white]="filter() !== 'all'"
                [class.text-slate-700]="filter() !== 'all'"
                class="px-4 py-2 rounded-lg border border-slate-200 text-sm font-medium hover:bg-slate-50 transition">
          Toutes ({{ store.notifications().length }})
        </button>
        <button (click)="filter.set('unread')" 
                [class.bg-blue-600]="filter() === 'unread'"
                [class.text-white]="filter() === 'unread'"
                [class.bg-white]="filter() !== 'unread'"
                [class.text-slate-700]="filter() !== 'unread'"
                class="px-4 py-2 rounded-lg border border-slate-200 text-sm font-medium hover:bg-slate-50 transition">
          Non lues ({{ store.unreadNotificationsCount() }})
        </button>
      </div>

      <!-- Notifications List -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        @if (store.loading()) {
          <div class="py-12 text-center">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <p class="text-slate-500 mt-4">Chargement des notifications...</p>
          </div>
        } @else if (filteredNotifications().length === 0) {
          <div class="py-12 text-center">
            <svg class="w-16 h-16 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
            </svg>
            <h3 class="text-slate-900 font-medium text-lg">Aucune notification</h3>
            <p class="text-slate-500 mt-1">Aucune notification ne correspond à votre filtre.</p>
          </div>
        } @else {
          <div class="divide-y divide-slate-100">
            @for (notif of filteredNotifications(); track notif.id) {
              <div (click)="markAsRead(notif.id)" 
                   class="p-5 hover:bg-slate-50 transition-colors flex gap-4 relative group cursor-pointer"
                   [class.bg-blue-50/30]="!notif.read">
                
                @if (!notif.read) {
                  <div class="absolute top-5 left-3 w-2 h-2 rounded-full bg-blue-500"></div>
                }

                <div class="mt-1 shrink-0">
                  @if (notif.type === 'success') {
                    <div class="w-10 h-10 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center">
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                    </div>
                  } @else if (notif.type === 'alert' || notif.type === 'FA_NON_REGLEE' || notif.type === 'ALERTE_TVA') {
                    <div class="w-10 h-10 rounded-full bg-red-100 text-red-600 flex items-center justify-center">
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                    </div>
                  } @else {
                    <div class="w-10 h-10 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center">
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    </div>
                  }
                </div>

                <div class="flex-1 min-w-0">
                  <div class="flex items-start justify-between gap-4">
                    <div class="flex-1">
                      <p class="text-base font-bold text-slate-800">{{ notif.title }}</p>
                      <p class="text-sm text-slate-600 mt-1 leading-relaxed">{{ notif.message }}</p>
                      <p class="text-xs text-slate-400 mt-2">{{ notif.time }}</p>
                    </div>
                    @if (!notif.read) {
                      <span class="shrink-0 text-xs font-medium text-blue-600 bg-blue-50 px-2 py-1 rounded-full border border-blue-200">
                        Non lue
                      </span>
                    }
                  </div>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `
})
export class NotificationsComponent implements OnInit {
  store = inject(StoreService);
  router = inject(Router);

  filter = signal<'all' | 'unread'>('all');

  filteredNotifications = computed(() => {
    const all = this.store.notifications();
    if (this.filter() === 'unread') {
      return all.filter(n => !n.read);
    }
    return all;
  });

  async ngOnInit() {
    await this.store.loadNotifications(false); // Load all notifications
  }

  async refreshNotifications() {
    await this.store.loadNotifications(false);
  }

  async markAsRead(id: string) {
    await this.store.markNotificationAsRead(id);
  }

  async markAllAsRead() {
    await this.store.markAllAsRead();
  }
}


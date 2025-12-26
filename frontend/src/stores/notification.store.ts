import { Injectable, inject, signal, computed } from '@angular/core';
import { ApiService } from '../services/api.service';

export interface Notification {
  id: string;
  title: string;
  message: string;
  time: string;
  read: boolean;
  type: 'info' | 'alert' | 'success';
}

/**
 * Store spécialisé pour la gestion des notifications
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationStore {
  private api = inject(ApiService);

  // État
  readonly notifications = signal<Notification[]>([]);
  readonly loading = signal<boolean>(false);

  // Computed
  readonly unreadCount = computed(() => 
    this.notifications().filter(n => !n.read).length
  );
  readonly unreadNotifications = computed(() => 
    this.notifications().filter(n => !n.read)
  );

  /**
   * Charge les notifications depuis l'API
   */
  async loadNotifications(unreadOnly: boolean = false): Promise<void> {
    try {
      this.loading.set(true);
      const params: Record<string, any> = { unreadOnly };
      const backendNotifications = await this.api.get<any[]>('/notifications', params).toPromise() || [];
      const mapped = backendNotifications.map(n => this.mapNotification(n));
      this.notifications.set(mapped);
    } catch (error) {
      console.error('Error loading notifications:', error);
      throw error;
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Marque une notification comme lue
   */
  async markAsRead(id: string): Promise<void> {
    try {
      await this.api.put(`/notifications/${id}/read`, {}).toPromise();
      this.notifications.update(list => 
        list.map(n => n.id === id ? { ...n, read: true } : n)
      );
    } catch (error) {
      console.error('Error marking notification as read:', error);
      throw error;
    }
  }

  /**
   * Marque toutes les notifications comme lues
   */
  async markAllAsRead(): Promise<void> {
    try {
      await this.api.put('/notifications/read-all', {}).toPromise();
      this.notifications.update(list => list.map(n => ({ ...n, read: true })));
    } catch (error) {
      console.error('Error marking all notifications as read:', error);
      throw error;
    }
  }

  /**
   * Ajoute une notification locale (pour feedback immédiat)
   */
  addNotification(n: Omit<Notification, 'id' | 'read' | 'time'>): void {
    const newNotif: Notification = {
      id: `local-${Date.now()}`,
      read: false,
      time: 'À l\'instant',
      ...n
    };
    this.notifications.update(list => [newNotif, ...list]);
  }

  /**
   * Supprime une notification
   */
  removeNotification(id: string): void {
    this.notifications.update(list => list.filter(n => n.id !== id));
  }

  private mapNotification(n: any): Notification {
    const niveau = n.niveau || 'info';
    let type: 'info' | 'alert' | 'success' = 'info';
    if (niveau === 'critique' || niveau === 'warning') {
      type = 'alert';
    } else if (niveau === 'info') {
      type = 'info';
    }

    let timeStr = 'À l\'instant';
    if (n.createdAt) {
      const date = new Date(n.createdAt);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMs / 3600000);
      const diffDays = Math.floor(diffMs / 86400000);

      if (diffMins < 1) {
        timeStr = 'À l\'instant';
      } else if (diffMins < 60) {
        timeStr = `Il y a ${diffMins} min`;
      } else if (diffHours < 24) {
        timeStr = `Il y a ${diffHours}h`;
      } else if (diffDays === 1) {
        timeStr = 'Hier';
      } else if (diffDays < 7) {
        timeStr = `Il y a ${diffDays} jours`;
      } else {
        timeStr = date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
      }
    }

    return {
      id: n.id,
      title: n.titre || n.title || 'Notification',
      message: n.message || '',
      time: timeStr,
      read: n.read || false,
      type: type
    };
  }
}


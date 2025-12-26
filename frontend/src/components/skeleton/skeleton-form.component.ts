import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton-form',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-6">
      @if (showTitle) {
        <div class="mb-6">
          <div class="h-6 skeleton-shimmer rounded w-48 mb-2"></div>
          <div class="h-4 skeleton-shimmer rounded w-64"></div>
        </div>
      }
      
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        @for (field of fields; track $index) {
          <div [class.md:col-span-2]="field.fullWidth">
            <div class="h-3 skeleton-shimmer rounded w-24 mb-2"></div>
            <div class="h-10 skeleton-shimmer rounded w-full"></div>
          </div>
        }
      </div>
      
      @if (showActions) {
        <div class="flex justify-end gap-3 pt-4 border-t border-slate-100">
          <div class="h-10 skeleton-shimmer rounded w-24"></div>
          <div class="h-10 skeleton-shimmer rounded w-24"></div>
        </div>
      }
    </div>
  `,
  styles: [`
    .skeleton-shimmer {
      background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
      background-size: 1000px 100%;
      animation: shimmer 2s infinite;
    }

    @keyframes shimmer {
      0% { background-position: -1000px 0; }
      100% { background-position: 1000px 0; }
    }
  `]
})
export class SkeletonFormComponent {
  @Input() fields: number = 6;
  @Input() showTitle: boolean = true;
  @Input() showActions: boolean = true;
}


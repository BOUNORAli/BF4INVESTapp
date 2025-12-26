import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
      @if (showHeader) {
        <div class="mb-4">
          <div class="h-5 skeleton-shimmer rounded w-32 mb-2"></div>
          <div class="h-3 skeleton-shimmer rounded w-48"></div>
        </div>
      }
      
      @if (type === 'kpi') {
        <div class="space-y-3">
          <div class="h-8 skeleton-shimmer rounded w-40"></div>
          <div class="h-4 skeleton-shimmer rounded w-24"></div>
        </div>
      } @else if (type === 'chart') {
        <div class="space-y-4">
          <div class="h-48 skeleton-shimmer rounded-lg"></div>
          <div class="flex justify-between">
            @for (item of [1,2,3,4,5]; track $index) {
              <div class="h-3 skeleton-shimmer rounded w-12"></div>
            }
          </div>
        </div>
      } @else {
        <div class="space-y-3">
          @for (line of lines; track $index) {
            <div class="h-4 skeleton-shimmer rounded" [style.width.%]="line.width || 100"></div>
          }
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
export class SkeletonCardComponent {
  @Input() type: 'default' | 'kpi' | 'chart' = 'default';
  @Input() showHeader: boolean = true;
  @Input() lines: Array<{ width?: number }> = [
    { width: 100 },
    { width: 80 },
    { width: 90 }
  ];
}


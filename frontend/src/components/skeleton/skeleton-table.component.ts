import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-sm text-left min-w-[600px]">
          <thead class="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-100">
            <tr>
              @for (col of columns; track $index) {
                <th class="px-6 py-4 font-semibold" [class.text-right]="col.align === 'right'" [class.text-center]="col.align === 'center'">
                  <div class="h-4 skeleton-shimmer rounded w-24"></div>
                </th>
              }
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            @for (row of rowsArray; track row) {
              <tr>
                @for (col of columns; track $index) {
                  <td class="px-6 py-4" [class.text-right]="col.align === 'right'" [class.text-center]="col.align === 'center'">
                    <div class="h-4 skeleton-shimmer rounded" [style.width]="col.width || '100%'"></div>
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
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
export class SkeletonTableComponent {
  @Input() columns: Array<{ width?: string; align?: 'left' | 'right' | 'center' }> = [];
  @Input() rows: number = 5;
  
  get rowsArray(): number[] {
    return Array.from({ length: this.rows }, (_, i) => i);
  }
}


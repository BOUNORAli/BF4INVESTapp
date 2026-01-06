import { trigger, transition, style, animate, query, group } from '@angular/animations';

/**
 * Animations de transition de route pour navigation fluide
 */
export const routeTransitionAnimations = trigger('routeTransition', [
  transition('* <=> *', [
    // Définir le style initial pour les deux pages
    query(':enter, :leave', [
      style({
        position: 'absolute',
        left: 0,
        width: '100%',
        opacity: 0
      })
    ], { optional: true }),
    
    // Animer la sortie de l'ancienne page
    query(':leave', [
      animate('200ms ease-in', style({ opacity: 0, transform: 'translateX(-10px)' }))
    ], { optional: true }),
    
    // Animer l'entrée de la nouvelle page
    query(':enter', [
      style({ opacity: 0, transform: 'translateX(10px)' }),
      animate('300ms ease-out', style({ opacity: 1, transform: 'translateX(0)' }))
    ], { optional: true })
  ])
]);

/**
 * Animation fade simple
 */
export const fadeTransition = trigger('fadeTransition', [
  transition(':enter', [
    style({ opacity: 0 }),
    animate('200ms ease-in', style({ opacity: 1 }))
  ]),
  transition(':leave', [
    animate('150ms ease-out', style({ opacity: 0 }))
  ])
]);

/**
 * Animation slide depuis la droite
 */
export const slideRightTransition = trigger('slideRight', [
  transition(':enter', [
    style({ transform: 'translateX(100%)', opacity: 0 }),
    animate('300ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
  ]),
  transition(':leave', [
    animate('250ms ease-in', style({ transform: 'translateX(-100%)', opacity: 0 }))
  ])
]);


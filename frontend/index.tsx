

import { bootstrapApplication } from '@angular/platform-browser';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import { AppComponent } from './src/app.component';
import { routes } from './src/app.routes';
import { errorInterceptor } from './src/interceptors/error.interceptor';
import { cacheInterceptor } from './src/interceptors/cache.interceptor';
import { credentialsInterceptor } from './src/interceptors/credentials.interceptor';
import { inject } from '@vercel/analytics';

// Initialize Vercel Web Analytics
inject();

bootstrapApplication(AppComponent, {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(
      withInterceptors([credentialsInterceptor, cacheInterceptor, errorInterceptor]),
      withInterceptorsFromDi()
    )
  ]
}).catch((err) => console.error(err));

// AI Studio always uses an `index.tsx` file for all project types.

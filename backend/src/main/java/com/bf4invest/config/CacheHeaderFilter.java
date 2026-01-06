package com.bf4invest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Filtre pour ajouter les headers de cache HTTP optimisés
 */
@Component
public class CacheHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Ne pas mettre en cache les endpoints d'authentification
        if (path.contains("/auth/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
        // Endpoints de données dynamiques - cache court
        else if (path.contains("/dashboard/") || path.contains("/solde/")) {
            response.setHeader("Cache-Control", "private, max-age=60"); // 1 minute
        }
        // Endpoints de listes - cache moyen
        else if (path.contains("/products") || path.contains("/clients") || path.contains("/suppliers")) {
            response.setHeader("Cache-Control", "private, max-age=300"); // 5 minutes
        }
        // Endpoints de factures et BCs - cache court
        else if (path.contains("/bcs") || path.contains("/factures")) {
            response.setHeader("Cache-Control", "private, max-age=120"); // 2 minutes
        }
        // Endpoints de settings - cache long
        else if (path.contains("/settings") && !path.contains("/settings/data/")) {
            response.setHeader("Cache-Control", "private, max-age=1800"); // 30 minutes
        }
        // Par défaut - pas de cache pour les autres endpoints
        else if (!path.contains("/files/") && !path.endsWith(".pdf") && !path.endsWith(".xlsx")) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
        
        // Ajouter ETag support (géré par ShallowEtagHeaderFilter)
        // Ajouter Vary header pour le cache
        response.setHeader("Vary", "Accept-Encoding, Authorization");
        
        filterChain.doFilter(request, response);
    }
}


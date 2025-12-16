package com.bf4invest.service;

import com.bf4invest.model.Client;
import com.bf4invest.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    
    public List<Client> findAll() {
        return clientRepository.findAll();
    }
    
    public Optional<Client> findById(String id) {
        return clientRepository.findById(id);
    }
    
    public Client create(Client client) {
        // Générer la référence si non fournie
        if (client.getReferenceClient() == null || client.getReferenceClient().trim().isEmpty()) {
            client.setReferenceClient(generateReferenceFromName(client.getNom()));
        } else {
            // Normaliser la référence fournie
            client.setReferenceClient(normalizeReference(client.getReferenceClient()));
        }
        
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        Client saved = clientRepository.save(client);
        
        // Journaliser la création
        auditService.logCreate("Client", saved.getId(), "Client " + saved.getNom() + " créé");
        
        return saved;
    }
    
    public Client update(String id, Client client) {
        return clientRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getNom();
                    existing.setIce(client.getIce());
                    existing.setNom(client.getNom());
                    existing.setAdresse(client.getAdresse());
                    existing.setTelephone(client.getTelephone());
                    existing.setEmail(client.getEmail());
                    existing.setContacts(client.getContacts());
                    
                    // Gérer la référence
                    if (client.getReferenceClient() != null && !client.getReferenceClient().trim().isEmpty()) {
                        existing.setReferenceClient(normalizeReference(client.getReferenceClient()));
                    } else if (existing.getReferenceClient() == null || existing.getReferenceClient().trim().isEmpty()) {
                        // Générer si toujours vide
                        existing.setReferenceClient(generateReferenceFromName(client.getNom()));
                    }
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    Client saved = clientRepository.save(existing);
                    
                    // Journaliser la modification
                    auditService.logUpdate("Client", saved.getId(), oldName, "Client " + saved.getNom() + " modifié");
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
    }
    
    public void delete(String id) {
        // Journaliser avant suppression
        clientRepository.findById(id).ifPresent(c -> {
            auditService.logDelete("Client", id, "Client " + c.getNom() + " supprimé");
        });
        clientRepository.deleteById(id);
    }
    
    /**
     * Génère une référence à partir des 3 premières lettres du nom
     */
    private String generateReferenceFromName(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            return "XXX";
        }
        
        // Supprimer les accents et convertir en majuscules
        String normalized = normalizeReference(nom);
        
        // Prendre les 3 premiers caractères (lettres uniquement)
        StringBuilder ref = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isLetter(c)) {
                ref.append(c);
                if (ref.length() >= 3) {
                    break;
                }
            }
        }
        
        // Si moins de 3 lettres, compléter avec X
        while (ref.length() < 3) {
            ref.append('X');
        }
        
        return ref.toString().substring(0, 3).toUpperCase();
    }
    
    /**
     * Normalise une référence : supprime accents, espaces, convertit en majuscules
     */
    private String normalizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        
        // Supprimer les accents
        String normalized = Normalizer.normalize(reference, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        // Supprimer les espaces et caractères spéciaux, garder seulement lettres et chiffres
        normalized = normalized.replaceAll("[^a-zA-Z0-9]", "");
        
        return normalized.toUpperCase();
    }
}





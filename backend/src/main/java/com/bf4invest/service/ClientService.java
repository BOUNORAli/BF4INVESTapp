package com.bf4invest.service;

import com.bf4invest.model.Client;
import com.bf4invest.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}





package com.bf4invest.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {
    
    private final GridFsTemplate gridFsTemplate;
    
    /**
     * Upload un fichier dans GridFS
     * @param file Le fichier à uploader
     * @param metadata Métadonnées additionnelles (optionnel)
     * @return L'ID du fichier dans GridFS
     */
    public String uploadFile(MultipartFile file, Map<String, String> metadata) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }
        
        // Préparer les métadonnées
        Map<String, String> fileMetadata = new HashMap<>();
        if (metadata != null) {
            fileMetadata.putAll(metadata);
        }
        fileMetadata.put("originalFilename", file.getOriginalFilename());
        fileMetadata.put("contentType", file.getContentType());
        fileMetadata.put("size", String.valueOf(file.getSize()));
        
        // Upload dans GridFS
        ObjectId fileId = gridFsTemplate.store(
            file.getInputStream(),
            file.getOriginalFilename(),
            file.getContentType(),
            fileMetadata
        );
        
        log.info("Fichier uploadé avec succès: {} (ID: {})", file.getOriginalFilename(), fileId);
        return fileId.toString();
    }
    
    /**
     * Récupère un fichier par son ID
     * @param fileId L'ID du fichier dans GridFS
     * @return Le fichier GridFS ou Optional.empty() si non trouvé
     */
    public Optional<GridFSFile> getFile(String fileId) {
        try {
            GridFSFile file = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(fileId)))
            );
            return Optional.ofNullable(file);
        } catch (IllegalArgumentException e) {
            log.warn("ID de fichier invalide: {}", fileId);
            return Optional.empty();
        }
    }
    
    /**
     * Récupère le contenu d'un fichier
     * @param fileId L'ID du fichier
     * @return Le stream du fichier
     * @throws IOException Si le fichier n'existe pas ou erreur de lecture
     */
    public InputStream getFileContent(String fileId) throws IOException {
        Optional<GridFSFile> fileOpt = getFile(fileId);
        if (fileOpt.isEmpty()) {
            throw new IOException("Fichier non trouvé avec l'ID: " + fileId);
        }
        
        GridFSFile file = fileOpt.get();
        return gridFsTemplate.getResource(file).getInputStream();
    }
    
    /**
     * Récupère les métadonnées d'un fichier
     * @param fileId L'ID du fichier
     * @return Les métadonnées du fichier
     */
    public Map<String, Object> getFileMetadata(String fileId) {
        Optional<GridFSFile> fileOpt = getFile(fileId);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("Fichier non trouvé avec l'ID: " + fileId);
        }
        
        GridFSFile file = fileOpt.get();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", file.getId().toString());
        metadata.put("filename", file.getFilename());
        metadata.put("length", file.getLength());
        metadata.put("uploadDate", file.getUploadDate());
        
        // Récupérer les métadonnées personnalisées (incluant contentType)
        if (file.getMetadata() != null) {
            metadata.putAll(file.getMetadata());
            // Le contentType est stocké dans les métadonnées
            if (!metadata.containsKey("contentType") && file.getMetadata().containsKey("contentType")) {
                metadata.put("contentType", file.getMetadata().get("contentType"));
            }
        }
        
        return metadata;
    }
    
    /**
     * Supprime un fichier
     * @param fileId L'ID du fichier à supprimer
     * @return true si le fichier a été supprimé, false sinon
     */
    public boolean deleteFile(String fileId) {
        try {
            gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(fileId))));
            log.info("Fichier supprimé avec succès: {}", fileId);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("ID de fichier invalide pour suppression: {}", fileId);
            return false;
        } catch (Exception e) {
            log.error("Erreur lors de la suppression du fichier: {}", fileId, e);
            return false;
        }
    }
    
    /**
     * Vérifie si un fichier existe
     * @param fileId L'ID du fichier
     * @return true si le fichier existe, false sinon
     */
    public boolean fileExists(String fileId) {
        return getFile(fileId).isPresent();
    }
}


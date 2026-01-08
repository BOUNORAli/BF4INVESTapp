# Plan de Gestion des Avoirs (Factures d'Avoir)

## 📋 Vue d'ensemble

Les avoirs sont des factures crédit qui annulent ou réduisent le montant d'autres factures. Ils peuvent créer des montants négatifs et doivent être correctement liés aux factures d'origine lors de l'importation Excel.

---

## 🎯 Objectifs

1. **Détecter automatiquement les avoirs** lors de l'importation Excel
2. **Lier les avoirs aux factures d'origine** qu'ils annulent
3. **Gérer les montants négatifs** dans tous les calculs
4. **Générer les écritures comptables correctes** pour les avoirs
5. **Afficher visuellement les avoirs** différemment dans l'interface
6. **Calculer correctement les soldes** en tenant compte des avoirs

---

## 📊 Phase 1 : Modification des Modèles de Données

### 1.1 FactureAchat.java

**Ajouter les champs suivants :**

```java
// Type de facture : "NORMALE" ou "AVOIR"
private String typeFacture; // Défaut: "NORMALE"

// Référence à la facture d'origine si c'est un avoir
private String factureOrigineId; // ID de la facture achat annulée

// Numéro de la facture d'origine (pour référence rapide)
private String numeroFactureOrigine;

// Flag pour indiquer si c'est un avoir
private Boolean estAvoir; // Défaut: false

// Liste des factures liées à cet avoir (si avoir partiel)
private List<String> facturesLieesIds;
```

**Modifications nécessaires :**
- Ajouter les getters/setters
- Mettre à jour le builder
- Ajouter validation: si `estAvoir = true`, `factureOrigineId` doit être renseigné

### 1.2 FactureVente.java

**Ajouter les mêmes champs que FactureAchat :**

```java
private String typeFacture; // "NORMALE" ou "AVOIR"
private String factureOrigineId; // ID de la facture vente annulée
private String numeroFactureOrigine;
private Boolean estAvoir; // Défaut: false
private List<String> facturesLieesIds;
```

---

## 📥 Phase 2 : Amélioration de l'Import Excel

### 2.1 Détection des Avoirs

**Dans ExcelImportService.java, améliorer la détection :**

```java
// Actuellement ligne 790 : simple détection par "AVOIR" dans prix_vente_unitaire_ttc
// À améliorer pour :
// 1. Détecter dans plusieurs colonnes : prix, designation, numero_facture
// 2. Détecter les montants négatifs
// 3. Chercher le numéro de facture d'origine dans les colonnes existantes
```

**Stratégies de détection :**

1. **Par mot-clé "AVOIR"** dans :
   - Colonne designation
   - Colonne numero_facture_vente/achat
   - Colonne prix (si contient "AVOIR")

2. **Par montant négatif** :
   - Si `totalTTC < 0` ou `totalHT < 0`
   - Marquer automatiquement comme avoir

3. **Par préfixe de numéro** :
   - Si numéro facture commence par "AV-", "AVOIR-", "CREDIT-"
   - Marquer comme avoir

### 2.2 Liaison avec Facture d'Origine

**Recherche de la facture d'origine :**

1. **Par numéro de facture** :
   - Chercher une colonne "facture_origine" ou similaire
   - Si trouvé, rechercher la facture correspondante dans la DB

2. **Par référence BC** :
   - Si même BC et même client/fournisseur
   - Chercher factures récentes du même partenaire

3. **Par montant correspondant** :
   - Si montant avoir = montant facture exacte
   - Proposer la liaison

**Code à ajouter dans processRow() :**

```java
// Après détection d'un avoir
if (estAvoir) {
    String numeroFactureOrigine = getCellValue(row, columnMap, "facture_origine");
    if (numeroFactureOrigine != null) {
        // Rechercher la facture d'origine
        Optional<FactureVente> factureOrigine = factureVenteRepository
            .findByNumeroFactureVente(numeroFactureOrigine.trim());
        
        if (factureOrigine.isPresent()) {
            fv.setEstAvoir(true);
            fv.setTypeFacture("AVOIR");
            fv.setFactureOrigineId(factureOrigine.get().getId());
            fv.setNumeroFactureOrigine(numeroFactureOrigine);
        } else {
            result.getWarnings().add(
                "Avoir détecté mais facture d'origine " + numeroFactureOrigine + " non trouvée"
            );
        }
    }
}
```

### 2.3 Traitement des Montants Négatifs

**Assurer que les totaux sont corrects :**

```java
// Dans calculateFactureAchatTotals() et calculateFactureVenteTotals()
if (facture.getEstAvoir() != null && facture.getEstAvoir()) {
    // S'assurer que les montants sont négatifs
    if (facture.getTotalHT() != null && facture.getTotalHT() > 0) {
        facture.setTotalHT(-facture.getTotalHT());
    }
    if (facture.getTotalTTC() != null && facture.getTotalTTC() > 0) {
        facture.setTotalTTC(-facture.getTotalTTC());
    }
    if (facture.getTotalTVA() != null && facture.getTotalTVA() > 0) {
        facture.setTotalTVA(-facture.getTotalTVA());
    }
}
```

---

## 💼 Phase 3 : Services Backend

### 3.1 FactureAchatService.java

**Modifications nécessaires :**

1. **Méthode create()** :
   - Valider que si `estAvoir = true`, `factureOrigineId` est renseigné
   - Vérifier que la facture d'origine existe
   - Inverser les montants si nécessaire

2. **Méthode linkAvoirToFacture()** :
   ```java
   public void linkAvoirToFacture(String avoirId, String factureOrigineId) {
       FactureAchat avoir = factureRepository.findById(avoirId)
           .orElseThrow(() -> new RuntimeException("Avoir non trouvé"));
       
       FactureAchat origine = factureRepository.findById(factureOrigineId)
           .orElseThrow(() -> new RuntimeException("Facture d'origine non trouvée"));
       
       avoir.setFactureOrigineId(factureOrigineId);
       avoir.setNumeroFactureOrigine(origine.getNumeroFactureAchat());
       avoir.setEstAvoir(true);
       avoir.setTypeFacture("AVOIR");
       
       factureRepository.save(avoir);
   }
   ```

3. **Méthode getAvoirsByFacture()** :
   ```java
   public List<FactureAchat> getAvoirsByFacture(String factureId) {
       return factureRepository.findByFactureOrigineId(factureId);
   }
   ```

### 3.2 FactureVenteService.java

**Mêmes modifications que FactureAchatService**

### 3.3 SoldeService.java

**Modifications pour gérer les avoirs :**

```java
// Dans enregistrerTransaction()
if (typeTransaction.contains("AVOIR") || montant < 0) {
    // Les avoirs réduisent le solde (montant déjà négatif)
    // Pas besoin d'inverser car montant est déjà négatif
}
```

---

## 📝 Phase 4 : Écritures Comptables

### 4.1 ComptabiliteService.java

**Modifications dans genererEcritureFactureAchat() et genererEcritureFactureVente() :**

```java
if (facture.getEstAvoir() != null && facture.getEstAvoir()) {
    // Les avoirs sont des écritures inversées
    // Débit/Crédit inversés par rapport aux factures normales
    
    // Exemple pour facture achat avoir :
    // Débit : 4456 (TVA déductible) - au crédit (négatif)
    // Crédit : 401 (Fournisseurs) - au débit (négatif)
    // Crédit : 60x (Charges) - au crédit (positif)
}
```

**Nouvelle méthode :**

```java
public void genererEcritureAvoirAchat(FactureAchat avoir) {
    // Générer écriture inversée pour avoir
    // Attention aux comptes et aux signes
}
```

---

## 🎨 Phase 5 : Interface Frontend

### 5.1 Affichage Visuel

**Dans purchase-invoices.component.ts et sales-invoices.component.ts :**

1. **Badge spécial pour avoirs** :
   ```html
   @if (inv.estAvoir) {
     <span class="px-2 py-1 bg-red-100 text-red-700 rounded text-xs font-bold">
       AVOIR
     </span>
   }
   ```

2. **Couleur différente pour montants négatifs** :
   ```html
   <td class="px-4 py-4 text-right" [class.text-red-600]="inv.estAvoir">
     {{ inv.amountTTC | number:'1.2-2' }} MAD
   </td>
   ```

3. **Lien vers facture d'origine** :
   ```html
   @if (inv.factureOrigineId) {
     <button (click)="viewOriginalInvoice(inv.factureOrigineId)">
       Voir facture d'origine: {{ inv.numeroFactureOrigine }}
     </button>
   }
   ```

### 5.2 Filtres

**Ajouter des filtres pour avoirs :**

```typescript
filterStatus: 'all' | 'paid' | 'pending' | 'overdue' | 'avoir' = 'all';

filteredInvoices = computed(() => {
  // ... filtres existants
  if (this.filterStatus() === 'avoir') {
    return invoices.filter(inv => inv.estAvoir);
  }
});
```

### 5.3 Formulaire de Création

**Ajouter option "Avoir" dans le formulaire :**

```html
<div class="bg-red-50 p-4 rounded-xl border border-red-100">
  <label class="flex items-center gap-3 cursor-pointer">
    <input type="checkbox" formControlName="estAvoir" 
           class="w-5 h-5 text-red-600">
    <div>
      <span class="text-sm font-semibold text-red-800">
        Facture d'Avoir
      </span>
      <p class="text-xs text-red-600 mt-0.5">
        Cochez si c'est un avoir annulant une autre facture
      </p>
    </div>
  </label>
  
  @if (form.get('estAvoir')?.value) {
    <div class="mt-3">
      <label>Facture d'origine à annuler</label>
      <select formControlName="factureOrigineId">
        <option value="">Sélectionner...</option>
        @for (facture of availableFactures(); track facture.id) {
          <option [value]="facture.id">
            {{ facture.number }} - {{ facture.amountTTC | number:'1.2-2' }} MAD
          </option>
        }
      </select>
    </div>
  }
</div>
```

---

## 📈 Phase 6 : Calculs et Soldes

### 6.1 Calcul du Solde Restant

**Modifier les calculs pour inclure les avoirs :**

```java
public Double calculerSoldeAvecAvoirs(String factureId) {
    Facture facture = factureRepository.findById(factureId).orElse(null);
    if (facture == null) return 0.0;
    
    Double solde = facture.getTotalTTC(); // Peut être négatif si avoir
    
    // Soustraire les avoirs liés
    List<Facture> avois = factureRepository.findByFactureOrigineId(factureId);
    for (Facture avoir : avois) {
        solde += avoir.getTotalTTC(); // Addition car avoir est déjà négatif
    }
    
    // Soustraire les paiements
    List<Paiement> paiements = paiementRepository.findByFactureId(factureId);
    for (Paiement paiement : paiements) {
        solde -= paiement.getMontant();
    }
    
    return solde;
}
```

### 6.2 Dashboard et Statistiques

**Inclure les avoirs dans les calculs :**

```java
// Revenus nets = Revenus bruts - Avoirs
Double revenusNets = revenusBruts + totalAvoirsVentes; // Avoirs sont négatifs

// Dépenses nettes = Dépenses brutes - Avoirs
Double depensesNettes = depensesBrutes + totalAvoirsAchats; // Avoirs sont négatifs
```

---

## 🧪 Phase 7 : Tests et Validation

### 7.1 Tests Unitaires

1. **Test détection avoir dans ExcelImportService**
2. **Test liaison avoir ↔ facture d'origine**
3. **Test calculs avec avoirs**
4. **Test écritures comptables pour avoirs**

### 7.2 Tests d'Intégration

1. **Import Excel avec avoirs**
2. **Création manuelle d'avoir**
3. **Affichage dans l'interface**
4. **Calcul des soldes après avoir**

---

## 📋 Checklist d'Implémentation

### Backend
- [ ] Modifier `FactureAchat.java` (ajouter champs avoir)
- [ ] Modifier `FactureVente.java` (ajouter champs avoir)
- [ ] Mettre à jour `ExcelImportService.java` (détection et traitement)
- [ ] Modifier `FactureAchatService.java` (gestion avoirs)
- [ ] Modifier `FactureVenteService.java` (gestion avoirs)
- [ ] Mettre à jour `ComptabiliteService.java` (écritures avoirs)
- [ ] Modifier `SoldeService.java` (calculs avec avoirs)
- [ ] Créer repository queries pour rechercher avoirs
- [ ] Ajouter endpoints API pour gérer avoirs

### Frontend
- [ ] Mettre à jour interface `Invoice` dans `store.service.ts`
- [ ] Modifier `purchase-invoices.component.ts` (affichage avoirs)
- [ ] Modifier `sales-invoices.component.ts` (affichage avoirs)
- [ ] Ajouter formulaire création avoir
- [ ] Ajouter filtres pour avoirs
- [ ] Ajouter liens vers factures d'origine
- [ ] Modifier affichage montants négatifs

### Tests
- [ ] Tests unitaires détection avoir
- [ ] Tests unitaires calculs
- [ ] Tests d'intégration import
- [ ] Tests d'intégration création avoir

---

## 🔄 Ordre d'Implémentation Recommandé

1. **Phase 1** : Modèles de données (2-3h)
2. **Phase 2** : Import Excel amélioré (4-5h)
3. **Phase 3** : Services backend (3-4h)
4. **Phase 4** : Écritures comptables (2-3h)
5. **Phase 5** : Interface frontend (4-5h)
6. **Phase 6** : Calculs et soldes (2-3h)
7. **Phase 7** : Tests (3-4h)

**Total estimé : 20-27 heures**

---

## ⚠️ Points d'Attention

1. **Rétrocompatibilité** : Les factures existantes doivent avoir `estAvoir = false` par défaut
2. **Validation** : S'assurer qu'un avoir ne peut pas avoir un montant positif
3. **Circulaire** : Empêcher qu'un avoir annule un autre avoir
4. **Performance** : Optimiser les requêtes de recherche de factures d'origine
5. **UI/UX** : S'assurer que les montants négatifs sont clairement identifiés
6. **Comptabilité** : Vérifier que les écritures comptables respectent le plan comptable marocain

---

## 📚 Références

- Plan comptable marocain : Comptes 4456, 4457 pour TVA
- Normes comptables : Gestion des avoirs et crédits clients/fournisseurs
- Format Excel : Colonnes existantes pour détection avoir


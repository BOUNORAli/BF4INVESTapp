# Ajout d'images aux produits

## Objectif
Permettre d'ajouter une image à chaque produit dans le catalogue de produits. L'image sera stockée en base64 dans MongoDB pour simplifier l'implémentation.

## Architecture

### Backend

1. **Modifier `Product` model** (`backend/src/main/java/com/bf4invest/model/Product.java`)
   - Ajouter un champ `imageBase64` (String) pour stocker l'image en base64
   - Optionnel : ajouter `imageContentType` (String) pour stocker le type MIME (image/png, image/jpeg, etc.)

2. **Modifier `ProductService`** (`backend/src/main/java/com/bf4invest/service/ProductService.java`)
   - Mettre à jour `update()` pour gérer le champ `imageBase64`
   - Mettre à jour `create()` pour gérer le champ `imageBase64`

3. **Créer un endpoint pour upload d'image** (optionnel, ou intégrer dans update/create)
   - Créer `POST /produits/{id}/image` dans `ProductController` pour uploader une image séparément
   - Valider que le fichier est bien une image (vérifier le content-type)
   - Convertir l'image en base64 avant de la stocker
   - Limiter la taille de l'image (par exemple max 2MB)

### Frontend

4. **Modifier l'interface `Product`** (`frontend/src/services/store.service.ts` et `frontend/src/models/types.ts`)
   - Ajouter `imageUrl?: string;` ou `imageBase64?: string;` à l'interface Product

5. **Modifier `ProductService`** (`frontend/src/services/product.service.ts`)
   - Inclure `imageBase64` dans le mapping entre frontend et backend
   - Ajouter une méthode `uploadProductImage(id: string, file: File)` si on crée un endpoint séparé

6. **Modifier le composant `ProductsComponent`** (`frontend/src/pages/products/products.component.ts`)
   - Ajouter un champ `imageFile` dans le formulaire (non reactive, géré séparément)
   - Ajouter un input de type `file` avec accept="image/*" dans le formulaire
   - Ajouter une prévisualisation de l'image sélectionnée
   - Convertir le fichier en base64 lors du submit
   - Afficher l'image dans la carte produit (remplacer l'icône SVG par l'image si disponible)

## Détails d'implémentation

### Conversion fichier -> base64 en TypeScript
```typescript
private async fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = error => reject(error);
  });
}
```

### Affichage de l'image base64
```html
<img [src]="prod.imageUrl || 'data:image/svg+xml...'" alt="{{prod.name}}" />
```

### Validation côté backend
- Limiter la taille du fichier (max 2MB)
- Vérifier le type MIME (image/jpeg, image/png, image/webp)
- Optionnel : redimensionner l'image côté serveur pour optimiser le stockage

## Remarques
- Le stockage en base64 dans MongoDB est simple mais augmente la taille des documents
- Pour une solution plus robuste à l'avenir, on pourra migrer vers un stockage de fichiers séparé (S3, système de fichiers, etc.)
- L'image base64 sera envoyée avec le reste des données du produit lors de la création/mise à jour


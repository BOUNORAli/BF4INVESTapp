/**
 * Utilitaire pour la recherche flexible de produits.
 * Permet de chercher avec des mots partiels dans n'importe quel ordre.
 */

/**
 * Recherche flexible de produits.
 * Permet de chercher avec des mots partiels dans n'importe quel ordre.
 * 
 * @param product Produit à tester
 * @param searchTerm Terme de recherche (peut contenir plusieurs mots)
 * @returns true si le produit correspond à la recherche
 * 
 * Exemple: "Tube rouge" trouvera "TUBE ANNELE DIAMETRE 75 ROUGE"
 */
export function matchesFlexibleSearch(product: { name: string; ref: string }, searchTerm: string): boolean {
  if (!searchTerm || searchTerm.trim() === '') return true;
  
  // Normaliser : minuscules, supprimer accents, supprimer caractères spéciaux
  const normalize = (text: string) => 
    text.toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '') // Supprimer accents
        .replace(/[^a-z0-9\s]/g, ' ') // Remplacer caractères spéciaux par espaces
        .replace(/\s+/g, ' ') // Normaliser espaces
        .trim();
  
  const normalizedSearch = normalize(searchTerm);
  const normalizedName = normalize(product.name);
  const normalizedRef = normalize(product.ref);
  
  // Combiner nom et référence pour la recherche
  const combinedText = `${normalizedName} ${normalizedRef}`;
  
  // Diviser le terme de recherche en mots
  const searchWords = normalizedSearch.split(/\s+/).filter(w => w.length > 0);
  
  // Vérifier que tous les mots sont présents (dans n'importe quel ordre)
  return searchWords.every(word => combinedText.includes(word));
}


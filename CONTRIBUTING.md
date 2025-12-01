# Contributing Guide

Thank you for contributing to the TC11 website! 🎾

## ✏️ Mettre à jour le contenu du site

### 📰 Ajouter une actualité / article

Les articles sont stockés dans `content/posts/`. Pour ajouter un nouvel article :

1. Créer un dossier avec le format `YYYY-MM-DD-titre-de-larticle` dans `content/posts/`
2. Ajouter un fichier `index.md` dans ce dossier avec le contenu suivant :

```markdown
---
title: "Titre de l'article"
description: "Description courte de l'article"
category: "Club"
date: "2025-12-01"
layout: layouts/post.html
labelDetails: "Lire la suite →"
---

Contenu de l'article en Markdown...
```

**Propriétés disponibles :**
- `title` : Titre affiché de l'article
- `description` : Résumé affiché dans la liste des actualités
- `category` : Catégorie (ex: "Club", "Interclubs", "Stages")
- `date` : Date de publication (format YYYY-MM-DD)
- `labelDetails` : Texte du lien "Voir le détail" (optionnel)

### 🏟️ Modifier les installations

Les installations sont configurées dans `content/installations.json`. Pour modifier, ajouter ou supprimer une installation :

1. Ouvrir le fichier `content/installations.json`
2. Modifier l'objet JSON correspondant

**Format d'une installation :**

```json
{
  "name": "Nom de l'installation",
  "image": "/assets/installations/nom-image.jpg",
  "coords": [48.8382777, 2.4081032],
  "terrains": 4,
  "surface": "béton poreux",
  "url": "https://www.paris.fr/lieux/..."
}
```

**Propriétés :**
- `name` : Nom de l'installation
- `image` : Chemin vers l'image (stocker dans `public/assets/installations/`)
- `coords` : Coordonnées GPS `[latitude, longitude]` pour la carte
- `terrains` : Nombre de terrains
- `surface` : Type de revêtement (ex: "béton poreux", "terre battue", "gazon synthétique")
- `url` : Lien vers la page officielle de l'installation

> 💡 N'oubliez pas d'ajouter l'image correspondante dans `public/assets/installations/`

### 📸 Galerie Instagram

Les posts Instagram sont récupérés automatiquement depuis le compte [@tc11assb](https://www.instagram.com/tc11assb/) selon cette chaîne de priorité :

1. **RSS Bridge** (par défaut) : Récupération via service RSS, sans authentification
2. **API Instagram Graph** : Si des identifiants sont configurés
3. **Scraping Playwright** : En dernier recours, via navigateur headless
4. **Liste de secours** : Si tout échoue, utilise `src/main/resources/instagram.json`

Pour mettre à jour la liste de secours, modifiez le fichier `src/main/resources/instagram.json` :

```json
[
  "https://www.instagram.com/p/SHORTCODE1",
  "https://www.instagram.com/p/SHORTCODE2"
]
```

Remplacez les URLs par les posts Instagram souhaités (format : `https://www.instagram.com/p/XXXXXX`).

### 📧 Modifier les informations de contact

L'adresse email de contact est configurée dans `src/main/resources/application.properties` :

```properties
tc11.contact.email=tc11-assb@fft.fr
```

### 🏠 Modifier la page d'accueil

Le contenu de la page d'accueil se trouve dans `content/index.html`. Vous pouvez modifier :
- Les textes de présentation du club
- Les statistiques affichées
- La structure des sections

## 📝 Pull Request Title Convention

All Pull Request titles must follow the **Conventional Commits** convention and be written **in English**.

### Format

```
<type>: <description>
```

or with an optional scope:

```
<type>(<scope>): <description>
```

### Allowed Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation |
| `style` | Formatting (no code change) |
| `refactor` | Code refactoring |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `build` | Build system changes |
| `ci` | CI/CD configuration |
| `chore` | Other changes |
| `revert` | Revert a commit |

### Examples

✅ **Valid:**
- `feat: Add new news page`
- `fix(navigation): Fix mobile menu`
- `docs: Update README`

❌ **Invalid:**
- `Add new page` (missing type)
- `FEAT: new page` (type in uppercase)
- `feat: Ajoute une page` (not in English)

## 🚀 Contribution Process

1. Fork the project
2. Create a branch (`git checkout -b feature/my-feature`)
3. Commit your changes with a conventional message
4. Push the branch (`git push origin feature/my-feature`)
5. Open a Pull Request with a conventional title in English

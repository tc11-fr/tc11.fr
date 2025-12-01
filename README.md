# TC11 – Site Web du Tennis Club du 11e

Site web officiel du **TC11**, le Tennis Club du 11e arrondissement de Paris.

🎾 Tennis pour toutes et tous, du loisir à la compétition, pour enfants et adultes.

## 🌐 Site en ligne

Le site est accessible à l'adresse : [https://tc11.fr](https://tc11.fr)

## 🏗️ Technologies utilisées

- **[Quarkus](https://quarkus.io/)** – Framework Java
- **[Roq](https://quarkiverse.github.io/quarkiverse-docs/quarkus-roq/dev/)** – Générateur de site statique pour Quarkus
- **[Tailwind CSS](https://tailwindcss.com/)** – Framework CSS
- **[Alpine.js](https://alpinejs.dev/)** – Framework JavaScript léger

## 📋 Prérequis

- Java 21 ou supérieur
- Maven 3.9+ (ou utiliser le wrapper Maven inclus `./mvnw`)

## 🚀 Développement en local

### Cloner le dépôt

```bash
git clone https://github.com/tc11-fr/tc11.fr.git
cd tc11.fr
```

### Lancer le serveur de développement

```bash
./mvnw quarkus:dev
```

Le site sera accessible à l'adresse : [http://localhost:8080](http://localhost:8080)

### Générer le site statique

```bash
./mvnw package
```

Les fichiers générés se trouvent dans le dossier `target/roq/`.

## 📁 Structure du projet

```
tc11.fr/
├── content/               # Contenu du site (pages, actualités)
│   ├── index.html         # Page d'accueil
│   ├── actus.json         # Liste des actualités (généré automatiquement)
│   ├── installations.json # Liste des installations de tennis
│   └── posts/             # Articles et actualités
├── public/                # Fichiers statiques (images, scripts)
│   ├── assets/            # Images du site
│   │   └── installations/ # Photos des installations
│   ├── reactions.js       # Système de likes et vues
│   └── style.css          # Styles CSS
├── templates/             # Modèles de page
│   ├── layouts/           # Mises en page
│   └── partials/          # Composants réutilisables
├── src/                   # Code source Java
│   └── main/resources/    # Configuration et ressources
│       └── instagram.json # Liste de secours des posts Instagram
└── pom.xml                # Configuration Maven
```

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

Les posts Instagram sont récupérés automatiquement depuis le compte [@tc11assb](https://www.instagram.com/tc11assb/) via RSS Bridge ou l'API Instagram.

En cas d'échec de la récupération automatique, le système utilise la liste de secours dans `src/main/resources/instagram.json` :

```json
[
  "https://www.instagram.com/p/SHORTCODE1",
  "https://www.instagram.com/p/SHORTCODE2"
]
```

Pour mettre à jour la liste de secours, modifiez ce fichier avec les URLs des posts souhaités.

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

## ❤️ Système de likes et vues

Le site dispose d'un système de likes et de compteur de vues pour les articles. Pour un site statique, plusieurs options sont disponibles :

### Option 1 : localStorage (par défaut)

Stockage local dans le navigateur. Les likes sont persistants par navigateur mais pas partagés entre appareils.

**Avantages :**
- Aucune configuration requise
- Fonctionne immédiatement
- Respect de la vie privée

**Inconvénients :**
- Données non partagées entre appareils/navigateurs
- Compteurs individuels par utilisateur

### Option 2 : Supabase (recommandé pour la persistance)

Base de données PostgreSQL gratuite avec API REST pour des compteurs partagés.

**Configuration :**

1. Créer un compte sur [supabase.com](https://supabase.com)
2. Créer une table `article_reactions` :
   ```sql
   CREATE TABLE article_reactions (
     article_id TEXT PRIMARY KEY,
     likes INTEGER DEFAULT 0,
     views INTEGER DEFAULT 0
   );
   ```
3. (Optionnel) Créer une fonction RPC pour l'incrémentation atomique des vues :
   ```sql
   CREATE OR REPLACE FUNCTION increment_views(article_id_param TEXT)
   RETURNS INTEGER AS $$
   DECLARE
     new_views INTEGER;
   BEGIN
     INSERT INTO article_reactions (article_id, views)
     VALUES (article_id_param, 1)
     ON CONFLICT (article_id)
     DO UPDATE SET views = article_reactions.views + 1
     RETURNING views INTO new_views;
     RETURN new_views;
   END;
   $$ LANGUAGE plpgsql;
   ```
4. Configurer dans `templates/partials/head.html` :
   ```html
   <script src="https://unpkg.com/@supabase/supabase-js@2"></script>
   <script>
     window.TC11_REACTIONS_CONFIG = {
       backend: 'supabase',
       supabaseUrl: 'https://your-project.supabase.co',
       supabaseAnonKey: 'your-anon-key'
     };
   </script>
   ```

**Avantages :**
- Compteurs partagés entre tous les visiteurs
- Tier gratuit généreux
- API REST simple

### Option 3 : Giscus (commentaires + réactions via GitHub)

[Giscus](https://giscus.app) utilise GitHub Discussions pour gérer les réactions et commentaires. Idéal pour les projets open source hébergés sur GitHub.

**Configuration :**

1. Activer GitHub Discussions sur votre dépôt
2. Installer l'application [Giscus](https://github.com/apps/giscus) sur votre dépôt
3. Générer la configuration sur [giscus.app](https://giscus.app)
4. Configurer dans `templates/partials/head.html` :
   ```html
   <script>
     window.TC11_REACTIONS_CONFIG = {
       backend: 'giscus',
       giscusRepo: 'tc11-fr/tc11.fr',
       giscusRepoId: 'R_kgDOPa7m9g',
       giscusCategory: 'Announcements',
       giscusCategoryId: 'DIC_kwDOPa7m9s4CzNU1',
       giscusMapping: 'pathname',
       giscusTheme: 'preferred_color_scheme',
       giscusLang: 'fr'
     };
   </script>
   ```

**Avantages :**
- Commentaires + réactions intégrés
- Authentification via GitHub
- Aucune base de données requise
- Modération via GitHub

**Inconvénients :**
- Nécessite un compte GitHub pour interagir
- Limité aux projets hébergés sur GitHub

### Autres options

- **Firebase Realtime Database** : Alternative à Supabase
- **Cloudflare Workers + KV** : Pour hébergement sur Cloudflare

## 🤝 Contribuer

Les contributions sont les bienvenues ! Consultez le [guide de contribution](CONTRIBUTING.md) pour les détails.

> **Note :** Les titres de Pull Request doivent suivre la convention [Conventional Commits](https://www.conventionalcommits.org/).
> Exemple : `feat: Ajoute une nouvelle page`

## 📧 Contact

- **Site web** : [https://tc11.fr](https://tc11.fr)
- **Instagram** : [@tc11assb](https://www.instagram.com/tc11assb/)

## 📄 Licence

Ce projet est la propriété du TC11. Tous droits réservés.
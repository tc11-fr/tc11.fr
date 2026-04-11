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
│   ├── actus.json         # Liste des actualités (générée automatiquement)
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

## 📸 Configuration Instagram

Le site affiche automatiquement les derniers posts Instagram du compte [@tc11assb](https://www.instagram.com/tc11assb/). La configuration se trouve dans `src/main/resources/application.properties`.

### Chaîne de récupération des posts

Le site utilise la chaîne de récupération suivante :

1. **Instagram API** (`graph.instagram.com`) — si le token `INSTAGRAM_ACCESS_TOKEN` est configuré *(recommandé)*
2. Scraping via navigateur sans tête (Playwright)
3. Instagram Graph API (`graph.facebook.com`) — si `INSTAGRAM_ACCESS_TOKEN` et `INSTAGRAM_ACCOUNT_ID` sont tous deux configurés
4. RSS Bridge (aucune authentification requise)
5. Fichier de secours `instagram.json` (si tout le reste échoue)

### Obtenir un token Instagram API

1. Aller sur [https://developers.meta.com/](https://developers.meta.com/) et se connecter avec son compte Facebook.
2. Aller dans **My Apps** → **Create App**.
3. Choisir un nom (ex. `APP_TC11`), sélectionner **Others** puis **Business**.
4. Depuis le dashboard, dans **Add products to your app**, cliquer sur **Set up** à côté de **Instagram**.
5. Dans le menu **App roles → Roles**, cliquer sur **Add people** et s'ajouter en tant que **Instagram Tester** en sélectionnant le compte Instagram cible.
6. Depuis l'application Instagram, aller dans **Profile → Options (⚙️) → Apps and websites → Tester Invites** et accepter l'invitation.
7. De retour sur le dashboard Meta, générer un token d'accès pour le compte Instagram.

**Valider le token** dans le navigateur :

```text
https://graph.instagram.com/me?fields=id,username&access_token=TON_TOKEN
```

Si `id` et `username` sont retournés, le token est valide.

**Tester la liste des posts :**

```text
https://graph.instagram.com/me/media?fields=id,caption,media_type,media_url,permalink,timestamp,thumbnail_url&access_token=TON_TOKEN
```

### Configurer le token dans GitHub

1. Aller dans **Settings → Secrets and variables → Actions** du dépôt GitHub.
2. Cliquer sur **New repository secret**.
3. Nom : `INSTAGRAM_ACCESS_TOKEN`, valeur : le token copié ci-dessus.
4. Sauvegarder.

Le workflow de déploiement utilise le fichier de secours `instagram.json` (`TC11_INSTAGRAM_ENABLED=false`). Le workflow quotidien `instagram-api-refresh.yml` appelle l'Instagram API avec le secret `INSTAGRAM_ACCESS_TOKEN` pour mettre à jour ce fichier de secours, puis déclenche un déploiement si des changements sont détectés. Le token n'est **jamais** stocké dans le code source.

### Liste noire des posts

Pour masquer certains posts Instagram (par exemple, des annonces obsolètes), ajoutez leurs identifiants à la liste noire :

```properties
# Liste des posts Instagram à exclure de la galerie
# Peut contenir des shortcodes ou des URLs complètes, séparés par des virgules
tc11.instagram.blacklist=DKurQ_ktdgw,https://www.instagram.com/p/ABC123/
```

**Exemples :**
- Avec shortcode uniquement : `tc11.instagram.blacklist=DKurQ_ktdgw`
- Avec URL complète : `tc11.instagram.blacklist=https://www.instagram.com/p/DKurQ_ktdgw`
- Plusieurs posts : `tc11.instagram.blacklist=DKurQ_ktdgw,ABC123DEF,XYZ789GHI`

Pour trouver le shortcode d'un post, utilisez l'URL du post Instagram :
`https://www.instagram.com/p/DKurQ_ktdgw/` → le shortcode est `DKurQ_ktdgw`

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
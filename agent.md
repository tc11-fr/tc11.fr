# Agent Guide – TC11 Website

This is the single source of truth for AI agents working on the TC11 project. **Update this file whenever you discover something important that is missing or incorrect.**

## ⚠️ Important: This is a Static Site Generator

This project uses **[Quarkus Roq](https://quarkiverse.github.io/quarkiverse-docs/quarkus-roq/dev/)**, a static site generator built on top of Quarkus. It is **not** a classic Quarkus REST application.

Key implications:
- **No REST endpoints** – Do not attempt to add or call `@Path`/JAX-RS REST resources; they will not work as expected in a static site context.
- **No server-side request handling** – All content is rendered at build time into static HTML files.
- **Java code** is limited to Qute template extensions (`@TemplateExtension`) that supply data to templates during site generation.
- Pages are defined in `content/` (Markdown or HTML with frontmatter) and rendered using `templates/` (Qute templates).

## 🚀 Running in Development Mode

To preview the site locally with live reload:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./mvnw quarkus:dev
```

The site will be available at **http://localhost:8080**.

Quarkus dev mode watches for file changes in `content/`, `templates/`, and `public/` and reloads automatically — no restart needed after editing content or templates.

## 📸 Verifying Changes with the Playwright MCP Server

After starting the dev server, use the **Playwright MCP server** to take screenshots and visually verify your changes:

```
Use the Playwright MCP tool to navigate to http://localhost:8080 and take a screenshot.
```

Useful pages to check after common changes:

| Change | URL to screenshot |
|--------|-------------------|
| Homepage | `http://localhost:8080/` |
| News list | `http://localhost:8080/#actus` |
| A specific article | `http://localhost:8080/posts/<slug>/` |
| Club / installations section | `http://localhost:8080/#club` |

### Example workflow

1. Start the dev server: `./mvnw quarkus:dev`
2. Make your content or template change.
3. Use the Playwright MCP `browser_navigate` tool to open the relevant page.
4. Use the Playwright MCP `browser_take_screenshot` tool to capture the result.
5. Inspect the screenshot to confirm the change looks correct before committing.

## 📰 Adding a News Article (Post)

Articles live in `content/posts/`. Each post is a **folder** containing an `index.md` file.

### Steps

1. Create the folder: `content/posts/YYYY-MM-DD-article-title/`
   - Use today's date and a lowercase, hyphenated slug derived from the title.
2. Create `index.md` inside that folder with this frontmatter:

```markdown
---
title: "Article Title"
category: "Club"
date: "YYYY-MM-DD"
layout: layouts/post.html
labelDetails: "Voir le détail →"
---

Article content in Markdown...
```

**Available categories:** `Compétitions`, `Club`, `Jeunes`, `Stages`, `Animations`, `Infos pratiques`

**Available `labelDetails` values:**
`Voir le détail →`, `En savoir plus →`, `Je m'inscris →`, `Découvrir →`, `Renseignements →`,
`Voir l'événement →`, `Voir les résultats →`, `Inscrire mon enfant →`, `Je participe →`,
`Voir le calendrier →`, `Infos et inscription →`, `Voir les photos →`

### Images and attachments

Any image or file placed **in the same folder** as `index.md` is automatically detected by Roq and made available on the article page — no extra configuration needed. Simply copy image files alongside `index.md`.

> ℹ️ `site.slugify-files=false` is set in `application.properties` so accented characters in filenames are preserved as-is.

### Example folder structure

```
content/posts/2025-06-01-tournoi-ete/
├── index.md
├── photo-terrain.jpg
└── resultats.pdf
```

## 🏗️ Project Layout

```
tc11.fr/
├── content/               # Site content (Markdown/HTML pages)
│   ├── index.html         # Homepage
│   ├── actus.json         # News list data file (auto-generated)
│   ├── instagram.json     # Instagram posts data file (template-rendered)
│   ├── installations.json # Tennis court installations data
│   └── posts/             # Blog articles (YYYY-MM-DD-slug/index.md)
├── public/                # Static assets (copied as-is)
│   ├── assets/            # Images
│   ├── style.css          # Main CSS
│   └── *.js               # JavaScript files
├── templates/             # Qute templates
│   ├── layouts/           # Page layouts (main.html, page.html, post.html)
│   └── partials/          # Reusable components (head.html, header.html, footer.html)
├── src/main/java/fr/tc11/ # Java source code
│   ├── ContactTemplateExtension.java    # {contact:email} template helper
│   ├── FilesViewHelpers.java            # {files:images(page)} template helper
│   ├── InstagramPostsFetcher.java       # Instagram feed fetcher
│   └── InstagramTemplateExtension.java  # {instagram:posts} template helper
├── src/main/resources/
│   ├── application.properties  # Quarkus/app configuration
│   └── instagram.json          # Fallback Instagram posts
├── src/test/java/         # Unit tests (QuarkusTest)
├── pom.xml                # Maven project config
└── .github/workflows/     # CI/CD workflows
```

## 🔑 Key Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies: Java 21, Quarkus 3.25.2, quarkus-roq 1.8.0, Playwright 1.49.0 |
| `src/main/resources/application.properties` | Instagram fetcher config, contact email |
| `.github/workflows/deploy.yml` | Main deploy to GitHub Pages on push to main |
| `.github/workflows/preview-pr.yml` | PR preview via Surge on `/preview` comment |

## 🔧 Build Commands

### Environment Setup

Always set Java 21 before running commands:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### Compile

```bash
./mvnw clean compile -DskipTests
```

### Run Tests (~14 s)

```bash
./mvnw test
```

### Generate Static Site

```bash
QUARKUS_ROQ_GENERATOR_BATCH=true ./mvnw -B -q package quarkus:run
```

Output is written to `target/roq/`.

## 🧪 Testing Notes

- Tests use `@QuarkusTest` and run on port **8081** (not 8080).
- Tests validate Java helper logic (e.g. Instagram JSON parsing) — not the rendered HTML pages.

## ☁️ CI/CD Workflows

1. **deploy.yml** – Deploys to GitHub Pages on push to `main`
2. **preview-pr.yml** – Comment `/preview` on a PR to deploy a Surge preview
3. **issue-to-pr.yml** – Auto-creates a PR from issues with the `contenu` label
4. **instagram-api-refresh.yml** – Refreshes the Instagram fallback JSON daily via the Instagram API (requires `INSTAGRAM_ACCESS_TOKEN` secret)
5. **warm-maven-cache.yml** – Weekly Maven cache warmup

## 📋 Pull Request Requirements

- PR titles **must** follow [Conventional Commits](https://www.conventionalcommits.org/) format **in English**.
- Examples: `feat: Add new page`, `fix(navigation): Fix mobile menu`, `docs: Update README`
- Invalid: missing type, uppercase type, non-English description.

## ☕ Java Code Notes

- Template extensions use `@TemplateExtension(namespace = "X")` for `{X:method}` syntax in Qute templates.
- Instagram posts are fetched at startup with fallback chain: Instagram API (graph.instagram.com, token only) → Playwright headless browser → Graph API (graph.facebook.com, token + account-id) → RSS Bridge → fallback JSON.
- All Java classes are in package `fr.tc11`.

## 📝 More Details

- Full contribution guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Project README (French): [`README.md`](README.md)

---

> **For AI agents:** Always use the documented commands and paths above. Only search the codebase if information here is incomplete or produces errors. If you discover something important that is missing or outdated in this file, update it as part of your work.

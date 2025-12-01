# Copilot Coding Agent Instructions

## Repository Overview

TC11 is the official website for the Tennis Club du 11e arrondissement de Paris. It is a **static site** built using:
- **Quarkus** (Java 21) with **Roq** static site generator
- **Qute** templating engine
- **Tailwind CSS** (via CDN) and **Alpine.js** for frontend
- **Maven** for build (wrapper included: `./mvnw`)

The site generates static HTML from content files and templates, then deploys to GitHub Pages.

## Build Commands

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

### Run Tests (17 tests, ~14s)
```bash
./mvnw test
```

### Generate Static Site
```bash
QUARKUS_ROQ_GENERATOR_BATCH=true ./mvnw -B -q package quarkus:run
```
Output is written to `target/roq/`.

### Development Server (live reload)
```bash
./mvnw quarkus:dev
```
Access at http://localhost:8080

## Project Layout

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

## Key Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies: Java 21, Quarkus 3.25.2, quarkus-roq 1.8.0, Playwright 1.49.0 |
| `src/main/resources/application.properties` | Instagram fetcher config, contact email |
| `.github/workflows/deploy.yml` | Main deploy to GitHub Pages on push to main |
| `.github/workflows/preview-pr.yml` | PR preview via Surge on `/preview` comment |

## Content Changes

### Adding a News Article
1. Create folder: `content/posts/YYYY-MM-DD-article-title/`
2. Add `index.md` with frontmatter:
```yaml
---
title: "Article Title"
category: "Club"
date: "YYYY-MM-DD"
layout: layouts/post.html
labelDetails: "Read more →"
---
Content in Markdown...
```

### Updating Installations
Edit `content/installations.json` (array of installation objects with name, image, coords, terrains, surface, url).

### Updating Contact Email
Edit `tc11.contact.email` in `src/main/resources/application.properties`.

## CI/CD Workflows

1. **deploy.yml** - Deploys to GitHub Pages on push to main
2. **preview-pr.yml** - Comment `/preview` on a PR to deploy preview to Surge
3. **issue-to-pr.yml** - Auto-creates PR from issues with `contenu` label
4. **rss-trigger.yml** - Daily check of Instagram RSS to trigger redeploy
5. **warm-maven-cache.yml** - Weekly Maven cache warmup

## Pull Request Requirements

- PR titles **must** follow [Conventional Commits](https://www.conventionalcommits.org/) format **in English**
- Examples: `feat: Add new page`, `fix(navigation): Fix mobile menu`, `docs: Update README`
- Invalid: Missing type, uppercase type, non-English description

## Java Code Notes

- Template extensions use `@TemplateExtension(namespace = "X")` for `{X:method}` syntax in Qute templates
- Instagram posts are fetched at startup with fallback chain: RSS Bridge → Graph API → Playwright → fallback JSON
- All Java classes are in package `fr.tc11`

## Testing Notes

- Tests use `@QuarkusTest` annotation
- Test port is 8081 (not 8080)
- Tests verify Instagram JSON parsing logic (not network calls)

## Trust These Instructions

Always use the documented commands and paths. Only search the codebase if information here is incomplete or produces errors.

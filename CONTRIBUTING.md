# Contributing Guide

Thank you for contributing to the TC11 website! 🎾

## ✏️ Updating Site Content

### 📰 Adding a News Article

Articles are stored in `content/posts/`. To add a new article:

1. Create a folder with the format `YYYY-MM-DD-article-title` in `content/posts/`
2. Add an `index.md` file in this folder with the following content:

```markdown
---
title: "Article Title"
description: "Short description of the article"
category: "Club"
date: "2025-12-01"
layout: layouts/post.html
labelDetails: "Read more →"
---

Article content in Markdown...
```

**Available properties:**
- `title`: Displayed article title
- `description`: Summary shown in the news list
- `category`: Category (e.g., "Club", "Interclubs", "Stages")
- `date`: Publication date (YYYY-MM-DD format)
- `labelDetails`: "View details" link text (optional)

### 🏟️ Updating Installations

Installations are configured in `content/installations.json`. To modify, add, or remove an installation:

1. Open the `content/installations.json` file
2. Edit the corresponding JSON object

**Installation format:**

```json
{
  "name": "Installation Name",
  "image": "/assets/installations/image-name.jpg",
  "coords": [48.8382777, 2.4081032],
  "terrains": 4,
  "surface": "porous concrete",
  "url": "https://www.paris.fr/lieux/..."
}
```

**Properties:**
- `name`: Installation name
- `image`: Path to the image (store in `public/assets/installations/`)
- `coords`: GPS coordinates `[latitude, longitude]` for the map
- `terrains`: Number of courts
- `surface`: Surface type (e.g., "porous concrete", "clay", "synthetic grass")
- `url`: Link to the official installation page

> 💡 Don't forget to add the corresponding image in `public/assets/installations/`

### 📸 Instagram Gallery

Instagram posts are fetched automatically from the [@tc11assb](https://www.instagram.com/tc11assb/) account using this priority chain:

1. **Playwright Scraping** (default): Headless browser fetch from Instagram profile page
2. **Instagram Graph API**: If credentials are configured
3. **RSS Bridge**: Fallback if Playwright and Graph API fail
4. **Fallback list**: If all else fails, uses `src/main/resources/instagram.json`

In CI, the daily `rss-trigger.yml` workflow refreshes `src/main/resources/instagram.json` using the live Playwright smoke test and commits the updated fallback file. The deploy and preview workflows then build the site with `tc11.instagram.enabled=false` so generation stays deterministic and does not depend on Instagram network access.

#### ✅ Refreshing the fallback list with Playwright

Use this Java test to fetch the latest posts from Instagram and rewrite the fallback file used by CI builds:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./mvnw -Dtest=InstagramFallbackRefreshTest#refreshFallbackFromPlaywrightLive -Dtc11.test.instagram.refresh=true -Dtc11.instagram.enabled=true -Dtc11.instagram.output-file=src/main/resources/instagram.json test
```

The test rewrites `src/main/resources/instagram.json` and prints the first fetched URL prefixed with `INSTAGRAM_FALLBACK_PRIMARY=`.

Example output (run on 2026-03-20):

```text
INSTAGRAM_FALLBACK_PRIMARY=https://www.instagram.com/p/DV6YmiTDBvC
```

> ℹ️ This refresh test uses the live Playwright fetcher and rewrites the fallback JSON before blacklist filtering.

To update the fallback list, edit the `src/main/resources/instagram.json` file:

```json
[
  "https://www.instagram.com/p/SHORTCODE1",
  "https://www.instagram.com/p/SHORTCODE2"
]
```

Replace the URLs with the desired Instagram posts (format: `https://www.instagram.com/p/XXXXXX`).

### 📧 Updating Contact Information

The contact email is configured in `src/main/resources/application.properties`:

```properties
tc11.contact.email=tc11-assb@fft.fr
```

### 🏠 Updating the Homepage

The homepage content is located in `content/index.html`. You can modify:
- Club presentation texts
- Displayed statistics
- Section structure

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

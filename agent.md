# Agent Guide – TC11 Website

This file contains guidance for AI agents working on the TC11 website project.

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
| News list | `http://localhost:8080/actus/` |
| A specific article | `http://localhost:8080/posts/<slug>/` |
| Installations page | `http://localhost:8080/installations/` |

### Example workflow

1. Start the dev server: `./mvnw quarkus:dev`
2. Make your content or template change.
3. Use the Playwright MCP `browser_navigate` tool to open the relevant page.
4. Use the Playwright MCP `browser_take_screenshot` tool to capture the result.
5. Inspect the screenshot to confirm the change looks correct before committing.

## 🏗️ Project Layout (quick reference)

```
tc11.fr/
├── content/               # Pages and articles (Markdown / HTML + frontmatter)
│   └── posts/             # News articles (YYYY-MM-DD-slug/index.md)
├── public/                # Static assets copied as-is (images, CSS, JS)
├── templates/             # Qute HTML templates
│   ├── layouts/           # Full-page layouts
│   └── partials/          # Reusable components (header, footer, …)
├── src/main/java/fr/tc11/ # Java template extensions (data helpers only)
├── src/main/resources/
│   └── application.properties  # App configuration
└── pom.xml
```

## 🧪 Running Tests

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./mvnw test
```

Tests use `@QuarkusTest` and run on port **8081**. They validate Java helper logic (e.g. Instagram JSON parsing) — not the rendered HTML pages.

## 📝 More Details

- Full development and contribution guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Project README (French): [`README.md`](README.md)
- Copilot-specific instructions: [`.github/copilot-instructions.md`](.github/copilot-instructions.md)

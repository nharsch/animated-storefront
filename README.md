# Animated Storefront

A demo exploring AI-directed UI via LLM tool use. A storefront where a chat sidebar drives the UI by emitting structured tool calls that map to re-frame events — chat is just another input source on the same event bus.

## What it does

The chat panel lets you query and navigate a product catalog in natural language. Claude controls the UI directly: filtering products, switching views (grid/list/compare/PDP), and running Datalog queries against an in-browser DataScript database. All chat actions go through the same event bus as manual UI interactions.

**Try queries like:**
- "show me things I can wear under $50"
- "compare the top-rated watches"
- "is the nail polish well reviewed?"
- "sort these by rating"

## Architecture

```
User message → Claude API (tools + current UI state in system prompt)
  → Claude returns tool_use blocks
    → client maps tool name → rf/dispatch (re-frame event)
    → tool results fed back to Claude (agentic loop, up to 5 iterations)
  → Claude returns text response → chat UI
```

**Stack:**
- **ClojureScript** — language
- **Shadow-cljs** — build tooling + hot reload
- **Reagent + Re-frame** — React wrapper + Redux-like state management
- **DataScript** — in-browser Datalog database for the product catalog
- **Claude API (Haiku)** — chat with tool use
- **DummyJSON** — product data source
- **Tailwind CSS** — styling

**Tools available to Claude:**

| Tool | Type | Effect |
|------|------|--------|
| `change_view` | write | Switch to grid/list/compare/pdp with optional pinned product set |
| `change_filters` | write | Update category/price filters |
| `change_sort` | write | Change sort field and direction |
| `query_products` | read | Simple text/category/price search |
| `get_current_products` | read | Return what's currently visible on screen |
| `datalog_query` | read | Run arbitrary Datalog against the product DataScript DB |

## Development

### Prerequisites

- Node.js
- Java (for ClojureScript compilation via Shadow-cljs)
- An Anthropic API key

### Running locally

```bash
npm install
ANTHROPIC_API_KEY=your-key-here npx shadow-cljs watch app
```

Open http://localhost:3000. The dev build calls the Anthropic API directly from the browser (using `anthropic-dangerous-direct-browser-access`). Your API key is injected at compile time via `#shadow/env` and baked into the JS bundle — don't use this in production.

### Building for production

```bash
npx shadow-cljs release app
```

The production build routes chat requests through `/api/chat` (a thin proxy) instead of hitting the Anthropic API directly.

## Deployment

### Backend proxy

A Lambda function is included at `lambda/chat/index.js`. It proxies requests to the Anthropic API, keeping your API key server-side.

Deploy it as an AWS Lambda + API Gateway endpoint, then set the `ANTHROPIC_API_KEY` environment variable on the Lambda. The function accepts the same request shape as the Anthropic Messages API.

### Frontend

The `public/` directory is a static site. Point it at any static host (S3, Cloudflare Pages, etc.) and ensure `/api/chat` routes to your Lambda.

## Key design decisions

**Chat as an event source** — The chat panel doesn't have special UI control powers. It dispatches the same re-frame events as buttons and dropdowns. This keeps the state model simple and makes chat actions composable with manual UI interactions.

**DataScript for queries** — Product data lives in a DataScript conn in the browser. Claude can run arbitrary Datalog queries including rules for cross-category concepts (e.g. "wearable" spanning shoes, shirts, and watches). Query results are returned to Claude before any UI change, so it can verify intent before committing.

**Active query tracking** — The last successful Datalog query is saved in app state and included in the system prompt on follow-up turns, giving Claude context about what produced the current result set.

**UI notes** — When the user manually clears filters or changes sort, a display-only note is inserted into the chat history (filtered out before sending to the API) so the conversation stays coherent.

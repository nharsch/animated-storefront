# Animated Storefront

A demo app exploring AI-directed UI via LLM tool use. A storefront where a chat sidebar drives the UI by emitting structured tool calls that map to state events — chat is just another input source on the same event bus.

## Architecture

- **DataScript DB** — product data, queryable in the browser
- **Redux-like state store** — single source of truth
- **React components** — Grid, PDP, Compare, List views
- **Chat panel** — persistent sidebar using Claude API with tool use (Haiku for speed/cost)
- **Thin backend proxy** — protects API key

## Chat Tool Use → Redux Action Pattern

Tools are JSON schemas mirroring UI actions. Claude returns `tool_use` blocks; client maps them to `store.dispatch()`.

```
User message → API (tools + current UI state in system prompt)
  → Claude returns tool_use blocks
    → client maps tool name → store.dispatch()
    → tool results fed back to Claude (agentic loop)
  → Claude returns text response → chat UI
```

**Write tools** (fire Redux actions):
- `change_filters` — update product filters
- `change_view` — switch view (grid, list, compare, pdp) + optional product_ids
- `change_sort` — change sort order

**Read tools** (query DataScript, return results to Claude):
- `query_products` — search products to answer customer questions

## Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

`clj-nrepl-eval --discover-ports`

**Evaluate code:**

`clj-nrepl-eval -p <port> "<clojure-code>"`

With timeout (milliseconds)

`clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"`

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.

## Open Questions

- **State serialization** — how much UI state to pack into system prompt vs. expose as read-only tools
- **Multi-turn tool loops** — e.g. "find something cheaper than what I'm viewing" requires read → query → view change
- **Streaming UX** — orchestrating streamed text + tool call execution + follow-up response
- **DataScript query design** — natural language → DataScript translation layer

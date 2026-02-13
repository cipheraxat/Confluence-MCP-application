# MCP + Confluence + LLM Integration Plan

Date: 2026-02-13

## Objective
Build an MCP workflow where an HTML client sends user requests to a local Java MCP server, the MCP server fetches Confluence data, sends context to an LLM (AWS Bedrock or Gemini), and returns processed responses back to the UI.

Target root source for retrieval:
- https://akshatanand.atlassian.net/wiki/spaces/~5e80e683cb85aa0c1448bd0f/pages/327681/Software+architecture+review

## Scope (from requirements)
1. **User Interface (HTML)** captures user input and selected tool/query options.
2. **Local MCP Server (Java)** authenticates with Confluence via API token and handles MCP client requests.
3. **Confluence Server (Java integration layer/client calls)** provides knowledge content.
4. **LLM (AWS Bedrock / Gemini)** summarizes or transforms retrieved Confluence content.
5. **Response to User** is returned to MCP client and rendered in UI.

## High-Level Architecture
- HTML page acts as MCP client.
- Local Java service exposes MCP-compatible endpoints (or request handlers).
- Java Confluence connector module performs authenticated fetches.
- Java LLM adapter module supports Bedrock and Gemini providers.
- Response formatter combines retrieval metadata + model output for UI display.

## Implementation Plan (before coding)

### Phase 1: Project Skeleton
- Create folders/modules:
  - `ui/` (HTML/CSS/JS client)
  - `server/` (Java MCP server)
  - `server/confluence/` (Confluence API client)
  - `server/llm/` (Bedrock + Gemini adapters)
  - `server/models/` (DTOs for requests/responses)
- Add configuration strategy for secrets:
  - environment variables for Confluence token, base URL, model provider keys.

### Phase 2: Define Contracts
- Define request contract from UI → MCP server:
  - user query
  - `provider` (required enum: `bedrock` | `gemini`)
  - selected source/tool scope
  - optional filters (space/page constraints)
- Define response contract MCP server → UI:
  - status
  - provider used (`bedrock` or `gemini`)
  - retrieved source metadata
  - processed LLM response
  - error details (if any)

### Phase 3: Confluence Integration
- Implement authenticated Confluence client in Java:
  - API token auth
  - fetch root page content (pageId: `327681`)
  - fetch all child pages under the root (recursive traversal)
  - search/fetch endpoint calls
  - robust error mapping (401/403/404/429/5xx)
- Normalize Confluence content for LLM input:
  - strip unsupported markup
  - truncate/chunk if needed
- Preserve hierarchy metadata for each page:
  - pageId
  - title
  - parentId
  - depth/path from root
  - source URL

### Phase 4: LLM Integration
- Implement provider abstraction:
  - `LLMProvider` interface
  - `BedrockProvider` implementation
  - `GeminiProvider` implementation
- Add prompt template for summarization/QA using retrieved Confluence context.
- Add provider selection logic via config or request parameter.

### Phase 5: MCP Request Orchestration
- Implement end-to-end handler in local MCP server:
  1. Validate incoming UI request
  2. Fetch root page + descendant pages from Confluence
  3. Build LLM prompt payload
  4. Invoke selected model
  5. Return structured response to UI

Traversal policy:
- Default scope: root page + all descendants.
- Optional controls: max depth, max pages, include/exclude specific branches.
- Deduplicate by pageId and avoid circular traversal.

### Phase 6: UI Flow
- Build a minimal HTML interface:
  - query input
  - provider selector with exactly two options: `Bedrock` and `Gemini`
  - submit button
  - response panel
- Add client-side fetch call to MCP server endpoint.
- Display loading, success, and error states.
- Require provider selection before submit; default to `Bedrock` if none is explicitly chosen.

### Phase 7: Validation & Hardening
- Test cases:
  - valid query returns summary
  - invalid token/auth failure handling
  - empty Confluence results
  - model timeout/failure fallback messaging
- Add logging and request correlation IDs.
- Ensure secrets are never returned to client.

## Deliverables
- Working HTML MCP client
- Local Java MCP server
- Java Confluence connector
- Bedrock + Gemini LLM adapters
- End-to-end response rendering in UI
- Basic README with run/config instructions

## Acceptance Criteria
- User can submit a query in UI and receive an LLM-processed response.
- Confluence data retrieval is authenticated and functional.
- Either Bedrock or Gemini can be selected and invoked.
- System retrieves data from the specified root page and pages under it.
- Errors are handled gracefully and shown in UI.
- No hardcoded secrets in source code.

## Notes
- Start with MVP paths first (single query flow), then extend with filtering/chunking optimizations.
- Keep modules loosely coupled so model providers can be swapped without changing core orchestration.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a ClojureScript application demonstrating the integration of DataScript (immutable in-memory database) with UIX (React wrapper for ClojureScript). The app is an organization chart visualizer that allows users to manage staff and departments with live reactive updates.

## Development Commands

### Running the app
```bash
npm run dev
```
Starts shadow-cljs in watch mode and serves the app at http://localhost:8080

### Building for production
```bash
npm run build
```
Compiles an optimized production build to `public/js/`

### REPL connection
```bash
npm run repl
```
Starts a ClojureScript REPL connected to the running app. The nREPL server runs on port 7002.

### clojure-mcp integration
This project is configured to work with clojure-mcp, which provides enhanced Clojure tooling for Claude Code:
- The `deps.edn` file includes the `:mcp` alias with clojure-mcp configuration
- Connection configured in `.mcp.json` to connect to nREPL on port 7002
- Provides REPL evaluation, namespace inspection, and code editing capabilities
- **Important**: The `deps.edn` file must include a top-level `:deps {}` map for clojure-mcp to recognize this as a valid Clojure project

### REPL + Browser Runtime Connection Workflow

When connecting the REPL to interact with the browser app state, follow this workflow to ensure you're connected to the correct runtime:

#### Understanding shadow-cljs Runtimes
- shadow-cljs maintains **separate runtime connections** for each open browser tab/window
- The REPL evaluates code in **one specific runtime** at a time (by default, the first that connected)
- File changes hot-reload to ALL runtimes, but REPL evaluations only go to the selected runtime
- Multiple browser tabs can be open simultaneously, each with its own runtime ID

#### Recommended Workflow

1. **Open the app in the browser**
   ```
   Navigate to http://localhost:8081
   ```

2. **Check the browser console for runtime ID**
   - Look for a message like: `shadow-cljs: #41 ready!`
   - This number (e.g., #41) is your runtime ID

3. **Verify runtime in shadow-cljs UI** (optional but recommended)
   ```
   Open http://localhost:9630/runtimes
   Confirm the runtime number and which build it's connected to
   ```

4. **Connect the REPL**
   ```clojure
   (require '[shadow.cljs.devtools.api :as shadow])
   (shadow/repl :app)
   ```

5. **Verify connection** (optional)
   ```clojure
   (in-ns 'datascript-app.core)
   (js/console.log "REPL connected!")
   ;; Check browser console to confirm this message appears
   ```

#### Troubleshooting

- **Changes not appearing in browser**: Check if you're connected to the correct runtime by comparing the runtime ID in the browser console with the shadow-cljs UI
- **Multiple tabs open**: Close extra tabs or explicitly select which runtime to use via the shadow-cljs UI at http://localhost:9630
- **"No application has connected" error**: Refresh the browser tab to reconnect the runtime

## Architecture

### Data Layer: DataScript
- **Schema** (`app-schema` in core.cljs:8-21): Defines the entity-attribute structure with unique constraints and reference types
  - Staff entities have `:staff/name` (unique) and `:staff/department` (ref to department)
  - Department entities have `:department/name` (unique), `:department/manager` (ref to staff), and `:department/enabled` (boolean)
  - Singleton app state uses `:app/selected-department` pattern with `:db/ident :app/singleton`

- **Rules** (core.cljs:24-30): DataScript logic rules for query composition, e.g., `manages-department` and `manages-staff`

- **Global connection** (`db` in core.cljs:33): Single DataScript connection atom shared across the app

### Reactivity Pattern: Live Queries
The key architectural insight is the `use-live-query` hook (core.cljs:205-213):
- Takes a query function as input
- Sets up a DataScript listener that re-runs the query on any transaction
- Returns reactive results that automatically update UIX components
- Cleanup function unlistens when component unmounts

This pattern bridges DataScript's immutable database with React's reactive rendering without requiring Om Next/Fulcro-style complexity.

### UI Layer: UIX
- Uses UIX's `defui` macro for defining React components
- Uses the `$` function for JSX-like element creation
- Component forms (e.g., `add-staff-form`, `add-department-form`) use controlled inputs with `uix/use-state`
- The `department-pill` component (core.cljs:187-201) demonstrates per-component reactivity with custom DataScript listeners

### Visualization: Mermaid
The `mermaid.cljs` namespace wraps the Mermaid.js library:
- `simple-graph` component generates Mermaid syntax from edge data
- Re-renders diagrams when edge data changes
- Used to visualize org chart relationships (department→staff, manager→department)

## Key Patterns

### Transaction functions
- `add-staff!` and `add-department!`: Write helpers that use `d/transact!`
- Use lookup refs like `[:department/name dep-name]` to reference entities by unique attributes
- Retractions use `[:db/retract entity-id attribute value]` pattern

### Query structure
Queries use Datalog with `:find`, `:in`, `:where` clauses. See examples:
- `get-all-staff` (core.cljs:58): Simple query finding all staff names
- `get-selected-staff` (core.cljs:72): Joins across relationships with filters
- `get-manager-dep-edges` (core.cljs:246): Multi-join query for visualization data

### App state management
Singleton app state (like selected department) uses a singleton entity pattern:
```clojure
{:db/ident :app/singleton
 :app/selected-department [:department/name "IT"]}
```

## Known Limitations (from README)

- **Reactivity granularity**: The `use-live-query` hook listens to ALL database changes, not specific attributes. This works but could be optimized for larger apps.
- **Singleton state**: Keeping app-level UI state (like selected department) in DataScript has slight impedance mismatch with typical React patterns but is workable.
- **No co-located queries**: Unlike Om Next/Fulcro, query fragments aren't co-located with components, requiring manual coordination.

## File Structure

- `src/main/datascript_app/core.cljs`: Main application logic, schema, queries, and UI components
- `src/main/datascript_app/mermaid.cljs`: Mermaid.js integration for diagram rendering
- `public/index.html`: HTML entry point with `#app` mount point
- `shadow-cljs.edn`: Build configuration

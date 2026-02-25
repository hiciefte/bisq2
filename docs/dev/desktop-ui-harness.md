# Desktop UI Harness (JavaFX)

This harness provides a Playwright-like loop for Bisq desktop UI work:

1. Start the real desktop app with deterministic settings.
2. Discover addressable UI nodes.
3. Trigger simple UI actions (`click`, `type`, `press-key`) over localhost.
4. Capture JavaFX scene screenshots for visual review.

It is intended for iterative UI development and user-perspective smoke checks.

## Build

```bash
./gradlew :apps:desktop:desktop-app:installDist
```

## Start / Stop

```bash
make desktop-ui-start
make desktop-ui-status
make desktop-ui-stop
```

Equivalent direct script:

```bash
./scripts/desktop-ui-harness.bash start
./scripts/desktop-ui-harness.bash status
./scripts/desktop-ui-harness.bash stop
```

## Core Commands

List automatable UI nodes (`id`, JavaFX class, text):

```bash
make desktop-ui-nodes
```

Type into an input field:

```bash
make desktop-ui-type id=chat-messages-input-field text="test prompt"
```

Click a button:

```bash
make desktop-ui-click id=chat-messages-send-button
```

Wait until a node exists (optionally visible):

```bash
make desktop-ui-wait-node id=logo-splash timeout_ms=30000 visible=true
```

Send a key to current focus (or to a target node id):

```bash
make desktop-ui-press-key key=ENTER
make desktop-ui-press-key key=ENTER id=chat-messages-input-field
```

Create a screenshot (saved under `/tmp/bisq2-ui-harness/artifacts` by default):

```bash
make desktop-ui-screenshot name=chat-after-send
```

Run a full UI scenario:

```bash
make desktop-ui-scenario file=scripts/scenarios/desktop-ui-smoke.scenario
```

## Runtime Design

When enabled, `desktop-app` starts a local automation server:

- Bind host/port: `127.0.0.1:18180` by default
- Token auth (required): header `X-Bisq-Automation-Token`
- Endpoints:
  - `GET /health`
  - `GET /nodes`
  - `POST /screenshot?name=...`
  - `POST /action/click?id=...`
  - `POST /action/type?id=...&text=...`
  - `POST /action/pressKey?key=ENTER&id=...`
  - `POST /wait/node?id=...&timeoutMs=...&visible=true|false`

The server is started only if:

```text
-Dapplication.desktop.automation.enabled=true
```

When enabled, a non-empty `-Dapplication.desktop.automation.token=<secret>` is required.
So normal app runs are unaffected.

## Determinism Controls

The harness defaults to:

- fixed window size (`1440x900`) for stable screenshots
- isolated data dir (`/tmp/bisq2-ui-harness/data`)
- fresh data reset on each `start` (`HARNESS_RESET_ON_START=1`) for deterministic runs
- isolated CLEAR P2P profile with auto-selected free local port (`19101-19250`)
  - seed addresses are pointed to self (`127.0.0.1:<same-port>`) to avoid accidental external peers

Override through environment variables:

- `HARNESS_DIR`, `DATA_DIR`, `ARTIFACTS_DIR`, `APP_NAME`
- `AUTOMATION_HOST`, `AUTOMATION_PORT`
- `WINDOW_WIDTH`, `WINDOW_HEIGHT`, `P2P_PORT`, `HARNESS_RESET_ON_START`
- `HARNESS_NETWORK_OPTS` (optional explicit network profile for local clusters)

## Best Practices For Reliable UI E2E

- Prefer explicit, stable UI node IDs for interactive controls.
- Do not rely on generated/randomized IDs in tests.
- Keep user-facing text assertions separate from interaction selectors.
- Use a dedicated harness data dir for reproducible runs.
- Capture screenshots at key states and on failure paths.

## Scenario Format

Scenario files are line-based command scripts with optional comments:

- blank lines and lines starting with `#` are ignored
- each command is the same as the harness CLI command
- arguments use shell-style tokenization; quote multi-word text
- supported commands:
  - `health`
  - `nodes`
  - `wait-node <id> [timeout_ms] [visible]`
  - `click <id>`
  - `type <id> <text...>`
  - `press-key <key> [id]`
  - `screenshot <name>`
  - `sleep <ms>`

Example (`scripts/scenarios/desktop-ui-smoke.scenario`):

```text
health
wait-node logo-splash 30000 true
type chat-messages-input-field "hello from scenario"
press-key ENTER chat-messages-input-field
screenshot smoke-logo
```

## Known Limits

- `click` currently guarantees `ButtonBase` fire behavior; non-button nodes are focus-targeted.
- `type` currently supports `TextInputControl`.
- `press-key` currently dispatches `KEY_PRESSED`/`KEY_RELEASED` for one `KeyCode`.
- For complex gestures (drag/drop, keyboard chords, context menus), extend the automation server APIs.

# Bisq API

REST API for testing and interacting with Bisq2.

## API Documentation

Access the interactive Swagger UI documentation at:

**Swagger UI:** `http://localhost:8090/doc/v1/index.html`

The Swagger UI provides:
- Complete API documentation
- Interactive endpoint testing
- Request/Response schemas
- Example values

## Quick Start

1. **Start the Bisq2 application** with API enabled
2. **Open Swagger UI** in your browser: `http://localhost:8090/doc/v1/index.html`
3. **Explore and test** the available endpoints

## Configuration

- **Default Port:** `8090`
- **Base URL:** `http://localhost:8090/api/v1`
- **Server Host:** `localhost` (configured in `api_app.conf` under `websocket.server.host`)

**Note:** The server is configured to use `localhost` instead of `0.0.0.0` to ensure Swagger UI can make API requests
from the browser without CORS issues.

### Docker-to-Host Integration Note

When API clients run in Docker and Bisq2 API runs on the host machine, `127.0.0.1`/`localhost` host binding is not
reachable from containers. For local headless integration from Dockerized clients, bind the Bisq2 API server to
`0.0.0.0` so container traffic can reach the host API port.

- Host-only usage (browser + Swagger on the same machine): `localhost` is fine.
- Dockerized client usage against host Bisq2 API: bind API to `0.0.0.0`.

## Support Chat Contract

Support chat integrations now use automatic AI handling in the support-agent backend.
The Bisq2 API contract only carries message and reaction data; no manual invocation metadata is exchanged.

WebSocket topics:

- `SUPPORT_CHAT_MESSAGES`
- `SUPPORT_CHAT_REACTIONS`

`SUPPORT_CHAT_MESSAGES` payload includes:

- `messageId`, `channelId`, `senderUserProfileId`
- `text`, `timestamp`, `citationMessageId` (nullable)

`SUPPORT_CHAT_REACTIONS` payload includes:

- `reaction`, `messageId`, `senderUserProfileId`, `channelId`

Support message send endpoint (`POST /api/v1/support/channels/{channelId}/messages`) accepts plain message text only and
returns `202 Accepted` (asynchronous publication).
Confidence and source details should be embedded by the upstream support-agent channel plugin as markdown within `text`,
so all markdown-capable clients (including older Bisq2 clients) can display that information without protocol extensions.

## Available API Categories

- **User Profile API** - Manage user profiles, report/ignore users
- **Market Price API** - Get market price quotes
- **Settings API** - User settings management
- **Payment Accounts API** - Payment account management
- **Explorer API** - Blockchain explorer data

## Example Endpoints

```
GET    /user-profiles?ids={ids}           - Get user profiles by IDs
POST   /user-profiles/report/{profileId}  - Report a user profile
GET    /market-price/quotes                - Get market price quotes
GET    /settings                           - Get user settings
GET    /payment-accounts                   - Get payment accounts
GET    /explorer/selected                  - Get selected explorer provider
GET    /explorer/tx/{txId}                 - Get transaction details
```

## Support

For more information:
- Check the Swagger documentation for detailed endpoint information
- Refer to the main Bisq2 project documentation
- Review the source code in the `api` module

# @zapier/zapier-sdk

TypeScript SDK for interacting with Zapier's API. See the [README](./README.md) for installation and quickstart instructions.

## Architecture & Mental Model

The SDK uses a **plugin-based architecture** where functionality is composed from modular plugins. Each plugin (like `listApps`, `runAction`, `apps`) adds methods to the SDK instance. This design allows for tree-shaking and modular extension.

**Key principles:**

- **Factory functions over classes**: Always use `createZapierSdk()` to create an SDK instance. The SDK never requires `new`.
- **Wrapped responses**: All SDK methods return `{ data }` wrappers (e.g., `{ data: app }` or `{ data: apps, nextCursor }`). This allows adding metadata in the future without breaking changes.
- **Single object parameters**: Methods take a single options object, making it easy to add parameters later without breaking existing code.

```typescript
// The SDK instance provides all methods via plugins
const zapier = createZapierSdk();

// Methods return wrapped responses
const { data: app } = await zapier.getApp({ app: "slack" });
const { data: apps, nextCursor } = await zapier.listApps();
```

## Authentication Flow

The SDK uses a unified `credentials` option that supports multiple authentication methods.

### Development: CLI Login (Recommended)

The easiest approach for local development. Run `npx zapier-sdk login` once, and the SDK automatically uses those credentials.

```typescript
// No credentials needed - SDK reads from CLI login automatically
const zapier = createZapierSdk();
```

The CLI stores tokens in `~/.zapier-sdk/config.json`. The SDK checks for this file when no credentials are provided.

### Production: Token String

For deployed applications with a pre-obtained token:

```typescript
// Option 1: Pass token directly via credentials
const zapier = createZapierSdk({ credentials: process.env.ZAPIER_CREDENTIALS });

// Option 2: Use ZAPIER_CREDENTIALS environment variable (SDK reads it automatically)
const zapier = createZapierSdk();
```

### Production: Client Credentials (OAuth)

For server-to-server authentication using OAuth client credentials flow:

```typescript
const zapier = createZapierSdk({
  credentials: {
    type: "client_credentials",
    clientId: process.env.ZAPIER_CREDENTIALS_CLIENT_ID,
    clientSecret: process.env.ZAPIER_CREDENTIALS_CLIENT_SECRET,
    // Optional: baseUrl and scope
  },
});
```

The SDK automatically exchanges these credentials for an access token and handles token refresh.

### Dynamic Credentials (Advanced)

For scenarios where credentials change at runtime (e.g., multi-tenant apps):

```typescript
const zapier = createZapierSdk({
  credentials: async () => {
    // Return a token string or credentials object
    return await getCredentialsForCurrentUser();
  },
});
```

### Credential Types Summary

| Type               | Use Case                | Example                                   |
| ------------------ | ----------------------- | ----------------------------------------- |
| String             | Pre-obtained token      | `credentials: "your_token"`               |
| Client Credentials | Server-to-server OAuth  | `credentials: { clientId, clientSecret }` |
| PKCE               | Interactive login (CLI) | `credentials: { clientId }` (no secret)   |
| Function           | Dynamic/lazy resolution | `credentials: async () => getToken()`     |

## Working with Paginated Results

Most list methods return paginated results. Understanding pagination is essential because some lists (like apps) have thousands of items.

### Single Page

```typescript
// Get first page only
const { data: apps, nextCursor } = await zapier.listApps();

// Get next page using cursor
const { data: moreApps } = await zapier.listApps({ cursor: nextCursor });
```

### Iterate All Pages

```typescript
// Iterate over pages (each page has { data, nextCursor })
for await (const page of zapier.listApps()) {
  console.log(`Got ${page.data.length} apps`);
}
```

### Iterate Individual Items

```typescript
// Iterate over individual items across all pages
for await (const app of zapier.listApps().items()) {
  console.log(app.title);
}

// Collect all items (be careful with large lists!)
const allApps = await Array.fromAsync(zapier.listApps().items());
```

### Limit Results

```typescript
// Limit to 50 total items across all pages
for await (const app of zapier.listApps({ maxItems: 50 }).items()) {
  console.log(app.title);
}
```

## The Apps Proxy Pattern

The `zapier.apps` proxy provides a type-safe, fluent way to execute actions. This is the recommended approach when you've generated TypeScript types for your apps.

### Basic Usage

```typescript
// Pattern: zapier.apps.{appKey}.{actionType}.{actionKey}(options)
const { data: channels } = await zapier.apps.slack.read.channels({
  connection: "123",
});
```

### Binding Connection

Instead of passing `connection` to every call, bind it once:

```typescript
// Create a bound app instance
const mySlack = zapier.apps.slack({ connection: "123" });

// Now all calls use that auth
const { data: channels } = await mySlack.read.channels({});
const { data: users } = await mySlack.search.user_by_email({
  inputs: { email: "user@example.com" },
});
```

### App Key Formats

Apps support multiple key formats. Use whichever is most convenient:

```typescript
// These are equivalent for Google Sheets:
zapier.apps.google_sheets; // snake_case (works as property)
zapier.apps["google-sheets"]; // slug with dashes
zapier.apps.GoogleSheetsV2CLIAPI; // implementation name
```

## App Keys and Discovery

Apps have multiple identifiers that can be used interchangeably:

- **Slug**: Human-friendly, like `slack` or `google-sheets`
- **Implementation name**: Internal identifier like `SlackCLIAPI` or `GoogleSheetsV2CLIAPI`
- **Snake_case**: Derived from slug, like `google_sheets`

### Finding Apps

```typescript
// Search by name
const { data: results } = await zapier.listApps({ search: "google sheets" });
// Results show all valid keys: "Google Sheets (GoogleSheetsV2CLIAPI, google-sheets, google_sheets)"

// Get specific app details
const { data: app } = await zapier.getApp({ app: "slack" });
```

### The Manifest (`.zapierrc`)

The manifest file locks app versions for reproducibility. When you run `npx zapier-sdk add slack`, it:

1. Records the current Slack app version in `.zapierrc`
2. Generates TypeScript types for that version

This ensures your code works with a known app version, not whatever happens to be latest.

## Type Generation Workflow

TypeScript types give you autocomplete for action inputs and outputs.

### Generate Types

```bash
# Add apps and generate types
npx zapier-sdk add slack google-sheets

# Types are created in src/zapier/apps/ or lib/zapier/apps/ by default
```

### Using Generated Types

After generation, the apps proxy becomes fully typed:

```typescript
// With generated types, you get autocomplete for:
// - Action names (read.channels, write.send_message, etc.)
// - Input field names and types
// - Output field types
const { data } = await zapier.apps.slack.write.send_message({
  connection: "123",
  inputs: {
    channel: "#general", // TypeScript knows this field exists
    text: "Hello!",
  },
});
```

### Regenerating Types

Run `add` again when:

- You need types for a new app
- An app has been updated and you want the latest fields
- You've deleted your generated types

## Named Connections

Named connections let you decouple workflow code from specific connection IDs. An external system (like an execution engine) provides the mapping via `.zapierrc`, and the workflow code references connections by name.

### Providing the Mapping

Connections are configured in `.zapierrc` alongside app version pins, or inline via the `manifest` option:

```typescript
const zapier = createZapierSdk({
  manifest: {
    apps: { slack: { implementationName: "SlackCLIAPI", version: "1.21.1" } },
    connections: {
      slack_work: { connectionId: 12345 },
      google_sheets: { connectionId: 67890 },
    },
  },
});
```

### Using Named Connections

```typescript
// Per-call with alias
await zapier.apps.slack.read.channels({ connection: "slack_work" });

// Factory binding
const slack = zapier.apps.slack({ connection: "slack_work" });
await slack.read.channels({});

// Lower-level API
await zapier.runAction({
  app: "slack",
  actionType: "read",
  action: "channels",
  connection: "slack_work",
});

// Direct numeric connection ID also works
await zapier.apps.slack.read.channels({ connection: 12345 });
```

### Resolution Precedence

For `connection`: explicit value > factory binding. String values are resolved from the connections map; numeric values are used directly as connection IDs. `connectionId` and `authenticationId` are deprecated aliases for `connection`.

App version is always resolved from the `.zapierrc` manifest, not from connections.

## Error Handling

### Debugging

Enable debug mode to see API requests and responses:

```typescript
const zapier = createZapierSdk({ debug: true });
```

Or set the environment variable:

```bash
DEBUG=zapier:* node your-script.js
```

### Common Errors

- **"No credentials provided"**: Run `npx zapier-sdk login` or provide credentials explicitly
- **"Connection not found"**: The `connection` doesn't exist or you don't have access
- **"App not found"**: Check the app key using `listApps({ search: "..." })`
- **"Action not found"**: Verify the action type and key with `listActions({ app: "..." })`

## Environment Variables

| Variable                           | Purpose                                                    |
| ---------------------------------- | ---------------------------------------------------------- |
| `ZAPIER_CREDENTIALS`               | Token string (alternative to passing `credentials` option) |
| `ZAPIER_CREDENTIALS_CLIENT_ID`     | OAuth client ID for client_credentials or PKCE flow        |
| `ZAPIER_CREDENTIALS_CLIENT_SECRET` | OAuth client secret (for client_credentials flow)          |
| `ZAPIER_CREDENTIALS_BASE_URL`      | Override auth base URL                                     |
| `ZAPIER_CREDENTIALS_SCOPE`         | OAuth scope                                                |
| `ZAPIER_BASE_URL`                  | Override API base URL (for testing)                        |
| `DEBUG`                            | Enable debug logging (`zapier:*` for all SDK logs)         |

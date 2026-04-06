# OIDC Authentication

AnyStream supports [OpenID Connect (OIDC)](https://openid.net/developers/how-connect-works/) for single sign-on (SSO)
authentication. This allows users to log in using an external identity provider such as
[Authentik](https://goauthentik.io/), [Keycloak](https://www.keycloak.org/),
[Authelia](https://www.authelia.com/), or any OIDC-compliant provider.

## Configuration

OIDC is configured in the AnyStream config file under the `oidc` section:

```hocon
app {
    oidc {
        enable = true
        provider {
            name = "my-provider"
            endpoint = "https://auth.example.com"
            client_id = "anystream"
            client_secret = "your-client-secret"
            admin_group = "anystream-admin"
            viewer_group = "anystream-viewer"
            groups_field = "groups"
            username_fields = ["preferred_username", "username"]
            scopes = ["openid", "profile", "email", "groups"]
        }
    }
}
```

### Provider Settings

| Setting           | Default                                    | Description                                                                                                                                                           |
|-------------------|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`            | (required)                                 | A name for this provider, used internally for routing.                                                                                                                |
| `endpoint`        | (required)                                 | The base URL of your OIDC provider. AnyStream uses [discovery](https://openid.net/specs/openid-connect-discovery-1_0.html) to find all other endpoints automatically. |
| `client_id`       | (required)                                 | The OAuth2 client ID registered with your provider.                                                                                                                   |
| `client_secret`   | (required)                                 | The OAuth2 client secret.                                                                                                                                             |
| `admin_group`     | `anystream-admin`                          | The group name that grants admin (Global) permissions.                                                                                                                |
| `viewer_group`    | `anystream-viewer`                         | The group name that grants viewer (ViewCollection) permissions.                                                                                                       |
| `groups_field`    | `groups`                                   | The claim field in the OIDC user info response that contains group membership.                                                                                        |
| `username_fields` | `["preferred_username", "username"]`       | A list of claim fields to try (in order) to determine the username.                                                                                                   |
| `scopes`          | `["openid", "profile", "email", "groups"]` | The OAuth2 scopes to request from the provider.                                                                                                                       |

## Setting Up Your Provider

### General Steps

1. Create a new OAuth2/OIDC client application in your identity provider.
2. Set the **redirect URI** to: `https://your-anystream-url/api/users/oidc/callback`
3. Ensure the client is configured to include group membership in the user info response or ID token.
4. Create the groups referenced by `admin_group` and `viewer_group` in your provider and assign users to them.
5. Copy the client ID and secret into your AnyStream config.

### Base URL Requirement

When using OIDC, you must set `BASE_URL` in your AnyStream configuration (or ensure proper forwarded headers
are being passed) so that the callback URL is generated correctly.

## How It Works

When OIDC is enabled, a provider-based login option appears on the AnyStream login screen alongside the
standard username/password login.

- **New users**: When a user logs in via OIDC for the first time, an AnyStream account is created automatically.
  Permissions are assigned based on the user's group membership in the identity provider.
- **Returning users**: On subsequent logins, a new session is created for the existing AnyStream account.

Users authenticated via OIDC use their provider's username (determined by `username_fields`). Permissions are
assigned based on group membership at the time of account creation.

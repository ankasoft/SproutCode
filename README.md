# SproutCode

An Android SSH terminal client with Hetzner Cloud provisioning. Connect to remote servers, spin up new VMs, and manage your infrastructure — all from your phone.

## Features

### Terminal
- Full `xterm-256color` terminal emulator (Termux library)
- SSH shell via JSch with PTY resizing
- Pinch-to-zoom font size adjustment
- Hardware keyboard support with Ctrl key combinations

### Server Management
- Add servers manually (IP, username, password or SSH key auth)
- Edit and delete saved servers
- Persistent encrypted storage for all server credentials

### Hetzner Cloud Integration
- Create servers directly from the app — SSH key injected automatically
- Browse live locations, server types, and OS images from the API
- Monitor server initialization and wait for SSH readiness before opening terminal
- Delete servers from Hetzner Cloud directly from the server list
- Optional: auto-clone a GitHub repository on first boot via `user_data` (cloud-init)

### SSH Key Management
- RSA 4096 key pair auto-generated on first launch
- Private key stored encrypted on device only
- Public key copyable for manual server configuration
- Key regeneration with confirmation

### Settings
- Hetzner API token and default server preferences
- GitHub Personal Access Token for private repo cloning
- Light / dark theme toggle
- All sensitive data stored in Android `EncryptedSharedPreferences`

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| SSH | JSch (mwiede fork) + BouncyCastle |
| Terminal | Termux Terminal View |
| Crypto storage | AndroidX Security EncryptedSharedPreferences |
| HTTP | `HttpURLConnection` (no extra deps) |
| Async | Kotlin Coroutines + StateFlow |

## Requirements

- Android 8.0+ (API 26)
- Hetzner Cloud account + API token for cloud provisioning (optional)
- GitHub Personal Access Token with `repo` scope for private repo cloning (optional)

## Getting Started

1. Clone the repo and open in Android Studio.
2. Build and run on a device or emulator (API 26+).
3. On the server list screen, tap **+** to add a server manually or create one on Hetzner.
4. For Hetzner: go to **Settings**, enter your API token, and tap **Fetch from Hetzner** to load available options.

## Architecture

MVVM with Jetpack ViewModels and a simple layered structure:

```
data/          — models, stores (ServerStore, SshKeyStore, HetznerConfigStore, AppPrefs)
ssh/           — SshManager, SshProbe (TCP + auth readiness checks)
hetzner/       — HetznerClient, models
ui/
  serverlist/  — server list screen + VM
  servercreate/— Hetzner create flow + VM
  serveredit/  — manual add/edit screen + VM
  settings/    — settings screen + VM
  terminal/    — terminal screen + VM
```

## License

MIT

# SproutCode

**Run AI coding agents from your phone.**
Spin up a Linux server on Hetzner, clone your repo, and hand it off to Claude Code, Opencode, or Kilo Code — all without touching a laptop.

## The Use Case: Agentic Coding on Demand

Tools like **Claude Code**, **Opencode**, and **Kilo Code** run best on a real Linux server with a full environment. SproutCode lets you spin that up and tear it down in minutes — entirely from your phone:

1. **Create a server** on Hetzner Cloud with one tap — SSH key injected automatically
2. **Clone your repo** at boot time — provide a GitHub URL and it's ready when the terminal opens
3. **Open the terminal** — run your AI coding agent directly over SSH
4. **Delete the server** when done — no ongoing cost, no leftover VM

No laptop needed. No pre-provisioned VM sitting idle. Just a clean server when you need it, gone when you don't.

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
- Create servers directly from the app — app's SSH key injected automatically
- Browse live locations, server types, and OS images from the Hetzner API
- Monitor server initialization and wait for SSH readiness before opening terminal
- Delete servers from Hetzner Cloud directly from the server list
- Auto-clone a GitHub repository on first boot via `user_data` (cloud-init)

### SSH Key Management
- RSA 4096 key pair auto-generated on first launch
- Private key stored encrypted on device only
- Public key copyable for manual server configuration
- Key regeneration with confirmation dialog

### Settings
- Hetzner API token and default server preferences (location, type, image)
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

## Privacy

SproutCode collects no data whatsoever. Everything stays on your device:

- SSH credentials and server list are stored in Android `EncryptedSharedPreferences` — never leave the device
- Your SSH private key is generated locally and never transmitted anywhere
- Hetzner and GitHub tokens are stored encrypted on-device only
- There are no analytics, no crash reporting, no telemetry, no backend

The only outbound connections are the ones you initiate: SSH to your servers and Hetzner Cloud API calls on your behalf.

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

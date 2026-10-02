<div align="center">

# DSH-Fusion

[简体中文](./README.md) | [English](./README.en.md)

**A launcher for DeepSeek Harness on Android**

</div>

DSH-Fusion puts [DeepSeek Harness](https://www.npmjs.com/package/@deepseek-ai/dsh) (a Node.js coding-agent CLI) on your phone:
it downloads a Linux container runtime (arm64 or x86_64, matched to your device), starts \`dsh web\` inside the container with proot / proroot, and you open its Web UI directly on the phone.

No root required, no Termux required. When root / Shizuku / wireless ADB are available they are used opportunistically to relax certain restricted operations.

> This project is an independently maintained merge: it keeps the original DSH-Folk native UI and
> containerized design, and brings in the dsh-mobile-apk runtime implementation logic (engine watchdog,
> config snapshot rollback, bridge compatibility) plus the @dsh-android plugin family.
> Sources & licenses: [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md). Merge architecture: [docs/MERGE.md](docs/MERGE.md).

## Table of Contents

- [Preview](#preview)
- [What it can do](#what-it-can-do)
- [Requirements](#requirements)
- [Installation](#installation)
- [Runtime management](#runtime-management)
- [DM feature plugins](#dm-feature-plugins)
- [Deep dive](#deep-dive)
- [Project layout](#project-layout)
- [Acknowledgments](#acknowledgments) · [License](#license) · [Community](#community)

Deep implementation notes live under \`docs/\`, one topic per file (see [Deep dive](#deep-dive)).

## Preview

<div align="center">

| Home | Terminal |
| :---: | :---: |
| <img src="docs/screenshots/home.jpg" width="260" alt="Home: start / stop, run mode, privilege channels and startup log"> | <img src="docs/screenshots/terminal.jpg" width="260" alt="Terminal: real PTY inside the container with ESC / TAB / CTRL / arrow keys"> |
| **Plugins** | **Settings** |
| <img src="docs/screenshots/plugins.jpg" width="260" alt="Plugins: installed list with weekly npm downloads, stars and update status"> | <img src="docs/screenshots/settings.jpg" width="260" alt="Settings: general / appearance / behavior / features / security / backup / plugins / media"> |

</div>

## What it can do

| Page | Description |
| --- | --- |
| **Home** | One-tap start / stop / restart DSH, shows run phase, Web UI address, current run mode and privilege channels, with a copyable startup log |
| **Terminal** | Real PTY inside the container (based on Termux \`terminal-view\`), \`bash\` straight into the container |
| **Plugins** | Manage DSH plugins in the container, showing weekly npm downloads, GitHub stars and dsh-market likes; built-in plugin store (full catalog downloaded and searched locally, 4000+ entries) with upstream-scanned capabilities and security red lines, plus local .tgz install. After install it verifies the plugin tree once on a temp port and uninstalls on failure |
| **Settings** | General / Appearance / Behavior / Features / Security / Backup / Plugins / Media; theme system stays compatible with FolkPatch (\`theme.json\`) |

Theme store entry is at **Settings → Appearance** top-right; theme archives (\`.fpt\`) export/import live in the store top bar.

UI language is chosen at **Settings → General → Language**, independent of the system language (a few "flavor" skins are only reachable from there).

**Config backup** uses the same export format as the DSH desktop \`dsh-config-manager\` plugin — a zip exported on the phone imports on a computer and vice versa; credential values are excluded by default, optional whole-archive AES-256-GCM encryption. Why the middle step is done by the app: [Why the app encrypts backups itself](docs/backup.md).

## Requirements

- Android 8.0 (API 26) or higher
- **arm64-v8a** or **x86_64** device (32-bit unsupported)
- First launch downloads the runtime over the network (~150 MB archive, ~600 MB extracted; mirror selection / auto speed test available in settings)
- 2 GB+ free storage recommended

After first-launch runtime download, four plugins are preinstalled: \`dsh-web-mobile\` (mobile adaptation), \`dshmarket\` (in-WebUI plugin market), \`dsh-config-manager\` (**dependency of the backup feature**), \`dsh-file-upload\` (drag-drop upload / document-to-Markdown / image OCR / voice input). Failure doesn't block startup — install manually from the store later; the preinstall ledger tracks each package by name so upgrades install newly added ones.

root / Shizuku / wireless ADB are all **optional** and **disabled by default**. The app only detects and reuses existing su (Magisk / KernelSU / APatch) and authorized Shizuku / Sui; it never patches the kernel, installs su, or bundles the Shizuku server. Choose a channel at **Settings → Security → Privilege channel → Preferred channel** (or "Auto" to pick root > Shizuku > wireless ADB).

With a channel selected, both the app (hardware monitor, dmesg/tombstones in bugreport, restart menu) and **the AI in the container** (via \`dsh-native shell\` executed by the app) can use it; the container itself doesn't need root. Strictness decides whether you're asked before use — default **strict** (prompt on every privileged call). Full permission model, the two wireless-ADB locks and which host capabilities the AI can reach: [What the container can access on the host](docs/host-bridges.md).

## Installation

Download the APK for **your architecture** from [Releases](https://github.com/byxumi/dsh-fusion/releases/latest); the \`.sha256\` files alongside can be used to verify:

- \`DSH-Fusion-<version>-arm64-v8a.apk\` — most phones and tablets
- \`DSH-Fusion-<version>-x86_64.apk\` — Android emulators, Android-x86, ChromeOS

Both packages are functionally identical; they differ only in bundled native binaries and the container rootfs. Wrong architecture shows "Unsupported architecture" at startup and exits. Not sure? Arm64-v8a on phones.

Development builds are also available from [Actions](https://github.com/byxumi/dsh-fusion/actions/workflows/build.yml): pick a successful run and download the \`dsh-fusion-debug-*\` artifacts.

**Beta channel**: app betas only appear after enabling **Settings → General → Accept beta updates** (default off); container-runtime betas are a separate switch (**Settings → Features → Runtime → Accept beta runtime updates**).

App self-update is at **Settings → General → Check for updates**: it measures latency/throughput of download channels (GitHub direct / gh-proxy mirror), supports resumable downloads, and refuses to install unless the release \`.sha256\` matches.

## Runtime management

The card at **Settings → Features → Runtime**:

- **Update**: one button, three uses. No update found → "Check for updates"; update found → confirm dialog (target version + kept data) before download; **long-press** lists every runtime version in the repo for arbitrary switching (downgrade included).
- **Reinstall**: re-download the latest runtime for the current channel, optionally keeping or wiping sessions, plugins, config and dependency data.
- **Import**: install a local tar.gz (trusted source), showing filename and size for confirmation first.
- **Auto-check for updates**: separate switch, default **on**; checks at startup and only prompts when a new version exists. Download still needs manual confirmation.

The line \`dsh web: http://127.0.0.1:3080/?token=…\` in the log is the token used to open the WebUI — it's the password of this instance (LAN access off by default, reachable only locally). Remove it before pasting logs for help.

## DM feature plugins

This merged project ships the dsh-mobile-apk @dsh-android plugin family sources under \`merge/dm/\`; they are built to tgz by \`.github/workflows/plugins.yml\` CI and installed **locally** from the plugin store:

- Trigger \`plugins.yml\` (workflow_dispatch) and download the \`dm-plugins\` artifact (12 tgz files);
- Switch/install the **beta** channel runtime (0.2.x, required by @dsh-android peer dependencies) at **Settings → Features → Runtime**;
- Install the corresponding \`.tgz\` from the plugin store; the tree is verified on a temp port and auto-uninstalled on failure.

See [docs/MERGE.md](docs/MERGE.md) section 4.

## Deep dive

Topic-per-file under \`docs/\`:

- [What the container can access on the host (dsh-fs / dsh-native)](docs/host-bridges.md) — two loopback bridges, 24 native capabilities, privileged command (\`shell\`) and self-elevation flow
- [Why the app encrypts backups itself](docs/backup.md) — export/import format, \`DCA1\` container, snapshot-based settings rollback, WebDAV cloud backup plugin
- [Start on boot](docs/autostart.md) — boot broadcast / accessibility / boot script paths and the two accessibility switches
- [Log collection & redaction](docs/logs.md) — bugreport file ownership, redaction, time-window trimming and \`session.lock\`
- [How it runs](docs/architecture.md) — proot/proroot, rootfs, link2symlink, pnpm, ELF closure and the rest of the run chain
- [Merge architecture with dsh-mobile-apk](docs/MERGE.md) — design and implementation of this merge

## Project layout

\`\`\`
app/src/main/java/me/bmax/apatch/
  dsh/                   runtime layer: download/install, proot launch, permission probe, PTY, config backup
  dsh/merge/             merge layer: engine watchdog / config snapshot rollback / androidBridge compat (dm runtime logic)
  ui/screen/HomeDsh.kt   home
  ui/screen/Dsh*.kt      terminal / plugins / plugin store
  ui/screen/settings/    settings pages
merge/dm/                dsh-mobile-apk assets: @dsh-android plugins, vendor, web compat, runtime-logic references
runtime-builder/         container rootfs build scripts (run on CI) + dynamic-lib closure check
.github/workflows/       build.yml (APK) + plugins.yml (dm plugin tgz) + runtime.yml (rootfs)
docs/                    topic-wise implementation notes
\`\`\`

## Acknowledgments

This project is independently maintained; sources and licenses are listed in [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md).
Core sources: DSH-Folk (UI / containerized design), dsh-mobile-apk (runtime logic & plugins), FolkPatch / DeepSeek Harness.

## License

[GNU General Public License v3.0](./LICENSE). This project derives from the GPL-3.0 FolkPatch and therefore stays GPL-3.0 as a whole: redistribution (including modified copies) must stay GPLv3 and provide full source.

## Community

- Issues: https://github.com/byxumi/dsh-fusion/issues

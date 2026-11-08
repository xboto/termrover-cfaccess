# TermRover CF Access

A [Vector](https://github.com/JingMatrix/Vector) / libxposed module for [TermRover](https://termrover.sh) that adds:

- **Cloudflare Access** for `cf:hostname` hosts, through `cloudflared access tcp`. Hosts can also be switched to Access from the host form.
- **Telemetry blocking** for Firebase and Google Play Services analytics endpoints.
- **Custom themes** (optional, off by default): import your own terminal themes from a JSON file through the module's companion screen. Turning the switch on or off closes TermRover; the change applies the next time it opens.

## Requirements

- Android arm64 with Vector (API 102) or a compatible framework
- TermRover 1.4.1, 1.4.0 or 1.3.1 (the module detects the build and uses its R8 names)
- Module scope: `sh.termrover.app`

## Installation

1. Install the APK, enable it in Vector and add TermRover to its scope.
2. Restart TermRover (after a fresh install or update of TermRover, **reboot the device**).
3. On a host, use `cf:your-hostname` as the address or turn on the Cloudflare Access switch.
4. Themes: Vector → module → Open companion app → turn on **Custom themes**, then Import a `termrover-themes` v1 JSON file. A ready-made example is [`examples/custom-themes.json`](examples/custom-themes.json) (edit or trim it as you like; it is not bundled in the APK).

### After reinstalling or updating TermRover

Reinstalling TermRover assigns it a new app UID. Vector attaches to the new
process but does not load modules into it until the device is rebooted.

If you reinstall or update TermRover, **reboot the device** before opening it.

### Sideload install (non-Play)

When installing TermRover APKs manually via `adb` outside of Google Play, pass
`-i com.android.vending` so the system registers Google Play Store as the
installer:

```bash
adb install-multiple -r -i com.android.vending \
  base.apk split_config.arm64_v8a.apk split_config.es.apk split_config.xxhdpi.apk
```

Then **reboot the device** before launching TermRover.

## Why telemetry blocking

TermRover's **About & Diagnostics → Share crash & usage data** switch controls Firebase Crashlytics and Firebase Analytics. Google's DataTransport channel runs on its own and keeps uploading with the switch off, including queued Google Play Billing library events (device, locale, network and build fingerprint).

The module turns Firebase off before it starts, keeps the About switch off, stops the DataTransport upload and blocks the telemetry hosts.

## Theme file format

Themes are imported from a `termrover-themes` v1 JSON file (`"format": "termrover-themes"`).
The file is not part of the APK: you pick it in the companion app. Start from
[`examples/custom-themes.json`](examples/custom-themes.json) if you want a full
sample to edit, export, or share.

```json
{
  "format": "termrover-themes",
  "version": 1,
  "themes": [
    {
      "id": "my-palenight-dark",
      "name": "Palenight",
      "terminal": {
        "foreground": "#a6accd",
        "background": "#292d3e",
        "cursor": "#ffcb6b",
        "ansi": ["#292d3e", "… 16 colors: 0-7 normal, 8-15 bright"]
      },
      "chrome": {
        "bgScreen": "#1b1e2b", "bgChrome": "#292d3e", "bgPanel": "#34384c",
        "bgElevated": "#2e3245", "bgKey": "#34384c", "bgKeyPressed": "#3e4256",
        "border": "#393d51", "hairline": "#3e4256",
        "fgText": "#a6accd", "fgMute": "#676e95", "fgFade": "#8a92b8",
        "green": "#c3e88d", "amber": "#ffcb6b", "red": "#f07178",
        "cyan": "#82aaff", "prefixInk": "#000000"
      }
    }
  ]
}
```

Rules:

- `id`: matches `[a-z0-9][a-z0-9-]{0,47}` and is unique. Use your own prefix; an id equal to a built-in TermRover theme is skipped.
- `name`: 1 to 40 characters, shown in the theme picker.
- Colors: `#rrggbb` or `#aarrggbb`.
- `terminal.ansi` holds exactly 16 colors, and all 16 `chrome` keys are required.
- Up to 300 themes and 1 MB per file. Extra fields are ignored.

| `chrome` key | Role |
|---|---|
| `bgScreen` | Deepest background (screen) |
| `bgChrome` | Bars and general UI background |
| `bgPanel`, `bgElevated` | Cards and panels |
| `bgKey`, `bgKeyPressed` | Extra-keys bar keys, pressed state |
| `border`, `hairline` | Borders and separators |
| `fgText`, `fgMute`, `fgFade` | Primary, muted and secondary text |
| `green`, `amber`, `red` | Success, warning, error |
| `cyan` | Accent (selection, buttons, active state) |
| `prefixInk` | Text drawn on the accent color |

After importing or deleting themes, close and reopen TermRover. The custom themes appear at the end of Settings → Theme.

## Building

The `cloudflared` binary is kept out of git. Download it before building:

```bash
./scripts/fetch_cloudflared.sh
./gradlew :app:assembleRelease
```

The script fetches the official `cloudflared-linux-arm64` release and checks its SHA-256.
cloudflared is licensed under Apache 2.0 by Cloudflare.

`assembleRelease` produces `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with
your own key before installing (the `debug` build type is signed with the Android debug key):

```bash
apksigner sign --ks my-release.jks --out TermRoverCfAccess.apk app-release-unsigned.apk
apksigner verify -v TermRoverCfAccess.apk
```

Releases are signed with the author's key, so an APK you build and sign yourself has a
different signature and installs as a separate upgrade path (uninstall first when switching).

## License

MIT (see `LICENSE`). The bundled `cloudflared` binary (fetched at build time) remains
Apache 2.0 by Cloudflare.

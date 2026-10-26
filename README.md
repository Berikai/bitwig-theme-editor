# Bitwig Studio Theme Editor

<img align="right" width="370" alt="Bitwig Theme Editor Preview" src="https://github.com/user-attachments/assets/a031566b-f7d8-4ff3-aa78-353c6149e6e0" />

A theme editor for **Bitwig Studio**, written in Java.

[Latest Download](https://github.com/Berikai/bitwig-theme-editor/releases)

## Requirements

- Bitwig Studio 4.x, 5.x, or 6.x
- **Java 8** or later (64-bit)

## Quick Start

1. Run `bitwig-theme-editor.jar`
2. Click **Select bitwig.jar** and choose your Bitwig Studio installation's `bitwig.jar` file
3. Browse and install themes directly from the **Community Themes** tab, import a `.bte`/`.json` theme via **File → Import file**, or create your own in the **Colors** tab

> Tip: Use **Switch / Re-patch** in the top-right bar anytime to target another Bitwig Studio installation, and check **Options → Enable global theme file** to share one `theme.bte` across all patched Bitwig versions.

---

## Themes

### Where to find themes?
- Directly inside the **Community Themes** tab in the app
- On GitHub in [awesome-bitwig-themes](https://github.com/Berikai/awesome-bitwig-themes)
- More themes on [Bitwig Community Discord](https://discord.com/invite/bitwig-121395249003233280), in `#your-themes` channel.

### Manual theming (`.bte` files)
You can also manage themes manually after patching Bitwig Studio:

1. Run patched Bitwig Studio once to initialize defaults
2. Locate `default.bte` in the config directory:
   - **Windows:** `%APPDATA%\.bitwig-theme-editor\versions\<version>\`
   - **Linux:** `~/.config/bitwig-theme-editor/versions/<version>/` 
       - (or `$XDG_CONFIG_HOME/bitwig-theme-editor/versions/<version>/`, or `~/.bitwig-theme-editor/versions/<version>/` if already existing)
   - **macOS:** `~/.bitwig-theme-editor/versions/<version>/`

   See [Config directory](#config-directory) for the full resolution order, migration rules and overrides.
3. Create or edit `theme.bte` in the same directory (or in `.bitwig-theme-editor/theme.bte` if **Global Theme** is enabled)
4. Add or modify `Color Name: #hex` lines and save
5. Click the **Dashboard Button** or resize the Bitwig Studio window to apply changes

### External Helper Tools
- **[Polarity's Theme Editor Helper](https://polarity.github.io/bitwig-theme-editor-helper/main.html):** A browser utility to batch-adjust, tint, and transform colors across `.bte` and `.json` theme files.
- **[Bitwig Theme Editor Web UI](https://bw5.berikai.dev):** A web-based theme editor / previewer to inspect and modify theme colors online. *(Bitwig 5.x / `.json`)*

---

## Commands (Advanced)

- **GUI mode (default):**
  ```sh
  java -jar bitwig-theme-editor.jar
  ```
- **Direct CLI patching:**
  ```sh
  java -jar bitwig-theme-editor.jar [--global-theme] [--config-dir <path>] <bitwig-jar-path>
  ```
  - `--global-theme` *(optional)*: Share a single `~/.bitwig-theme-editor/theme.bte` (or `%APPDATA%\.bitwig-theme-editor\theme.bte`) across all patched Bitwig Studio installations.
  - `--config-dir <path>` *(optional)*: Override the config directory. Can also be set with the `BTE_CONFIG_DIR` environment variable (see [Config directory](#config-directory)).

> Note: `apply` and `export` are deprecated. The theme editor patches `bitwig.jar` once, and patched Bitwig Studio watches `theme.bte` for changes.

---

<details>

<summary>Config directory</summary>

### Config directory

Bitwig Theme Editor keeps its configuration, downloaded themes and per-version `.bte` files in one directory. It is resolved in this order:

| Priority | Platform | Directory |
| --- | --- | --- |
| 1 | Any | `--config-dir <path>` command-line option |
| 2 | Any | `BTE_CONFIG_DIR` environment variable |
| 3 | Windows | `%APPDATA%\.bitwig-theme-editor` (falls back to `~\.bitwig-theme-editor` when `APPDATA` is unset) |
| 4 | macOS | `~/.bitwig-theme-editor` (kept for backwards compatibility) |
| 5 | Linux / Unix | `~/.bitwig-theme-editor` when it already contains user data |
| 6 | Linux / Unix | `$XDG_CONFIG_HOME/bitwig-theme-editor`, defaulting to `~/.config/bitwig-theme-editor` |

Notes:

- The resolved directory is printed on startup, for example `Config directory: /home/<username>/.config/bitwig-theme-editor (XDG_CONFIG_HOME)`.
- A leading `~` in `BTE_CONFIG_DIR` or `--config-dir` is expanded, and relative values are resolved against your home directory.
- Per the [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/), `XDG_CONFIG_HOME` must be an absolute path; relative values are ignored with a warning.
- On Linux, an existing `~/.bitwig-theme-editor` is only kept when it contains data. Empty leftovers are ignored, and if both locations contain data the legacy one wins with a warning.
- The config directory is compiled into the patched `bitwig.jar`. If you change it, re-patch Bitwig Studio (the GUI does this on launch) so both use the same directory, and set the override consistently (shell profile, desktop entry, ...).

</details>

<details>

<summary>Development</summary>

### Built With
- **Java JDK**
- **IntelliJ IDEA Community Edition** (recommended IDE)

### How to Build
- Open the project in your Java IDE or run `./gradlew build` / the Gradle `jar` task.

</details>

<details>
<summary>Contribution</summary>

Pull requests are **welcome!** Feel free to contribute and help improve the project.

</details>

<details>
<summary>Looking for the old version? (1.x)</summary>

<sup>[Legacy Download](https://github.com/Berikai/bitwig-theme-editor/releases/tag/1.4.3) · [Source Code](https://github.com/Berikai/bitwig-theme-editor/tree/1.4.3)</sup>

</details>

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

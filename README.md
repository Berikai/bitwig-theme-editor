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
  java -jar bitwig-theme-editor.jar [--global-theme] <bitwig-jar-path>
  ```
  - `--global-theme` *(optional)*: Share a single `~/.bitwig-theme-editor/theme.bte` (or `%APPDATA%\.bitwig-theme-editor\theme.bte`) across all patched Bitwig Studio installations.

> Note: `apply` and `export` are deprecated. The theme editor patches `bitwig.jar` once, and patched Bitwig Studio watches `theme.bte` for changes.

---

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

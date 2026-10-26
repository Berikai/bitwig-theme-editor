# Bitwig Studio Theme Editor

<img align="right" width="370" alt="Bitwig Theme Editor Preview" src="https://github.com/user-attachments/assets/8b147fd9-be26-435d-a2d9-f3d7ebcb48ff" />

A theme editor for **Bitwig Studio**, written in Java.

[Latest Download](https://github.com/Berikai/bitwig-theme-editor/releases)

### Requirements

- Bitwig Studio 4.x, 5.x or 6.x
- **Java 8** or later (64-bit)

### Usage

Simple steps to get started:

- **Step1:** Run *bitwig-theme-editor.jar*

- **Step2:** Click on the "Select bitwig.jar" button to choose your Bitwig Studio installation's `bitwig.jar` file.

- **Step3:** [Download a theme](https://github.com/Berikai/awesome-bitwig-themes), or create your own using the editor.

## More About

<details>

<summary>Themes</summary>

### Where to find themes?
Community-made themes are available [here](https://github.com/Berikai/awesome-bitwig-themes).

### How to create, or apply themes?
You can use theme editor user interface to import themes, or change colors and create your own theme!

<img width="226" alt="Bitwig Theme Editor MenuBar" src="https://github.com/user-attachments/assets/6924190d-7d87-4692-899d-33e060a1186a" />

<img width="226" alt="Bitwig Theme Editor MenuBar2" src="https://github.com/user-attachments/assets/defa60b8-e221-427e-88e7-e516dd7d265a" />

<img width="300" alt="image" src="https://github.com/user-attachments/assets/79390de0-2a25-463a-ac56-fd89d89aef0f" />

You can also manually manage the theme after patching Bitwig Studio:

- **Step 1:** Run Bitwig Studio
- **Step 2:** A file named `default.bte` will be created in the Bitwig settings directory
    - **Windows:** `%APPDATA%\.bitwig-theme-editor\versions\<bitwig-version>\`
    - **Linux:** `~/.config/bitwig-theme-editor/versions/<bitwig-version>/`, or `$XDG_CONFIG_HOME/bitwig-theme-editor/versions/<bitwig-version>/` when `XDG_CONFIG_HOME` is set
    - **macOS:** `~/.bitwig-theme-editor/versions/<bitwig-version>/`

    See [Config directory](#config-directory) for the full resolution order, migration rules and overrides.
- **Step 3:** Create a file named `theme.bte` in the same directory if doesn't exists
- **Step 4:** Add the lines of the color values you want to change, modify, and save
- **Step 4:** Click on the "Dashboard Button" or resize the window to render changes

Happy theming!

</details>

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

<summary>Commands (advanced)</summary>

- Enter GUI mode (default): `java -jar bitwig-theme-editor.jar`

- Direct patching command (CLI): `java -jar bitwig-theme-editor.jar <bitwig-jar-path>`

- Use a custom config directory: `java -jar bitwig-theme-editor.jar --config-dir <path> [<bitwig-jar-path>]`

> The config directory can also be set with the `BTE_CONFIG_DIR` environment variable. See [Config directory](#config-directory).

> Note: `apply` or `export` are deprecated. The theme editor now patches the jar once, then patched Bitwig Studio watches the **theme.bte** file for changes.

</details>

<details>

<summary>Development</summary>

### Built With
- **Java 8 JDK**
- **IntelliJ IDEA Community Edition** (recommended IDE)

### How to Build

- Open the project in a Java IDE of your choice.
- Use `jar` task defined in Gradle to build the project.

</details>

<details>

<summary>Contribution</summary>

Pull requests are **welcome!**  
Feel free to contribute and help me improve the project.

</details>

<details><summary>Looking for the old version? (1.x)</summary>

<sup>[Legacy Download](https://github.com/Berikai/bitwig-theme-editor/releases/tag/1.4.3)   [Source Code](https://github.com/Berikai/bitwig-theme-editor/tree/1.4.3)</sup>

</details>

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

# Bitwig Studio Theme Editor

<img align="right" width="370" alt="Bitwig Theme Editor Preview" src="https://github.com/user-attachments/assets/8b147fd9-be26-435d-a2d9-f3d7ebcb48ff" />

A theme editor for **Bitwig Studio**, written in Java.

[Latest Download](https://github.com/Berikai/bitwig-theme-editor/releases)

### Requirements

- Bitwig Studio 4.x, 5.x or 6.x
- **Java 8** or later (64-bit)

### Usage

Simple steps to get started:

- **Step 1:** Run *bitwig-theme-editor.jar*

- **Step 2:** Click on the "Select bitwig.jar" button to choose your Bitwig Studio installation's `bitwig.jar` file.

- **Step 3:** [Download a theme](https://github.com/Berikai/awesome-bitwig-themes), or create your own using the editor.

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
    - **Windows:** `C:\Users\<username>\AppData\Roaming\.bitwig-theme-editor\<version>\`
    - **Linux:** `~/.config/bitwig-theme-editor/<version>/` (or `$XDG_CONFIG_HOME/bitwig-theme-editor/<version>/`, or `~/.bitwig-theme-editor/<version>/` if already existing)
    - **macOS:** `/Users/<username>/.bitwig-theme-editor/<version>/`
    *(Note: You can override the base directory on any OS using the `BTE_CONFIG_DIR` environment variable)*
- **Step 3:** Create a file named `theme.bte` in the same directory if doesn't exists
- **Step 4:** Add the lines of the color values you want to change, modify, and save
- **Step 4:** Click on the "Dashboard Button" or resize the window to render changes

### Looking for more tools to create themes?

If you are designing or tweaking themes, these web tools can help streamline the process:

- **[Polarity's Theme Editor Helper](https://polarity.github.io/bitwig-theme-editor-helper/main.html):** A browser utility to batch-adjust, tint, and transform colors across `.bte` and `.json` theme files.
- **[Bitwig Theme Editor Web UI](https://bw5.berikai.dev):** A web-based theme editor / previewer to inspect and modify theme colors online. **(version 5.x / .json)**

Happy theming!

</details>

<details>

<summary>Commands (advanced)</summary>

- Enter GUI mode (default): `java -jar bitwig-theme-editor.jar`

- Direct patching command (CLI): `java -jar bitwig-theme-editor.jar <bitwig-jar-path>`

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

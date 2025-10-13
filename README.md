# Bitwig Studio Theme Editor

A theme editor for **Bitwig Studio**, written in Java.

> ⚠ **Warning:** Please back up your **bitwig.jar** file before using this program, just in case.

---

## 📥 Download

Download the latest version from the [releases page](https://github.com/Berikai/bitwig-theme-editor/releases/2.0.0-dev2)

#### 🕰️ Looking for the old version? (1.x)
<sup>[Legacy Download](https://github.com/Berikai/bitwig-theme-editor/releases/tag/1.4.3)   [Source Code](https://github.com/Berikai/bitwig-theme-editor/tree/1.4.3-stable)</sup>

### ⚙️ Requirements

- Bitwig Studio 4.x, 5.x or 6.x
- **Java 8** or later

---

## 🚀 Usage

Run the program by **double-clicking**, or executing the following command in terminal:

```bash
java -jar bitwig-theme-editor.jar
```

You can also provide the path to your **bitwig.jar** to patch directly (advanced users):

```bash
java -jar bitwig-theme-editor.jar <bitwig-jar-path>
```

---

## 🎨 Themes

### 📍 Where to find themes?
Community-made themes are available [here](https://github.com/Berikai/awesome-bitwig-themes).


### 🛠 How to create themes?
You can use theme editor user interface to change colors and create your own theme!

Or, you can manually create the theme file after patching Bitwig Studio:

1. Run Bitwig Studio
2. A file named `default.bte` will be created in the directory of `bitwig.jar`
3. Create a file named `theme.bte` in the same directory
4. Add the lines of the color values you want to change, modify, and save
5. Click on the "Dashboard Button" or resize the window to render changes

Happy theming!

---

## 🛠 Development

### 🏗 Built With
- **Java 8 JDK**
- **IntelliJ IDEA Community Edition** (recommended IDE)

### 🔹 How to Build

- Open the project in a Java IDE of your choice.
- Use `jar` task defined in Gradle to build the project.

---

## 🤝 Contribution

Pull requests are **welcome!**  
Feel free to contribute and help me improve the project.

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

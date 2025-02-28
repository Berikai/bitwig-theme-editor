# Bitwig Studio Theme Editor

<img align="right" src="https://github.com/Berikai/bitwig-theme-editor/assets/18515671/8c76c8c6-30b4-43cf-9043-17759e744d75" width="50%" alt="UI preview" />

A theme editor for **Bitwig Studio**, written in Java.  
Bitwig meets themes, finally!  

> ⚠ **Warning:** Please back up your **bitwig.jar** file before using this app, just in case.

---

## 📥 Download  

[![GitHub release](https://img.shields.io/github/release/Berikai/bitwig-theme-editor.svg)](https://github.com/Berikai/bitwig-theme-editor/releases/latest)  

Download the latest version from the [Releases page](https://github.com/Berikai/bitwig-theme-editor/releases).  

---

## ⚙️ Requirements  

- **Java 8** or a newer version installed on your system.  
- Compatible with **any Bitwig Studio version** (theoretically), though not extensively tested.  

---

## 🚀 Usage  

The app can be used via a simple UI by running it without any parameters.  

Alternatively, you can run it via the command line:  

```bash
java -jar bitwig-theme-editor.jar <bitwig-jar-path> [command] <theme-path>
```

### 🔹 Available Commands  

| Command  | Description |
|----------|------------|
| `export` | Exports the current Bitwig theme to a file (overwrites existing file). |
| `apply`  | Applies a new theme by modifying **bitwig.jar** with the specified theme file. |

### 🔹 Example Usage  

Export the current theme:  
```bash
java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar export current-bitwig-theme.yaml
```

Apply a new theme:  
```bash
java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar apply custom-theme.json
```

---

## 🎨 Themes  

### 📍 Where to find themes?  
Community-made themes are available [here](https://github.com/Berikai/awesome-bitwig-themes).  

### 🛠 How to create themes?  
You can use the **visual theme editor**:  
➡ [WebUI](https://berikai.github.io/bitwig-theme-editor-webui/)  

Alternatively, you can manually edit a theme file by:  
1. **Exporting** the theme from your **bitwig.jar** using Bitwig Theme Editor.  
2. **Modifying** the color values in the exported file.  
3. **Applying** the theme back to Bitwig using the editor.  

---

## 📖 How It Works  

1. Run the **export** command to extract the current Bitwig theme from **bitwig.jar**.  
   - The exported file will be in **JSON** or **YAML** format.  
2. Modify the color values in the exported theme file.  
3. Run the **apply** command to apply the edited theme.  

🎉 **Done! Your Bitwig Studio now has a custom theme!**  

> **Note:** RGBA values (instead of RGB) are **not supported** since it may cause UI glitches.  
> However, you can use RGBA where it is already present in the theme file.  

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

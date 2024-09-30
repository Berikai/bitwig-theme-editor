<h1 align="left">Bitwig Studio Theme Editor</h1>

<img align="right" src="https://github.com/Berikai/bitwig-theme-editor/assets/18515671/8c76c8c6-30b4-43cf-9043-17759e744d75" width="50%" alt="ui" />

A theme editor for Bitwig Studio, written in java. Bitwig meets themes, finally!

> :warning: Please back up your **bitwig.jar** file before running the app, just in case.

## Download

[![GitHub release](https://img.shields.io/github/release/Berikai/bitwig-theme-editor.svg)](https://github.com/Berikai/bitwig-theme-editor/releases/latest)

Head to the [Releases page](https://github.com/Berikai/bitwig-theme-editor/releases) to download the latest version.

## Requirements

You need Java 8 or a higher version installed on your computer.

It should work with any Bitwig Studio version in theory, but it has not been tested extensively yet.

## Themes

### Where to find themes?

You can find themes made by community [here](https://github.com/Berikai/awesome-bitwig-themes).

### How to create themes?

You can check the in development visual theme editor [here](https://berikai.github.io/bitwig-theme-editor-webui/)!

Another way of creating themes is editing a theme file. You can do this by exporting the theme file via Bitwig Theme Editor from your **bitwig.jar** and changing color values on the exported theme file.

## Usage

You can run the app directly without parameters to use it with a simple UI.

Alternatively, you can run:
```bash
java -jar bitwig-theme-editor.jar <bitwig-jar-path> [command] <theme-path>
```

**Available commands**:

- export: Exports the current theme to the specified file path (overwrites existing file). 
- apply: Applies the theme by modifying the bitwig.jar file based on the specified theme file.

**Example usage**:
```bash
java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar export current-bitwig-theme.yaml
# or
java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar apply current-bitwig-theme.json
```

### Brief Explanation

First, run the app using the `export` command to create theme file based on your **bitwig.jar** file. This file will be in JSON or YAML format depending on your choice. Then, edit the color values in the theme file to your liking. After that, run the app with the `apply` command. 

Voilà! You have themed your Bitwig!

> Note: Using RGBA values instead of RGB values is not supported, as it may cause glitches in the Bitwig UI. However, you can use RGBA values where RGBA values are already in use.

## Development

This project is built using the following tools.

- IntelliJ IDEA Community Edition
- Java 8 JDK

Open the project with a Java IDE of your choice; IntelliJ IDEA is recommended. You can build the project with the `jar` task via Gradle.

## Contribution

Pull requests are welcome!

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.


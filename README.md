
# Bitwig Studio Theme Editor

A theme editor for Bitwig Studio, written in java. Bitwig meets themes, finally!

It should work with any version in theory, but it has not been tested extensively yet.

> Please back up your **bitwig.jar** file before running the app, just in case.

## Usage

Run:
```bash
java -jar bitwig-theme-editor.jar <bitwig-jar-path> [command] <theme-path>
```

**Available commands**:

- export-window: Exports the current window theme to the specified file path (overwrites existing file). 
- export-arranger: Exports the current arranger theme to the specified file path (overwrites existing file). 
- update-window: Updates the window theme by modifying the bitwig.jar file based on the specified theme file. 
- update-arranger: Updates the arranger theme by modifying the bitwig.jar file based on the specified theme file.

**Example usage**:
```bash
java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar export-window current-bitwig-theme.json
```

### Brief Explanation

First, run the app using the `export-window` and `export-arranger` commands to create theme files based on your **bitwig.jar** file. These files will be in JSON format. Then, edit the color values in the theme files to your liking. After that, run the app with the `update-window` and `update-arranger` commands. Voilà! You have themed your Bitwig!

> Note: Using RGBA values instead of RGB values is not supported, as it may cause glitches in the Bitwig UI. However, you can use RGBA values where RGBA values are already in use.

## TODO

- Tidy up the code; it is currently a mess.
- Create a basic Swing UI 
  - Next step for this is visual theming

## Contribution

Pull requests are welcome!

## Example Themes

Example themes can be found in [Themes](themes) folder

#### Bitwig Default

![Default Theme](themes/default.png)

#### Medium Blue

![Medium Blue Theme](themes/medium_blue.png)

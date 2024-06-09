
# Bitwig Studio Theme Editor

A theme editor for Bitwig Studio, written in java. Bitwig meets themes, finally!

Should work with any versions in theory, but not tested enough yet.

> Please back up your bitwig.jar file before running the app, just in case.

## Usage

Run: `java -jar bitwig-theme-editor.jar <bitwig-jar-path> [command] <theme-path>`

Available commands: export-window, export-arranger, update-window, update-arranger

```  
    export-window      Exports current window theme into given file path. (overrides)  
    export-arranger    Exports current arranger theme into given file path. (overrides)  
    update-window      Updates window theme by editing bitwig.jar file due to given theme file.  
    update-arranger    Updates arranger theme by editing bitwig.jar file due to given theme file.  
```  

Example usage: `java -jar bitwig-theme-editor.jar /opt/bitwig-studio/bin/bitwig.jar export-window current-bitwig-theme.json`

### Brief Explanation

First, run the app with `export-window` and `export-arranger` commands to create theme files due to your **bitwig.jar** file. These files are in JSON format. Then, edit the color values in theme files to your liking. After that, run the app with `update-window` and `update-arranger` commands. Voilà! You have themed your Bitwig!


- Greyscale values can not be changed to any RGB values (e.g. #696969, has 3 repeating sections made of 69, so can only be changed to something like #7a7a7a, until it is implemented)

## TODO

- Add support for GREYSCALE to RGB conversion in bytecode manipulation step (in ThemeClass)
- Tidy up the code since it is a mess
- Create a basic Swing UI interface (Next step for this is visual themeing)

## Contribution

Pull requests are welcome!

## Example Themes

Example themes can be found in [Themes](themes) folder

#### Bitwig Default

![Default Theme](themes/default.png)

#### Medium Blue

![Medium Blue Theme](themes/medium_blue.png)

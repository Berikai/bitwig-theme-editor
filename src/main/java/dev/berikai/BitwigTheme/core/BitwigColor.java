package dev.berikai.BitwigTheme.core;

public class BitwigColor {

    public static final int UNKNOWN = -1;
    public static final int GREYSCALE = 0;
    public static final int RGB = 1;
    public static final int RGBA = 2;

    private final String hex;

    private final int red;
    private final int green;
    private final int blue;
    private final int alpha;

    private final int type;

    public BitwigColor(String hex) {
        this.hex = hex;

        String hexCode = hex.substring(1);

        if (hexCode.length() == 2) {
            this.type = GREYSCALE;
            this.red = Integer.valueOf(hexCode.substring(0, 2), 16);
            this.green = Integer.valueOf(hexCode.substring(0, 2), 16);
            this.blue = Integer.valueOf(hexCode.substring(0, 2), 16);
            this.alpha = 255;
            return;
        }

        if (hexCode.length() == 6) {
            this.type = RGB;
            this.red = Integer.valueOf(hexCode.substring(0, 2), 16);
            this.green = Integer.valueOf(hexCode.substring(2, 4), 16);
            this.blue = Integer.valueOf(hexCode.substring(4, 6), 16);
            this.alpha = 255;
            return;
        }

        if (hexCode.length() == 8) {
            this.type = RGBA;
            this.red = Integer.valueOf(hexCode.substring(0, 2), 16);
            this.green = Integer.valueOf(hexCode.substring(2, 4), 16);
            this.blue = Integer.valueOf(hexCode.substring(4, 6), 16);
            this.alpha = Integer.valueOf(hexCode.substring(6, 8), 16);
            return;
        }

        this.type = UNKNOWN;
        this.red = 255;
        this.green = 255;
        this.blue = 255;
        this.alpha = 255;
    }

    public BitwigColor(int grey) {
        this.hex = "#" + String.format("%02x", grey) + String.format("%02x", grey) + String.format("%02x", grey);

        this.type = GREYSCALE;
        this.red = grey;
        this.green = grey;
        this.blue = grey;
        this.alpha = 255;
    }

    public BitwigColor(int red, int green, int blue) {
        this.hex = "#" + String.format("%02x", red) + String.format("%02x", green) + String.format("%02x", blue);

        this.type = RGB;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = 255;
    }

    public BitwigColor(int red, int green, int blue, int alpha) {
        this.hex = "#" + String.format("%02x", red) + String.format("%02x", green) + String.format("%02x", blue) + String.format("%02x", alpha);

        this.type = RGBA;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public int getType() {
        return this.type;
    }

    public int[] getRGB() {
        return new int[]{this.red, this.green, this.blue, this.alpha};
    }

    public String getHex() {
        return this.hex;
    }

}

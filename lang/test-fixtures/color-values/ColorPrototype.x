/**
 * L77 fixture only: byte-valued sRGB with straight (unpremultiplied) alpha.
 * This is not a public XDK graphics/color API.
 */
module ColorPrototype {
    const Rgba(UInt8 red, UInt8 green, UInt8 blue, UInt8 alpha = 255) {}

    Rgba accent = new Rgba(255, 128, 0);

    Rgba translucent() {
        return new Rgba(blue = 40, red = 200, alpha = 128, green = 80);
    }

    // Dynamic values and color-looking text deliberately have no swatch.
    Rgba dynamic(UInt8 red) = new Rgba(red, 128, 0);
    String ordinaryText = "#ff8000";
}

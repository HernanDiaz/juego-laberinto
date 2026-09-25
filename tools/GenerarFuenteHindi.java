import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Genera una fuente bitmap para hindi (devanagari).
 *
 * El devanagari necesita "shaping": las letras se combinan (conjuntos, matras que cambian de sitio...),
 * y BitmapFont de libGDX no sabe hacerlo. Así que cada grupo de letras (akshara) que aparece en los
 * textos del juego se dibuja ya combinado con Java2D y se guarda como un único glyph en el área de uso
 * privado de Unicode (U+E000...). El juego sustituye cada akshara por su glyph (ver Strings.kt).
 * Los caracteres latinos (números, signos) se toman de la fuente pixel latina (segunda página).
 *
 * Uso: java tools/GenerarFuenteHindi.java <textos.txt> <fuente.ttf> <tamaño> <carpeta assets>
 * Salida: font_hi.png, font_hi.fnt, font_hi.map
 */
public class GenerarFuenteHindi {
    private static final String CONS = "[\\u0915-\\u0939\\u0958-\\u095F\\u0978-\\u097F]\\u093C?";
    private static final Pattern CLUSTER = Pattern.compile(
            "(?:" + CONS + "(?:\\u094D[\\u200C\\u200D]?" + CONS + ")*|[\\u0904-\\u0914\\u0960\\u0961\\u0972-\\u0977])"
                    + "[\\u0900-\\u0903\\u093A\\u093B\\u093E-\\u094F\\u0955-\\u0957\\u0962\\u0963]*");

    private static final int ATLAS_W = 512;
    private static final int LATIN_CAP_CENTER = 6; // centro de las mayúsculas en la celda latina de 11 px
    private static final int LATIN_CELL = 11;

    record Glyph(int id, String text, BufferedImage img, Rectangle bounds, int advance) {}

    public static void main(String[] args) throws Exception {
        String text = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File(args[1])).deriveFont(Float.parseFloat(args[2]));
        Path out = Path.of(args[3]);
        FontRenderContext frc = new FontRenderContext(null, false, false);

        // 1. Aksharas usados en los textos (y otros caracteres no latinos sueltos, como el danda ।)
        TreeSet<String> clusters = new TreeSet<>();
        Matcher m = CLUSTER.matcher(text);
        int last = 0;
        while (m.find()) {
            addLoose(clusters, text.substring(last, m.start()));
            clusters.add(m.group());
            last = m.end();
        }
        addLoose(clusters, text.substring(last));

        // 2. Dibujar cada akshara ya combinado
        List<Glyph> glyphs = new ArrayList<>();
        int id = 0xE000;
        for (String c : clusters) {
            TextLayout tl = new TextLayout(c, font, frc);
            Rectangle b = tl.getPixelBounds(frc, 0, 0);
            if (b.width <= 0 || b.height <= 0) b = new Rectangle(0, 0, 1, 1);
            BufferedImage img = new BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(Color.WHITE);
            tl.draw(g, -b.x, -b.y);
            g.dispose();
            glyphs.add(new Glyph(id++, c, img, b, Math.round(tl.getAdvance())));
        }

        // 3. Alineación vertical: el centro del cuerpo de las letras (entre la línea superior y la base)
        //    coincide con el centro de las mayúsculas latinas.
        Rectangle ka = new TextLayout("क", font, frc).getPixelBounds(frc, 0, 0);
        double bodyCenter = ka.y / 2.0; // relativo a la línea base (negativo = arriba)
        int maxAbove = 0, maxBelow = 0;
        for (Glyph gl : glyphs) {
            maxAbove = Math.max(maxAbove, -gl.bounds.y);
            maxBelow = Math.max(maxBelow, gl.bounds.y + gl.bounds.height);
        }
        int baseline = maxAbove;
        int latinTop = (int) Math.round(baseline + bodyCenter - LATIN_CAP_CENTER);
        if (latinTop < 0) {
            baseline -= latinTop;
            latinTop = 0;
        }
        int lineHeight = Math.max(baseline + maxBelow, latinTop + LATIN_CELL) + 2;

        // 4. Empaquetar en el atlas
        int x = 0, y = 0, rowH = 0;
        int[][] pos = new int[glyphs.size()][2];
        for (int i = 0; i < glyphs.size(); i++) {
            BufferedImage img = glyphs.get(i).img;
            if (x + img.getWidth() + 1 > ATLAS_W) {
                x = 0;
                y += rowH + 1;
                rowH = 0;
            }
            pos[i][0] = x;
            pos[i][1] = y;
            x += img.getWidth() + 1;
            rowH = Math.max(rowH, img.getHeight());
        }
        int atlasH = Integer.highestOneBit(Math.max(1, y + rowH)) * 2;
        BufferedImage atlas = new BufferedImage(ATLAS_W, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag = atlas.createGraphics();
        for (int i = 0; i < glyphs.size(); i++) ag.drawImage(glyphs.get(i).img, pos[i][0], pos[i][1], null);
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("font_hi.png").toFile());

        // 5. .fnt: página 0 = aksharas, página 1 = fuente latina
        List<String> lines = new ArrayList<>();
        List<String> latin = new ArrayList<>();
        for (String l : Files.readAllLines(out.resolve("font.fnt"), StandardCharsets.UTF_8)) {
            if (l.startsWith("char id=")) {
                latin.add(l.replace("yoffset=0", "yoffset=" + latinTop).replace("page=0", "page=1"));
            }
        }
        lines.add("info face=\"hindi\" size=" + (int) font.getSize2D() + " bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1");
        lines.add("common lineHeight=" + lineHeight + " base=" + baseline + " scaleW=" + ATLAS_W + " scaleH=" + atlasH + " pages=2 packed=0");
        lines.add("page id=0 file=\"font_hi.png\"");
        lines.add("page id=1 file=\"font.png\"");
        lines.add("chars count=" + (glyphs.size() + latin.size()));
        lines.addAll(latin);
        StringBuilder map = new StringBuilder();
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph gl = glyphs.get(i);
            lines.add("char id=" + gl.id + " x=" + pos[i][0] + " y=" + pos[i][1]
                    + " width=" + gl.img.getWidth() + " height=" + gl.img.getHeight()
                    + " xoffset=" + gl.bounds.x + " yoffset=" + (baseline + gl.bounds.y)
                    + " xadvance=" + gl.advance + " page=0 chnl=0");
            map.append(Integer.toHexString(gl.id)).append('\t').append(gl.text).append('\n');
        }
        Files.write(out.resolve("font_hi.fnt"), lines, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("font_hi.map"), map, StandardCharsets.UTF_8);
        System.out.println("hindi: " + glyphs.size() + " aksharas, lineHeight=" + lineHeight + ", latinTop=" + latinTop);
    }

    private static void addLoose(TreeSet<String> set, String s) {
        s.codePoints().filter(cp -> cp > 0xFF && !Character.isWhitespace(cp))
                .forEach(cp -> set.add(new String(Character.toChars(cp))));
    }
}

package me.deprilula28.gamesrob.commands;

import com.github.kevinsawicki.http.HttpRequest;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

public class ImageCommands {
    public static final int USER_PROFILE_HEIGHT = 150;
    public static final int USER_PROFILE_WIDTH = 800;
    public static final int PFP_SIZE = 109;

    private static final Color GUILD_FALLBACK_BACKGROUND_COLOR = new Color(0x2f3136);

    public static int drawBackground(Graphics2D g2d, String background, int width, int height, int blurryHeight, boolean user) {
        if (!user && background == null) {
            g2d.setColor(GUILD_FALLBACK_BACKGROUND_COLOR);
            g2d.fillRect(0, 0, width, height);
            return -1;
        }
        Image image = getImage("userprofilebg_" + background, background == null
                ? ImageCommands.class.getResourceAsStream("/imggen/shapesbackground.png")
                : HttpRequest.get(background).userAgent(Constants.USER_AGENT).stream());
        Image blurryImage = getImage("userprofilebgblurry_" + background, background == null
                ? ImageCommands.class.getResourceAsStream("/imggen/shapesbackgroundblurry.png")
                : HttpRequest.get(background + "blurry").userAgent(Constants.USER_AGENT).stream());

        int relHeight = (int) (image.getHeight(null) / ((double) image.getWidth(null)
                / USER_PROFILE_WIDTH));
        int y = relHeight / 2 - height / 2;
        int x = width / 2 - USER_PROFILE_WIDTH / 2;

        if (user) {
            // Blurry (bottom) background
            drawTileImage(image, g2d, 0, 0, width, USER_PROFILE_HEIGHT, 0, -relHeight / 2, width, relHeight);
            drawTileImage(blurryImage, g2d, 0, USER_PROFILE_HEIGHT, width, blurryHeight - USER_PROFILE_HEIGHT,
                    0, -relHeight / 2 - USER_PROFILE_HEIGHT, width, relHeight);

            // Avatar blurry background
            y = -relHeight / 2 - height / 2;
            g2d.setClip(new Ellipse2D.Float(x + 12, 8, (float) PFP_SIZE, (float) PFP_SIZE));
            g2d.drawImage(blurryImage, 0, y, width, relHeight, null);
            g2d.setClip(null);
        } else {
            drawTileImage(image, g2d, 0, 0, width, USER_PROFILE_HEIGHT, 0, -relHeight / 2, width, relHeight);
            drawTileImage(blurryImage, g2d, 0, USER_PROFILE_HEIGHT, width, blurryHeight - USER_PROFILE_HEIGHT,
                    0, -relHeight / 2 - USER_PROFILE_HEIGHT, width, relHeight);
        }

        return x;
    }

    private static final int USER_PROFILE_FONT_SIZE = 60;

    public static void drawUserProfile(Member member, Graphics2D g2d, int x, int y, Font starlight) {
        Optional<String> nick = Optional.ofNullable(member.getNickname());
        User user = member.getUser();
        Image template = getImage("uptemplate", ImageCommands.class.getResourceAsStream("/imggen/userprofiletemplate.png"));
        g2d.drawImage(template, x, y, null);

        g2d.setClip(new Ellipse2D.Float(x + 12, y + 8, (float) PFP_SIZE, (float) PFP_SIZE));
        g2d.drawImage(getImage("pfp_" + user.getId(), HttpRequest.get(user.getEffectiveAvatarUrl()).userAgent(Constants.USER_AGENT).stream()),
                x + 12, y + 8, PFP_SIZE, PFP_SIZE, null);
        g2d.setClip(null);

        // [nick]/name
        int maxNameWidth = 350;
        g2d.setColor(Color.WHITE);
        g2d.setFont(starlight.deriveFont((float) USER_PROFILE_FONT_SIZE).deriveFont(Font.PLAIN));
        g2d.drawString(Utility.truncateLength(nick.orElseGet(user::getName), maxNameWidth, g2d.getFontMetrics()),
                x + 200, y + 85);
        int oldHeight = g2d.getFontMetrics().getHeight();
        int fullWidth = Math.min(maxNameWidth + g2d.getFontMetrics().stringWidth("..."),
                g2d.getFontMetrics().stringWidth(nick.orElseGet(user::getName)));
        // [name]#tagg
        g2d.setColor(new Color(0x90A4AE));
        g2d.setFont(starlight.deriveFont(20F));
        g2d.drawString(nick.map(it -> user.getName()).orElse("") + "#" + user.getDiscriminator(),
                x + 210 + fullWidth, y + 110 - g2d.getFontMetrics().getHeight());
    }

    private static final Color[] POSITION_COLORS = {
            new Color(0xFFA000), new Color(0x607D8B), new Color(0x3E2723),
            new Color(55, 71, 79, 200)
    };

    private static final double TITLE_SPACE = 0.25;
    private static final double ITEMS_SPACE = 1.0 - TITLE_SPACE;

    public static void drawLeaderboardEntry(Optional<Member> member, String title, UserProfile.GameStatistics stats, int position,
                                            Graphics2D g2d, int x, int y, int width, Font starlight, String language) {
        member.ifPresent(it -> {
            String background = UserProfile.get(it.getUser()).getBackgroundImageUrl();
            Image image = getImage("userprofilebgblurry_" + background, background == null
                    ? ImageCommands.class.getResourceAsStream("/imggen/shapesbackgroundblurry.png")
                    : HttpRequest.get(background + "blurry").userAgent(Constants.USER_AGENT).stream());
            int relHeight = (int) (image.getHeight(null) / ((double) image.getWidth(null) / width));
            drawTileImage(image, g2d, x, y, width, LEADERBOARD_ENTRY_HEIGHT, 0, -relHeight / 2, width, relHeight);
            g2d.setClip(null);
        });

        drawLeaderboardEntry(member.isPresent() ? Optional.empty()
                : Optional.of(POSITION_COLORS[position < 0 ? 3 : Math.min(position, 3)]),
                title, g2d, x, y, width, starlight, language,
                () -> position >= 0 ? (position + 1) + Utility.formatNth(language, position + 1)
                        : Language.transl(language, "command.profile.unranked"),
                () -> stats.victories, () -> stats.losses, () -> stats.gamesPlayed,
                () -> new BigDecimal(stats.getWonPercent()).setScale(1, BigDecimal.ROUND_HALF_UP).toString() + "%");
    }

    public static final int LEADERBOARD_ENTRY_HEIGHT = 50;
    public static final int LEADERBOARD_ENTRY_FONT_SIZE = 30;
    public static final int LEADERBOARD_BORDERS = 10;

    public static void drawLeaderboardEntry(Optional<Color> backgroundColor, String title, Graphics2D g2d,
                                             int x, int y, int width, Font starlight, String language, Supplier... suppliers) {
        g2d.setFont(starlight.deriveFont((float) LEADERBOARD_ENTRY_FONT_SIZE).deriveFont(Font.PLAIN));
        backgroundColor.ifPresent(color -> drawQuad(g2d, color, x, y, width, LEADERBOARD_ENTRY_HEIGHT));

        g2d.setColor(Color.white);
        int texty = y + LEADERBOARD_ENTRY_HEIGHT - LEADERBOARD_BORDERS - g2d.getFontMetrics().getHeight() / 4;
        int textbasex = x + LEADERBOARD_BORDERS;
        g2d.drawString(title, textbasex, texty);

        final int startSpace = (int) (textbasex + TITLE_SPACE * width);
        final double itemSpace = ITEMS_SPACE / suppliers.length;
        for (int i = 0; i < suppliers.length; i++) {
            String str = String.valueOf(suppliers[i].get());
            drawCenteredString(g2d, str, startSpace + (int) ((itemSpace * i) * width), texty, (int) itemSpace);
        }
    }

    public static Image getImage(String cacheName, InputStream is) {
        return Cache.get(cacheName, n -> {
            try {
                Image image = ImageIO.read(is);
                Utility.quietlyClose(is);
                return image;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static Image getImageFromWebsite(String path) {
        return getImage(path, HttpRequest.get(Constants.GAMESROB_DOMAIN + "/" + path).userAgent(Constants.USER_AGENT)
                .stream());
    }

    public static void drawTileImage(Image image, Graphics2D g2d, int x, int y, int width, int height,
                                    int u, int v, int tileWidth, int tileHeight) {
        g2d.setClip(new Rectangle2D.Float((float) x, (float) y, (float) width, (float) height));
        g2d.drawImage(image, x + u, y + v, tileWidth, tileHeight, null);

        int bottom = y + v + tileHeight;
        if (bottom < y + height) g2d.drawImage(image, x + u, y + v + tileHeight, tileWidth, tileHeight, null);
    }

    public static void drawCenteredString(Graphics2D g2d, String text, int x, int y, int width) {
        g2d.drawString(text, x + (width / 2 - g2d.getFontMetrics().stringWidth(text) / 2), y);
    }

    public static void drawQuad(Graphics2D g2d, Color color, int x, int y, int width, int height) {
        g2d.setColor(color);
        g2d.fillRect(x, y, width, height);
    }
}

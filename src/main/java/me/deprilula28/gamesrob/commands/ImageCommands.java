package me.deprilula28.gamesrob.commands;

import com.github.kevinsawicki.http.HttpRequest;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.LeaderboardHandler;
import me.deprilula28.gamesrob.data.UserProfile;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrob.utility.Constants;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.User;

import javax.imageio.ImageIO;
import javax.xml.ws.Provider;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public class ImageCommands {
    private static final int USER_PROFILE_HEIGHT = 150;
    private static final int USER_PROFILE_WIDTH = 800;

    private static final int PROFILE_GAMES_IN_LINE = 4;
    private static final int PROFILE_GAMES_BORDER_PX = 30;

    private static final int PROFILE_GAME_LINE_HEIGHT = 30;
    private static final int PROFILE_GAME_LINE_DISTANCE = 5;
    private static final int PROFILE_GAME_LINES = 4;
    private static final int PROFILE_GAME_HEIGHT = (PROFILE_GAME_LINE_HEIGHT * (PROFILE_GAME_LINES + 1) +
            PROFILE_GAME_LINE_DISTANCE * (PROFILE_GAME_LINES - 1));

    static final int PROFILE_COMMAND_WIDTH = USER_PROFILE_WIDTH + 100;
    static final int PROFILE_COMMAND_HEIGHT = USER_PROFILE_HEIGHT + PROFILE_GAMES_BORDER_PX * 2 +
        PROFILE_GAME_HEIGHT * (GamesROB.ALL_GAMES.length + 1) / PROFILE_GAMES_IN_LINE;

    private static final Color GAME_DESC_COLOR = new Color(33, 33, 33, 201);

    private static final int PFP_SIZE = 109;

    public static void profile(CommandContext context, Graphics2D g2d, Font starlight) throws Exception {
        String background = UserProfile.get(context.getAuthor()).getBackgroundImageUrl();

        int userprofileX = renderBackground(g2d, background, PROFILE_COMMAND_WIDTH, USER_PROFILE_HEIGHT, PROFILE_COMMAND_HEIGHT);
        drawUserProfile(context.getAuthor(), g2d, userprofileX, 0, starlight);

        GuildProfile board = GuildProfile.get(context.getGuild());

        g2d.setFont(starlight.deriveFont((float) PROFILE_GAME_LINE_HEIGHT - 1F));
        int curi = 0;
        final int singleSize = PROFILE_COMMAND_WIDTH / PROFILE_GAMES_IN_LINE - PROFILE_GAMES_BORDER_PX;
        for (Map.Entry<String, UserProfile.GameStatistics> entry : board.getLeaderboard()
                .getStatsForUser(context.getAuthor().getId()).getRawMap().entrySet()) {
            int x = PROFILE_GAMES_BORDER_PX + (curi % PROFILE_GAMES_IN_LINE) * singleSize;
            int y = USER_PROFILE_HEIGHT + PROFILE_GAMES_BORDER_PX + PROFILE_GAME_HEIGHT * (curi / PROFILE_GAMES_IN_LINE);
            boolean overall = entry.getKey().equals("overall");
            int leaderboardPosition = board.getIndex(board.getLeaderboard().getEntriesForGame(overall ? Optional.empty() :
                    Optional.of(entry.getKey())), context.getAuthor().getId());

            g2d.setColor(Color.BLACK);
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
            g2d.drawString(overall
                ? Language.transl(context, "command.profile.overall")
                : Language.transl(context, "game." + entry.getKey() + ".name"), x, y);

            g2d.setColor(GAME_DESC_COLOR);
            g2d.setFont(g2d.getFont().deriveFont(Font.PLAIN));

            y += PROFILE_GAME_LINE_DISTANCE + g2d.getFontMetrics().getHeight();
            g2d.drawString(Language.transl(context, "command.profile.line1",
                    leaderboardPosition + Utility.formatNth(Constants.getLanguage(context), leaderboardPosition)),
                    x, y);
            y += PROFILE_GAME_LINE_DISTANCE + g2d.getFontMetrics().getHeight();
            g2d.drawString(Language.transl(context, "command.profile.line2",
                    entry.getValue().getVictories(), entry.getValue().getLosses()), x, y);
            y += PROFILE_GAME_LINE_DISTANCE + g2d.getFontMetrics().getHeight();
            g2d.drawString(Language.transl(context, "command.profile.line3", new BigDecimal(entry.getValue()
                    .getWonPercent()).setScale(1, BigDecimal.ROUND_HALF_UP).toString()), x, y);

            curi ++;
        }
    }

    private static int renderBackground(Graphics2D g2d, String background, int width, int height, int blurryHeight) {
        Image image = getImage("userprofilebg_" + background, background == null
                ? ImageCommands.class.getResourceAsStream("/imggen/shapesbackground.png")
                : HttpRequest.get(background).userAgent(Constants.USER_AGENT).stream());
        Image blurryImage = getImage("userprofilebgblurry_" + background, background == null
                ? ImageCommands.class.getResourceAsStream("/imggen/shapesbackgroundblurry.png")
                : HttpRequest.get(background + "blurry").userAgent(Constants.USER_AGENT).stream());

        int relHeight = (int) (image.getHeight(null) / ((double) image.getWidth(null)
                / USER_PROFILE_WIDTH));
        int y = - relHeight / 2 - height / 2;
        int x = width / 2 - USER_PROFILE_WIDTH / 2;

        g2d.drawImage(blurryImage, 0, y + height, width,
                - y + blurryHeight, null);
        g2d.drawImage(image, 0, y, width, - y + height,
                null);

        g2d.setClip(new Ellipse2D.Float(x + 12, 8, (float) PFP_SIZE, (float) PFP_SIZE));
        g2d.drawImage(blurryImage, 0, y, width, relHeight, null);
        g2d.setClip(null);

        return x;
    }

    private static void drawUserProfile(User user, Graphics2D g2d, int x, int y, Font starlight) throws Exception {
        Image template = getImage("uptemplate", ImageCommands.class.getResourceAsStream("/imggen/userprofiletemplate.png"));
        g2d.drawImage(template, x, y, null);

        g2d.setClip(new Ellipse2D.Float(x + 12, y + 8, (float) PFP_SIZE, (float) PFP_SIZE));
        g2d.drawImage(getImage("pfp_" + user.getId(), HttpRequest.get(user.getEffectiveAvatarUrl()).userAgent(Constants.USER_AGENT).stream()),
                x + 12, y + 8, PFP_SIZE, PFP_SIZE, null);
        g2d.setClip(null);

        // name
        g2d.setColor(Color.WHITE);
        g2d.setFont(starlight.deriveFont(60F));
        g2d.drawString(user.getName(), x + 200, y + 85);
        int oldHeight = g2d.getFontMetrics().getHeight();
        int fullWidth = g2d.getFontMetrics().stringWidth(user.getName());
        // #tagg
        g2d.setColor(new Color(0x90A4AE));
        g2d.setFont(starlight.deriveFont(20F));
        g2d.drawString("#" + user.getDiscriminator(), x + 210 + fullWidth, y + 110 - g2d.getFontMetrics().getHeight());
    }

    private static Image getImage(String cacheName, InputStream is) {
        return Cache.get(cacheName, (Provider<Image>)  n -> {
            try {
                Image image = ImageIO.read(is);
                Utility.quietlyClose(is);
                return image;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}

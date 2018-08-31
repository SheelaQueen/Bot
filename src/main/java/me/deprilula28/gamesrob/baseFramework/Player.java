package me.deprilula28.gamesrob.baseFramework;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.dv8tion.jda.core.entities.User;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Data
public class Player {
    // TODO Show random patreon as bot name
    private static final String[] BOT_NAMES = {
            "deprilula28", "Fin", "alex", "Bsc", "Ephysios", "diniboy", "ipu", "Late Trender", "judg3GH", "Niekold",
            "rafa", "Voropaulo", "Jazzy Spazzy", "Lord Mark", "Buffetpls",
            // may mays
            "Juan", "Wumpus", "Clyde", "<@1>", "John Dick", "GamesROB"
    };

    private Optional<User> user;
    private Optional<Random> aiRandom;
    private String aiName;

    private Player(User playerUser, Random random) {
        user = Optional.ofNullable(playerUser);
        aiRandom = Optional.ofNullable(random);
        aiRandom.ifPresent(it -> aiName = BOT_NAMES[it.nextInt(BOT_NAMES.length)]);
    }

    public static Player user(User user) {
        return new Player(user, null);
    }

    public static Player ai() {
        return new Player(null, GameUtil.generateRandom());
    }

    @Override
    public String toString() {
        return user.map(User::getName).orElse(aiName + " <:botTag:230105988211015680>");
    }
}

package me.deprilula28.gamesrob.data;

import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrob.commands.CommandsManager;
import me.deprilula28.gamesrob.utility.Language;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.baseFramework.GamesInstance;
import me.deprilula28.gamesrob.utility.Cache;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.gamesrob.utility.Utility;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

import java.io.DataOutputStream;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private String emote;
    private String language;
    private int globalTokens;
    private long lastUpvote;
    private int upvotedDays;
    private String backgroundImageUrl;
    private List<Badge> badges;
    private int candy;
    private long gameplayTime;
    private int gamesPlayed;
    private long firstUse;
    private long lastRanWeekly;
    private boolean edited = false;

    public void setTokens(Guild guild, int amount) {
        setTokens(guild == null ? null : guild.getId(), amount);
    }

    public void setTokens(String guild, int amount) {
        if (GamesROBShardCluster.premiumBot && guild != null && GuildProfile.get(guild).isCustomEconomy()) {
            PremiumGuildMember member = PremiumGuildMember.get(userId, guild);
            member.setCustomEconomyAmount(amount);
            member.setEdited(true);
        } else {
            globalTokens = amount;
            edited = true;
        }
    }

    public int getTokens(Guild guild) {
        return getTokens(guild == null ? null : guild.getId());
    }

    public int getTokens(String guild) {
        return GamesROBShardCluster.premiumBot && guild != null ? GuildProfile.get(guild).isCustomEconomy()
                ? PremiumGuildMember.get(userId, guild).getCustomEconomyAmount()
                : globalTokens : globalTokens;
    }

    public LeaderboardHandler.UserStatistics getStatsForGuild(Guild guild) {
        return GuildProfile.get(guild).getLeaderboard().getStatsForUser(userId);
    }

    public List<PatreonPerk> getPatreonPerks() {
        Optional<Member> member = GamesROB.getGuildById(Constants.SERVER_ID).map(it -> it.getMemberById(userId));
        return member.map(it -> Arrays.stream(PatreonPerk.values()).filter(perk -> it.getRoles()
            .stream().anyMatch(role -> role.getIdLong() == perk.getRoleId())).collect(Collectors.toList()))
                .orElse(new ArrayList<>());
    }

    @AllArgsConstructor
    public static enum PatreonPerk {
        SUPPORTER(484844603267219467L), VIP(507955848610316318L), PREMIUM(507747686020022274L), INSIDER(507956432449306644L);

        private long roleId;

        public long getRoleId() {
            return roleId;
        }
    }

    @AllArgsConstructor
    public static enum Badge {
        HIGHEST_BALTOP_1ST("highestBaltop1st"), HIGHEST_BALTOP_2ND("highestBaltop2nd"), HIGHEST_BALTOP_3RD("highestBaltop3rd"),
        GLOBAL_BALTOP_TOP_10("globalBaltopTop10"), STAFF("staff"), TRANSLATOR("translator"), PATRON("patron"),
        ACHIEVER("achiever"), DEVELOPER("developer");

        private String languageCode;

        public String getBadgeImagePath() {
            return "res/badge/" + languageCode + ".png";
        }

        public String getName(String language) {
            return Language.transl(language, "badges." + languageCode);
        }
    }

    @Data
    @AllArgsConstructor
    public static class GameStatistics {
        public int victories;
        public int losses;
        public int gamesPlayed;

        public double getWonPercent() {
            if (gamesPlayed == 0) return 0.0;
            return ((double) victories / (double) gamesPlayed) * 100L;
        }
    }

    @Data
    @AllArgsConstructor
    public static class Transaction {
        private UserProfile profile;
        private int amount;
        private long time;
        private String message;
    }

    private static final List<String> TRANSACTION_MESSAGES = Arrays.asList(
            "upvote", "completeAchievement", "cheater", "give", "got", "winGamePrize", "betting", "slots", "changingEmote", "weekly"
    );

    public void addBadge(Badge badge) {
        if (!badges.contains(badge)) badges.add(badge);
        edited = true;
    }

    public int addTokens(Guild guild, int amount, String message) {
        return addTokens(guild == null ? null : guild.getId(), amount, message);
    }

    public int addTokens(String guild, int amount, String message) {
        if (CommandsManager.getBlacklist(userId).isPresent()) return 0;

        int earnt = getPatreonPerks().contains(PatreonPerk.VIP) ? (int) (amount * 1.25) :
                getPatreonPerks().contains(PatreonPerk.SUPPORTER) ? (int) (amount * 1.5) :
                amount;
        boolean hasCustomEco = guild != null && GuildProfile.get(guild).isCustomEconomy();

        if (hasCustomEco) {
            PremiumGuildMember member = PremiumGuildMember.get(userId, guild);
            member.setCustomEconomyAmount(member.getCustomEconomyAmount() + earnt);
            member.setEdited(true);
        } else {
            edited = true;
            globalTokens += earnt;
        }
        GamesROB.database.ifPresent(it ->
            it.insert("transactions", Arrays.asList("userid", "amount", "earnt", "time", "message", "premiumguild"),
                sql -> Log.wrapException("Inserting SQL transaction log", () -> {
            sql.setString(1, userId);
            sql.setInt(2, amount);
            sql.setInt(3, earnt);
            sql.setLong(4, System.currentTimeMillis());
            sql.setShort(5, (short) TRANSACTION_MESSAGES.indexOf(message.substring("transactions.".length())));
            sql.setString(6, hasCustomEco ? guild : null);
        })));

        return earnt;
    }

    public int getTransactionAmount(Optional<String> transactionMessage) {
        if (!GamesROB.database.isPresent()) return 0;
        return Cache.get("transactions_amount_" + userId + "_" + transactionMessage.orElse("all"), it -> {
            try {
                return GamesROB.database.get().getSize("transactions", "userid = '" + userId + "'" +
                        transactionMessage.map(str -> " AND message = " + TRANSACTION_MESSAGES.indexOf(str))
                                .orElse(""));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Transaction> getTransactions(int limit, int offset, Optional<String> transactionMessage) {
        return getTransactions(limit, offset, transactionMessage, Optional.empty());
    }

    public List<Transaction> getTransactions(int limit, int offset, Optional<String> transactionMessage, Optional<String> guild) {
        if (!GamesROB.database.isPresent()) return new ArrayList<>();
        return Cache.get("transactions_" + userId + "_" + limit + "_" + offset + "_" + transactionMessage.orElse("all"), it -> {
            try {
                ResultSet set = GamesROB.database.get().select("transactions", Arrays.asList("amount", "time", "message", "premiumguild"),
                        "userid = '" + userId + "'" + transactionMessage.map(str -> " AND message = " +
                                TRANSACTION_MESSAGES.indexOf(str)).orElse("") + " AND premiumguild = '" +
                                guild.orElse("null") + "'", "time", true, limit, offset);
                List<Transaction> transactions = new ArrayList<>();

                while (set.next()) transactions.add(new Transaction(
                        this, set.getInt("amount"), set.getLong("time"),
                        "transactions." + TRANSACTION_MESSAGES.get(set.getShort("message"))
                ));
                set.close();

                return transactions;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public int getTransactionAmount() {
        if (!GamesROB.database.isPresent()) return 0;
        return Cache.get("transactionscount_" + userId, it -> {
            try {
                return GamesROB.database.get().getSize("transactions", "userid = '" + userId + "'");
            } catch (Exception e) {
                return 0;
            }
        });
    }

    public boolean transaction(Guild guild, int amount, String message) {
        if (getTokens(guild) >= amount) {
            addTokens(guild, -amount, message);
            return true;
        } else return false;
    }

    public void registerGameResult(Guild guild, User user, boolean victory, boolean loss, GamesInstance game, long playTime) {
        LeaderboardHandler.UserStatistics stats = getStatsForGuild(guild);
        registerGameResult(stats.getStats("overall"), victory, loss);
        registerGameResult(stats.getStats(game.getLanguageCode()), victory, loss);
        gameplayTime += playTime;
        edited = true;
    }

    private void registerGameResult(GameStatistics stats, boolean victory, boolean loss) {
        if (victory) stats.victories ++;
        else if (loss) stats.losses ++;
        stats.gamesPlayed ++;
    }

    public static UserSaveManager manager = new UserSaveManager();

    public static UserProfile get(User user) {
        return get(user.getId());
    }

    public static UserProfile get(String id) {
        return manager.get(id, UserProfile.class);
    }

    public static class UserSaveManager extends DataManager<String, UserProfile> {
        @Override
        public Optional<UserProfile> getFromSQL(SQLDatabaseManager db, String from) throws Exception {
            ResultSet select = db.select("userData", Arrays.asList("emote", "language", "tokens", "lastupvote",
                    "upvoteddays", "profilebackgroundimgurl", "badges", "candy", "gameplaytime", "gamesplayed", "firstuse", "lastranweekly"),
                    "userid = '" + from + "'");
            if (select.next()) return fromResultSet(from, select);
            select.close();

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, UserProfile value) {
            return db.save("userData", Arrays.asList(
                    "emote", "userId", "tokens", "lastupvote", "upvoteddays", "language", "profilebackgroundimgurl",
                    "badges", "candy", "gameplaytime", "gamesplayed", "firstuse", "lastranweekly"
            ), "userid = '" + value.getUserId() + "'",
                set -> !value.isEdited(), true,
                (set, it) -> Log.wrapException("Saving data on SQL", () -> write(it, value)));
        }

        private Optional<UserProfile> fromResultSet(String from, ResultSet select) {
            try {
                return Optional.of(new UserProfile(from, select.getString("emote"),
                        select.getString("language"), select.getInt("tokens"),
                        select.getLong("lastupvote"), select.getInt("upvoteddays"),
                        select.getString("profilebackgroundimgurl"),
                        Utility.decodeBinary(select.getInt("badges"), Badge.class),
                        select.getInt("candy"), select.getLong("gameplaytime"),
                        select.getInt("gamesplayed"), select.getLong("firstuse"),
                        select.getLong("lastranweekly"),  false));
            } catch (Exception e) {
                Log.exception("Saving UserProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void write(PreparedStatement statement, UserProfile profile) throws Exception {
            statement.setString(1, profile.getEmote());
            statement.setString(2, profile.getUserId());
            statement.setInt(3, profile.getGlobalTokens());
            statement.setLong(4, profile.getLastUpvote());
            statement.setInt(5, profile.getUpvotedDays());
            statement.setString(6, profile.getLanguage());
            statement.setString(7, profile.getBackgroundImageUrl());
            statement.setInt(8, Utility.encodeBinary(profile.getBadges(), Badge.class));
            statement.setInt(9, profile.getCandy());
            statement.setLong(10, profile.getGameplayTime());
            statement.setInt(11, profile.getGamesPlayed());
            statement.setLong(12, profile.getFirstUse());
            statement.setLong(13, profile.getLastRanWeekly());
        }

        private void saveStatistics(DataOutputStream stream, GameStatistics stats) throws Exception {
            stream.writeInt(stats.getVictories());
            stream.writeInt(stats.getLosses());
            stream.writeInt(stats.getGamesPlayed());
        }

        @Override
        public UserProfile createNew(String from) {
            return new UserProfile(from, null, null, 0, 0, 0,
                    null, new ArrayList<>(), 0,0L, 0,
                    System.currentTimeMillis(), 0L, false);
        }

        @Override
        public File getDataFile(String from) {
            return new File(Constants.USER_PROFILES_FOLDER, from + ".json");
        }
    }
}

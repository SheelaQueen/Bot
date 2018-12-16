package me.deprilula28.gamesrob.data;

import javafx.util.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.gamesrobshardcluster.utilities.Constants;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Optional;

@Data
@AllArgsConstructor
public class PremiumGuildMember {
    private String userId;
    private String guildId;

    private int tournmentWins;
    private int customEconomyAmount;

    private boolean edited;

    public static PremiumGuildMemberManager manager = new PremiumGuildMemberManager();

    public static PremiumGuildMember get(User user, Guild guild) {
        return get(user.getId(), guild.getId());
    }

    public static PremiumGuildMember get(String userId, String guildId) {
        return manager.get(new Pair<>(userId, guildId), PremiumGuildMember.class);
    }

    public static class PremiumGuildMemberManager extends DataManager<Pair<String, String>, PremiumGuildMember> {
        @Override
        public Optional<PremiumGuildMember> getFromSQL(SQLDatabaseManager db, Pair<String, String> from) throws Exception {
            ResultSet select = db.select("premiumguildmember", Arrays.asList("tournmentwins", "customecoamount"),
                    "userid = '" + from.getKey() + "' AND guildid = '" + from.getValue() + "'");
            if (select.next()) return fromResultSet(from, select);
            select.close();

            return Optional.empty();
        }

        @Override
        public Utility.Promise<Void> saveToSQL(SQLDatabaseManager db, PremiumGuildMember value) {
            return db.save("premiumguildmember", Arrays.asList("tournmentwins", "customecoamount"),
                    "userid = '" + value.getUserId() + "' AND guildid = '" + value.getGuildId() + "'",
                    set -> !value.isEdited(), true,
                    (set, it) -> Log.wrapException("Saving data on SQL", () -> write(it, value)));
        }

        private Optional<PremiumGuildMember> fromResultSet(Pair<String, String> from, ResultSet select) {
            try {
                return Optional.of(new PremiumGuildMember(from.getKey(), from.getValue(),
                        select.getInt("tournmentwins"),
                        select.getInt("customecoamount"), false));
            } catch (Exception e) {
                Log.exception("Saving UserProfile in SQL", e);
                return Optional.empty();
            }
        }

        private void write(PreparedStatement statement, PremiumGuildMember member) throws Exception {
            statement.setInt(1, member.getTournmentWins());
            statement.setInt(2, member.getCustomEconomyAmount());
        }

        @Override
        public PremiumGuildMember createNew(Pair<String, String> from) {
            return new PremiumGuildMember(from.getKey(), from.getValue(), 0, 0, false);
        }

        @Override
        public File getDataFile(Pair<String, String> from) {
            return new File(Constants.USER_PROFILES_FOLDER, from.toString() + ".json");
        }
    }
}

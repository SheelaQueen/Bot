package me.deprilula28.gamesrob.utility;

import javafx.util.Pair;
import me.deprilula28.gamesrob.GamesROB;
import me.deprilula28.gamesrobshardcluster.GamesROBShardCluster;
import me.deprilula28.gamesrobshardcluster.utilities.Log;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;

import java.beans.ConstructorProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Permissions {
    private static List<Function<CommandContext, Pair<TargetType, List<String>>>> TARGET_GETTERS =
            Arrays.asList(context -> new Pair<>(TargetType.USER, Collections.singletonList(context.getAuthor().getId())),
            context -> new Pair<>(TargetType.ROLE, context.getAuthorMember().getRoles().stream().map(ISnowflake::getId).collect(Collectors.toList())),
            context -> new Pair<>(TargetType.ALL, Collections.singletonList(null)));
    private static List<Function<CommandContext, Pair<LocationType, String>>> LOCATION_GETTERS =
            Arrays.asList(context -> new Pair<>(LocationType.CHANNEL, context.getChannel().getId()),
            context -> new Pair<>(LocationType.CATEGORY, Optional.ofNullable(context.getChannel().getParent())
                    .map(ISnowflake::getId).orElse(null)),
            context -> new Pair<>(LocationType.GUILD, context.getGuild().getId()));

    public static boolean check(CommandContext context) {
        if (context.getAuthorMember().equals(context.getGuild().getOwner()) || GamesROB.owners.contains(context.getAuthor().getIdLong())) {
            return true;
        }
        return Permissions.check(context, PermissionType.COMMAND, Optional.of(GamesROBShardCluster.framework
                .getCommands().indexOf(context.getCurrentCommand())), context.getCurrentCommand()
                .attr("adminlockdefault") == null);
    }

    public static boolean check(CommandContext context, PermissionType permission, Optional<Integer> spec, boolean fallback) {
        if (context.getAuthorMember().equals(context.getGuild().getOwner()) || GamesROB.owners.contains(context.getAuthor().getIdLong())) {
            return true;
        }
        List<Pair<TargetType, List<String>>> targets = TARGET_GETTERS.stream().map(it -> it.apply(context)).collect(Collectors.toList());
        for (Pair<LocationType, String> location : LOCATION_GETTERS.stream().map(it -> it.apply(context)).collect(Collectors.toList())) {
            for (Pair<TargetType, List<String>> target : targets) {
                for (String targetId : target.getValue()) {
                    if (Permissions.check(location.getKey(), location.getValue(), target.getKey(), targetId, permission,
                            spec, context.getAuthorMember().hasPermission(Permission.ADMINISTRATOR)
                            || GamesROB.owners.contains(context.getAuthor().getIdLong()) || fallback)) continue;
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean check(LocationType locType, String locId, TargetType targetType, String targetId,
                                PermissionType type, Optional<Integer> spec, boolean fallback) {
        return Cache.get("checking:" + locType.toString() + " " + locId + " " + targetType.toString() + " " +
                targetId + " " + type.toString() + " " + spec.map(Object::toString).orElse("") + " ", n -> {
            try {
                ResultSet set = GamesROB.database.orElseThrow(() -> new RuntimeException("Where my DB at")).select("guildpermissions", Collections.singletonList("allow"), "target = " + targetId + " AND targettype = " + Utility.indexOf(TargetType.values(), targetType) + " AND locationid = " + locId + " AND locationtype = " + Utility.indexOf(LocationType.values(), locType) + " AND (permtype = " + Utility.indexOf(PermissionType.values(), type) + " OR permtype IS NULL)" + spec.map(it -> " AND (permspec = " + it + " OR permspec IS NULL)").orElse(""));
                boolean result = set.next() ? set.getBoolean("allow") : fallback;
                set.close();
                return result;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static List<PermissionLog> getLogs(List<Pair<TargetType, String>> targets, List<Pair<LocationType, String>> locations) {
        return Cache.get("checking:" + targets.hashCode() + " " + locations.hashCode(), n -> {
            try {
                ResultSet set = GamesROB.database.orElseThrow(() -> new RuntimeException("Where my DB at")).select("guildpermissions", Arrays.asList("locationid", "locationtype", "target", "targettype", "permtype", "permspec", "allow"), "((" + targets.stream().map(it -> "target = " + (String)it.getValue() + " AND targettype = " + Utility.indexOf(TargetType.values(), it.getKey())).collect(Collectors.joining(") OR (")) + ")) AND ((" + locations.stream().map(it -> "locationid = " + (String)it.getValue() + " AND locationtype = " + Utility.indexOf(LocationType.values(), it.getKey())).collect(Collectors.joining(") OR (")) + "))");
                ArrayList<PermissionLog> logs = new ArrayList<PermissionLog>();
                while (set.next()) {
                    logs.add(Permissions.getLogFromSet(set));
                }
                set.close();
                return logs;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static PermissionLog getLogFromSet(ResultSet set) throws SQLException {
        return new PermissionLog(set.getLong("locationid"), LocationType.values()[set.getShort("locationtype")], set.getLong("target"), TargetType.values()[set.getShort("targettype")], set.getInt("permspec"), PermissionType.values()[set.getShort("permtype")], set.getBoolean("allow") ? PermCommandMode.ALLOW : PermCommandMode.DENY);
    }

    public static class PermissionLog {
        private long locId;
        private LocationType locType;
        private long targetId;
        private TargetType targetType;
        private int permissionSpec;
        private PermissionType permissionType;
        private PermCommandMode commandMode;

        public String toString(String language) {
            return Language.transl(language, "command.perm.permReg", targetType.getTargetName.apply(targetId),
                    commandMode.toString().toLowerCase(), commandMode.equals(PermCommandMode.LOCK)
                            ? Language.transl(language, "command.perm.allPerms")
                            : permissionType.toString().toLowerCase().replaceAll("_", ".")
                                + (!this.permissionType.getSpecType().equals(PermissionType.SpecType.NONE)
                                    ? " (Spec " + permissionSpec + ")" : ""),
                    locType.getLocationName.apply(this.locId));
        }

        private String getWhere(int targetTypeIndex, int locationTypeIndex, int permTypeIndex) {
            return "target = " + this.targetId + " AND targettype = " + targetTypeIndex + " AND locationid = " + this.locId + " AND locationtype = " + locationTypeIndex + " AND permspec = " + this.permissionSpec + (permTypeIndex > 0 ? new StringBuilder().append(" AND permtype = ").append(permTypeIndex).toString() : " AND permtype IS NULL");
        }

        public boolean hasEntry() {
            int targetTypeIndex = Utility.indexOf(TargetType.values(), this.targetType);
            int locationTypeIndex = Utility.indexOf(LocationType.values(), this.locType);
            int permTypeIndex = this.permissionType == null ? -1 : Utility.indexOf(PermissionType.values(), this.permissionType);
            return GamesROB.database.map(it -> {
                try {
                    return it.select("guildpermissions", this.getWhere(targetTypeIndex, locationTypeIndex, permTypeIndex)).next();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).orElse(false);
        }

        public void save() {
            GamesROB.database.ifPresent(db -> {
                int permTypeIndex;
                int targetTypeIndex = Utility.indexOf(TargetType.values(), this.targetType);
                int locationTypeIndex = Utility.indexOf(LocationType.values(), this.locType);
                int n = permTypeIndex = this.permissionType == null ? -1 : Utility.indexOf(PermissionType.values(), this.permissionType);
                if (this.commandMode.equals(PermCommandMode.NEUTRAL)) db.delete("guildpermissions",
                        getWhere(targetTypeIndex, locationTypeIndex, permTypeIndex));
                else {
                    db.save("guildpermissions", Arrays.asList("locationid", "locationtype", "target", "targettype",
                            "permspec", "permtype", "allow"), getWhere(targetTypeIndex, locationTypeIndex, permTypeIndex),
                            set -> false, false, (old, statement) -> Log.wrapException("Saving SQL for permissions", () -> {
                        statement.setLong(1, locId);
                        statement.setShort(2, (short) locationTypeIndex);
                        statement.setLong(3, targetId);
                        statement.setShort(4, (short) targetTypeIndex);
                        statement.setLong(5, permissionSpec);
                        statement.setBoolean(7, commandMode.equals(PermCommandMode.ALLOW));
                        if (commandMode.equals(PermCommandMode.LOCK)) statement.setNull(6, 5);
                        else statement.setShort(6, (short) permTypeIndex);
                    }, new Object[0]));
                }
            });
            Cache.clear("checking:" + this.locType.toString() + " " + this.locId + " " + this.targetType.toString() + " " + this.targetId + " " + this.permissionType.toString() + " " + this.permissionSpec + " ");
        }

        public long getLocId() {
            return this.locId;
        }

        public LocationType getLocType() {
            return this.locType;
        }

        public long getTargetId() {
            return this.targetId;
        }

        public TargetType getTargetType() {
            return this.targetType;
        }

        public int getPermissionSpec() {
            return this.permissionSpec;
        }

        public PermissionType getPermissionType() {
            return this.permissionType;
        }

        public PermCommandMode getCommandMode() {
            return this.commandMode;
        }

        public void setLocId(long locId) {
            this.locId = locId;
        }

        public void setLocType(LocationType locType) {
            this.locType = locType;
        }

        public void setTargetId(long targetId) {
            this.targetId = targetId;
        }

        public void setTargetType(TargetType targetType) {
            this.targetType = targetType;
        }

        public void setPermissionSpec(int permissionSpec) {
            this.permissionSpec = permissionSpec;
        }

        public void setPermissionType(PermissionType permissionType) {
            this.permissionType = permissionType;
        }

        public void setCommandMode(PermCommandMode commandMode) {
            this.commandMode = commandMode;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof PermissionLog)) {
                return false;
            }
            PermissionLog other = (PermissionLog)o;
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.getLocId() != other.getLocId()) {
                return false;
            }
            LocationType this$locType = this.getLocType();
            LocationType other$locType = other.getLocType();
            if (this$locType == null ? other$locType != null : !this$locType.equals(other$locType)) {
                return false;
            }
            if (this.getTargetId() != other.getTargetId()) {
                return false;
            }
            TargetType this$targetType = this.getTargetType();
            TargetType other$targetType = other.getTargetType();
            if (this$targetType == null ? other$targetType != null : !this$targetType.equals(other$targetType)) {
                return false;
            }
            if (this.getPermissionSpec() != other.getPermissionSpec()) {
                return false;
            }
            PermissionType this$permissionType = this.getPermissionType();
            PermissionType other$permissionType = other.getPermissionType();
            if (this$permissionType == null ? other$permissionType != null : !this$permissionType.equals(other$permissionType)) {
                return false;
            }
            PermCommandMode this$commandMode = this.getCommandMode();
            PermCommandMode other$commandMode = other.getCommandMode();
            if (this$commandMode == null ? other$commandMode != null : !this$commandMode.equals(other$commandMode)) {
                return false;
            }
            return true;
        }

        protected boolean canEqual(Object other) {
            return other instanceof PermissionLog;
        }

        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            long $locId = this.getLocId();
            result = result * 59 + (int)($locId >>> 32 ^ $locId);
            LocationType $locType = this.getLocType();
            result = result * 59 + ($locType == null ? 43 : $locType.hashCode());
            long $targetId = this.getTargetId();
            result = result * 59 + (int)($targetId >>> 32 ^ $targetId);
            TargetType $targetType = this.getTargetType();
            result = result * 59 + ($targetType == null ? 43 : $targetType.hashCode());
            result = result * 59 + this.getPermissionSpec();
            PermissionType $permissionType = this.getPermissionType();
            result = result * 59 + ($permissionType == null ? 43 : $permissionType.hashCode());
            PermCommandMode $commandMode = this.getCommandMode();
            result = result * 59 + ($commandMode == null ? 43 : $commandMode.hashCode());
            return result;
        }

        public String toString() {
            return "Permissions.PermissionLog(locId=" + this.getLocId() + ", locType=" + (this.getLocType()) + ", targetId=" + this.getTargetId() + ", targetType=" + (this.getTargetType()) + ", permissionSpec=" + this.getPermissionSpec() + ", permissionType=" + (this.getPermissionType()) + ", commandMode=" + (this.getCommandMode()) + ")";
        }

        @ConstructorProperties(value={"locId", "locType", "targetId", "targetType", "permissionSpec", "permissionType", "commandMode"})
        public PermissionLog(long locId, LocationType locType, long targetId, TargetType targetType, int permissionSpec, PermissionType permissionType, PermCommandMode commandMode) {
            this.locId = locId;
            this.locType = locType;
            this.targetId = targetId;
            this.targetType = targetType;
            this.permissionSpec = permissionSpec;
            this.permissionType = permissionType;
            this.commandMode = commandMode;
        }
    }

    public static enum PermissionType {
        GAME_START(SpecType.GAME),
        GAME_JOIN(SpecType.GAME),
        GAME_LEAVE(SpecType.GAME),
        GAME_STOP(SpecType.GAME),
        OWN_GAME_STOP(SpecType.GAME),
        COMMAND(SpecType.COMMAND),
        VIEW_OTHER_USER_PROFILES(SpecType.NONE),
        VIEW_GLOBAL_BALTOP(SpecType.NONE);

        private SpecType specType;

        @ConstructorProperties(value={"specType"})
        private PermissionType(SpecType specType) {
            this.specType = specType;
        }

        public SpecType getSpecType() {
            return this.specType;
        }

        public static enum SpecType {
            COMMAND(arg -> GamesROBShardCluster.framework.getCommands().stream().filter(it -> it.getAliases()
                    .contains(arg)).findFirst().map(it -> GamesROBShardCluster.framework.getCommands().indexOf(it))
                    .orElseThrow(() -> new CommandArgsException("Command not found.")), spec ->
                    GamesROBShardCluster.framework.getCommands().get((int)spec).getName()),
            GAME(arg -> Arrays.stream(GamesROB.ALL_GAMES).filter(it -> Arrays.asList(it.getAliases().split(" ")).contains(arg)).map(it ->
                    Utility.indexOf(GamesROB.ALL_GAMES, it)).findFirst().orElseThrow(() -> new CommandArgsException("Game not found.")), spec -> GamesROB.ALL_GAMES[spec].getLanguageCode()),
            NONE(null, null);

            private Function<String, Integer> specFromArgument;
            private Function<Integer, String> argumentFromSpec;

            @ConstructorProperties(value={"specFromArgument", "argumentFromSpec"})
            private SpecType(Function<String, Integer> specFromArgument, Function<Integer, String> argumentFromSpec) {
                this.specFromArgument = specFromArgument;
                this.argumentFromSpec = argumentFromSpec;
            }

            public Function<String, Integer> getSpecFromArgument() {
                return this.specFromArgument;
            }

            public Function<Integer, String> getArgumentFromSpec() {
                return this.argumentFromSpec;
            }
        }

    }

    public static enum TargetType {
        USER(id -> GamesROB.getUserById(id).map(IMentionable::getAsMention).orElse("*Invalid User*")),
        ROLE(id -> GamesROB.getRoleById(id).map(Role::getName).orElse("*Invalid Role*")),
        ALL(id -> "All");

        private Function<Long, String> getTargetName;

        @ConstructorProperties(value={"getTargetName"})
        private TargetType(Function<Long, String> getTargetName) {
            this.getTargetName = getTargetName;
        }
    }

    public static enum LocationType {
        GUILD(id -> GamesROB.getGuildById(id).map(Guild::getName).orElse("*Invalid Guild*")),
        CATEGORY(id -> GamesROB.getCategoryById(id).map(Channel::getName).orElse("*Invalid Category*")),
        CHANNEL(id -> GamesROB.getTextChannelById(id).map(MessageChannel::getName).orElse("*Invalid Channel*"));

        private Function<Long, String> getLocationName;

        @ConstructorProperties(value={"getLocationName"})
        private LocationType(Function<Long, String> getLocationName) {
            this.getLocationName = getLocationName;
        }
    }

    public static enum PermCommandMode {
        ALLOW,
        DENY,
        LOCK,
        NEUTRAL;


        private PermCommandMode() {
        }
    }
}

package me.deprilula28.gamesrob.commands;

import javafx.util.Pair;
import me.deprilula28.gamesrob.utility.Language;
import me.deprilula28.gamesrob.utility.Permissions;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.Command;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;
import me.deprilula28.jdacmdframework.exceptions.InvalidCommandSyntaxException;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class PermissionCommands {
    public static String permission(CommandContext context) {
        throw new InvalidCommandSyntaxException();
    }

    public static String permissionCheck(CommandContext context) {
        User target = context.opt(context::nextUser).orElseGet(context::getAuthor);

        // Targets
        ArrayList<Pair<Permissions.TargetType, String>> targets = new ArrayList<>();
        targets.add(new Pair<>(Permissions.TargetType.ALL, null));
        targets.add(new Pair<>(Permissions.TargetType.USER, target.getId()));
        targets.addAll(context.getGuild().getMember(target).getRoles().stream().map(it ->
                new Pair<>(Permissions.TargetType.ROLE, it.getId())).collect(Collectors.toList()));

        // Affecting
        List<Permissions.PermissionLog> affecting = Permissions.getLogs(targets,
                Arrays.asList(new Pair<>(Permissions.LocationType.GUILD, context.getGuild().getId()),
                        new Pair<>(Permissions.LocationType.CATEGORY, context.getChannel().getParent().getId()),
                        new Pair<>(Permissions.LocationType.CHANNEL, context.getChannel().getId())));

        HashMap<Permissions.PermissionType, List<Permissions.PermissionLog>> map = new HashMap<>();
        affecting.forEach(it -> {
            if (!map.containsKey(it.getPermissionType())) map.put(it.getPermissionType(), new ArrayList<>());
            map.get(it.getPermissionType()).add(it);
        });

        // Message
        return (target.equals(context.getAuthor())
                ? Language.transl(context, "command.perm.check.ownHeader")
                : Language.transl(context, "command.perm.check.otherHeader", target.getName()))
                + Arrays.stream(Permissions.PermissionType.values()).sorted(Comparator.comparingInt(it ->
                Utility.indexOf(Permissions.PermissionType.values(), it))).map(it -> {
            if (!map.containsKey(it)) return "<:offline:313956277237710868> `" + it.toString().toLowerCase().replaceAll("_", ".") + "`";

            List<Permissions.PermissionLog> logs = map.get(it);
            if (it.getSpecType().equals(Permissions.PermissionType.SpecType.NONE))
                return (logs.get(0).getCommandMode().equals(Permissions.PermCommandMode.ALLOW) ? "<:online:313956277808005120>"
                        : "<:dnd:313956276893646850>") + " `" + it.toString().toLowerCase().replaceAll("_", ".") + "`";
            return "`" + it.toString().toLowerCase().replaceAll("_", ".") + "` Specs:\n" +
                    logs.stream().map(log -> (log.getCommandMode().equals(Permissions.PermCommandMode.ALLOW)
                            ? "<:online:313956277808005120>" : "<:dnd:313956276893646850>") + " "
                            + it.getSpecType().getArgumentFromSpec().apply(log.getPermissionSpec())).collect(Collectors.joining("\n")) + "\n";
        }).collect(Collectors.joining("\n"));
    }

    public static Command.Executor permissionEdit(Permissions.PermCommandMode commandMode) {
        return context -> {
            String curArg = context.next();
            Permissions.PermissionLog log = new Permissions.PermissionLog(context.getGuild().getIdLong(),
                    Permissions.LocationType.GUILD, context.getGuild().getIdLong(), Permissions.TargetType.ALL,
                    0, null, commandMode);

            if (curArg.startsWith("<#") && curArg.endsWith(">")) {
                log.setLocType(Permissions.LocationType.CHANNEL);
                log.setLocId(Long.parseLong(curArg.substring(2, curArg.length() - 1)));
                curArg = context.opt(context::next).orElse(null);
            } else {
                List<Category> categoriesByName = context.getGuild().getCategoriesByName(curArg, true);
                if (!categoriesByName.isEmpty()) {
                    log.setLocType(Permissions.LocationType.CATEGORY);
                    log.setLocId(categoriesByName.get(0).getIdLong());
                    curArg = context.opt(context::next).orElse(null);
                }
            }

            if (curArg != null && curArg.startsWith("<@") && curArg.endsWith(">")) {
                log.setTargetType(Permissions.TargetType.USER);
                log.setTargetId(Long.parseLong(curArg.substring(curArg.startsWith("<@!") ? 3 : 2, curArg.length() - 1)));
                curArg = context.opt(context::next).orElse(null);
            } else if (curArg != null && curArg.startsWith("<@&") && curArg.endsWith(">")) {
                log.setTargetType(Permissions.TargetType.ROLE);
                log.setTargetId(Long.parseLong(curArg.substring(3, curArg.length() - 1)));
                curArg = context.opt(context::next).orElse(null);
            }

            if (!commandMode.equals(Permissions.PermCommandMode.LOCK)) {
                if (curArg == null) {
                    throw new InvalidCommandSyntaxException();
                }
                try {
                    log.setPermissionType(Permissions.PermissionType.valueOf(curArg.toUpperCase().replaceAll("\\.", "_")));
                }
                catch (IllegalArgumentException e) {
                    Object[] arrobject = new Object[1];
                    arrobject[0] = Arrays.stream(Permissions.PermissionType.values()).map(it -> "`" + it.toString().toLowerCase()
                            .replaceAll("_", ".") + "`").collect(Collectors.joining(", "));
                    return Language.transl(context, "command.perm.invalidPermList", arrobject);
                }
                if (!log.getPermissionType().getSpecType().equals(Permissions.PermissionType.SpecType.NONE)) {
                    curArg = context.opt(context::next).orElseThrow(() -> new CommandArgsException(Language.transl(context,
                            "command.perm.specifySpec", log.getPermissionType().getSpecType().toString().toLowerCase())));
                    log.setPermissionSpec(log.getPermissionType().getSpecType().getSpecFromArgument().apply(curArg));
                }
            }
            if (log.hasEntry()) {
                log.setCommandMode(Permissions.PermCommandMode.NEUTRAL);
            }
            log.save();
            if (commandMode.equals(Permissions.PermCommandMode.LOCK) && log.getCommandMode().equals(Permissions.PermCommandMode.NEUTRAL)) {
                return Language.transl(context, "command.perm.unlock");
            }
            return Language.transl(context, "command.perm.set", log.toString(Utility.getLanguage(context)));
        };
    }
}

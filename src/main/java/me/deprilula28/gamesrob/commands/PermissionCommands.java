package me.deprilula28.gamesrob.commands;

import me.deprilula28.gamesrob.Language;
import me.deprilula28.gamesrob.data.GuildProfile;
import me.deprilula28.gamesrob.data.PermissionType;
import me.deprilula28.jdacmdframework.CommandContext;
import net.dv8tion.jda.core.entities.Role;

import java.util.List;
import java.util.Optional;

public class PermissionCommands {
    public static String changePerm(CommandContext context) {
        PermissionType perm;
        try {
            perm = PermissionType.valueOf(context.next().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Language.transl(context, "command.perm.invalidPerm");
        }
        Optional<String> roleOpt = context.opt(context::next);
        GuildProfile profile = GuildProfile.get(context.getGuild());

        if (roleOpt.isPresent()) {
            List<Role> roles = context.getGuild().getRolesByName(roleOpt.get(), true);
            if (roles.isEmpty()) return Language.transl(context, "command.perm.noRole");
            Role role = roles.get(0);

            perm.getSetter().accept(profile, role.getId());
            profile.setEdited(true);
            return Language.transl(context, "command.perm.set", perm.toString().toLowerCase(), role.getName());
        } else {
            perm.getSetter().accept(profile, null);
            profile.setEdited(true);
            return Language.transl(context, "command.perm.reset", perm.toString().toLowerCase());
        }
    }
}

package me.deprilula28.gamesrob.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.BiConsumer;

@AllArgsConstructor
public enum PermissionType {
    STOP(GuildProfile::setPermStopGame), START(GuildProfile::setPermStartGame);

    @Getter private BiConsumer<GuildProfile, String> setter;
}

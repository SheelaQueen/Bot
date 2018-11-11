package me.deprilula28.gamesrobshardcluster;

import me.deprilula28.jdacmdframework.CommandFramework;

public abstract class CommandProcessor {
    public abstract void registerCommands(String[] args, CommandFramework f);
    public abstract void close();
}

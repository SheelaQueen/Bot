package me.deprilula28.gamesrob.achievements;

public class Achievements {
    // Games Played
    public static final Achievement realPlayer = new Achievement("realPlayer", 30, AchievementType.PLAY_GAMES, 10);
    public static final Achievement intensePlayer = new Achievement("intensePlayer", 150, AchievementType.PLAY_GAMES, 25);
    public static final Achievement proPlayer = new Achievement("proPlayer", 400, AchievementType.PLAY_GAMES, 50);
    public static final Achievement tooManyGames = new Achievement("tooManyGames", 1250, AchievementType.PLAY_GAMES, 100);

    // Games Won
    public static final Achievement crowned = new Achievement("crowned", 50, AchievementType.WIN_GAMES, 5);
    public static final Achievement master = new Achievement("master", 125, AchievementType.WIN_GAMES, 12);
    public static final Achievement tooGood = new Achievement("tooGood", 500, AchievementType.WIN_GAMES, 30);
    public static final Achievement tryhard = new Achievement("tryhard", 2000, AchievementType.WIN_GAMES, 100);

    // Tokens Gambled
    public static final Achievement gambler = new Achievement("gambler", 20, AchievementType.GAMBLE_TOKENS, 500);
    public static final Achievement addicted = new Achievement("addicted", 50, AchievementType.GAMBLE_TOKENS, 1500);
    public static final Achievement highRoller = new Achievement("highRoller", 100, AchievementType.GAMBLE_TOKENS, 5000);

    // Tokens Won Gambling
    public static final Achievement onARoll = new Achievement("onARoll", 50, AchievementType.WIN_TOKENS_GAMBLING, 250);
    public static final Achievement lucky = new Achievement("lucky", 100, AchievementType.WIN_TOKENS_GAMBLING, 700);
    public static final Achievement rigged = new Achievement("rigged", 250, AchievementType.WIN_TOKENS_GAMBLING, 3000);
}

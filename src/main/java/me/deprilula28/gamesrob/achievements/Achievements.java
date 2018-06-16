package me.deprilula28.gamesrob.achievements;

public class Achievements {
    // Games Played
    public static Achievement realPlayer = new Achievement("realPlayer", 30, Achievement.AchievementType.PLAY_GAMES, 10);
    public static Achievement intensePlayer = new Achievement("intensePlayer", 150, Achievement.AchievementType.PLAY_GAMES, 25);
    public static Achievement proPlayer = new Achievement("proPlayer", 400, Achievement.AchievementType.PLAY_GAMES, 50);
    public static Achievement tooManyGames = new Achievement("tooManyGames", 1250, Achievement.AchievementType.PLAY_GAMES, 100);

    // Games Won
    public static Achievement crowned = new Achievement("crowned", 50, Achievement.AchievementType.WIN_GAMES, 5);
    public static Achievement master = new Achievement("master", 125, Achievement.AchievementType.WIN_GAMES, 12);
    public static Achievement cheater = new Achievement("cheater", 500, Achievement.AchievementType.WIN_GAMES, 30);
    public static Achievement tooGood = new Achievement("tooGood", 2000, Achievement.AchievementType.WIN_GAMES, 100);

    // Tokens Gambled
    public static Achievement gambler = new Achievement("gambler", 20, Achievement.AchievementType.GAMBLE_TOKENS, 500);
    public static Achievement addicted = new Achievement("addicted", 50, Achievement.AchievementType.GAMBLE_TOKENS, 1500);
    public static Achievement highRoller = new Achievement("highRoller", 100, Achievement.AchievementType.GAMBLE_TOKENS, 5000);

    // Tokens Won Gambling
    public static Achievement onARoll = new Achievement("onARoll", 50, Achievement.AchievementType.WIN_TOKENS_GAMBLING, 250);
    public static Achievement lucky = new Achievement("lucky", 100, Achievement.AchievementType.WIN_TOKENS_GAMBLING, 700);

    // Tokens Lost Gambling
    
}

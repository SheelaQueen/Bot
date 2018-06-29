package me.deprilula28.gamesrob.achievements;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum Achievements {
    // Games Played
    realPlayer(new Achievement("realPlayer", 30, AchievementType.PLAY_GAMES, 10)),
    intensePlayer(new Achievement("intensePlayer", 150, AchievementType.PLAY_GAMES, 25)),
    proPlayer(new Achievement("proPlayer", 400, AchievementType.PLAY_GAMES, 50)),
    tooManyGames(new Achievement("tooManyGames", 1250, AchievementType.PLAY_GAMES, 100)),

    // Games Won
    crowned(new Achievement("crowned", 50, AchievementType.WIN_GAMES, 5)),
    master(new Achievement("master", 125, AchievementType.WIN_GAMES, 12)),
    tooGood(new Achievement("tooGood", 500, AchievementType.WIN_GAMES, 30)),
    tryhard(new Achievement("tryhard", 2000, AchievementType.WIN_GAMES, 100)),

    // Tokens Gambled
    gambler(new Achievement("gambler", 20, AchievementType.GAMBLE_TOKENS, 500)),
    addicted(new Achievement("addicted", 50, AchievementType.GAMBLE_TOKENS, 1500)),
    highRoller(new Achievement("highRoller", 100, AchievementType.GAMBLE_TOKENS, 5000)),

    // Tokens Won Gambling
    onARoll(new Achievement("onARoll", 50, AchievementType.WIN_TOKENS_GAMBLING, 250)),
    lucky(new Achievement("lucky", 100, AchievementType.WIN_TOKENS_GAMBLING, 700)),
    rigged(new Achievement("rigged", 250, AchievementType.WIN_TOKENS_GAMBLING, 3000)),

    // Tokens Lost Gambling
    unfair(new Achievement("unfair", 10, AchievementType.LOSE_TOKENS_GAMBLING, 200)),
    overconfident(new Achievement("overconfident", 40, AchievementType.LOSE_TOKENS_GAMBLING, 1000)),
    stubborn(new Achievement("stubborn", 100, AchievementType.LOSE_TOKENS_GAMBLING, 4000)),

    // Reach place in lb
    wwcd(new Achievement("winnerWinnerChickenDinner", 300, AchievementType.REACH_PLACE_LEADERBOARD, 1)),

    // Reach tokens
    mediumClass(new Achievement("mediumClass", 200, AchievementType.REACH_TOKENS, 3000)),
    upperClass(new Achievement("upperClass", 500, AchievementType.REACH_TOKENS, 6000)),
    richBoi(new Achievement("richBoi", 800, AchievementType.REACH_TOKENS, 10000)),
    billGates(new Achievement("billGates", 2000, AchievementType.REACH_TOKENS, 20000));

    @Getter private Achievement achievement;
}

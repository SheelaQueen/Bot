;CREATE TABLE IF NOT EXISTS guildData (
    permstartgame TEXT, 
    permstopgame TEXT, 
    prefix TEXT,
    guildid TEXT, 
    shardid INT, 
    language TEXT
);
CREATE TABLE IF NOT EXISTS userData (
    emote TEXT,
    language TEXT,
    tokens INT,
    lastupvote BIGINT,
    upvoteddays INT,
    shardid INT,
    userID TEXT
);
CREATE TABLE IF NOT EXISTS leaderboardEntries (
    userid TEXT,
    guildid TEXT,
    gameid TEXT,
    victories INT,
    losses INT,
    gamesplayed INT
);
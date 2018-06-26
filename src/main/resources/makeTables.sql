CREATE TABLE IF NOT EXISTS guildData (
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
    userid TEXT
);
CREATE TABLE IF NOT EXISTS leaderboardEntries (
    userid TEXT,
    guildid TEXT,
    gameid TEXT,
    victories INT,
    losses INT,
    gamesplayed INT
);
CREATE TABLE IF NOT EXISTS statsPlots (
    shardsOnline INT,
    avgCommandDelay BIGINT,
    totalCommandsExecuted BIGINT,
    totalGamesPlayed BIGINT,
    activeGames BIGINT,
    websocketPing BIGINT,
    guilds BIGINT,
    users BIGINT,
    textChannels BIGINT,
    ramUsage BIGINT,
    upvotes BIGINT,

    time BIGINT
);
CREATE TABLE IF NOT EXISTS achievements (
    type TEXT,
    userid TEXT,
    amount INT
);
CREATE TABLE IF NOT EXISTS blacklist (
    userid TEXT,
    botownerid TEXT,
    reason TEXT,
    time BIGINT
);
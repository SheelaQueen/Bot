SELECT * FROM (
    (SELECT * FROM statsplots WHERE "time" >= ? ORDER BY "time" LIMIT 1) UNION ALL
    (SELECT * FROM statsplots WHERE "time" < ? ORDER BY "time" DESC LIMIT 1)
) AS statsplots ORDER BY abs(?-"time") LIMIT 1;
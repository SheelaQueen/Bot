WITH ranked AS (
    SELECT userid, rank() over(ORDER BY tokens DESC) AS rank FROM userdata
)
SELECT rank FROM ranked WHERE userid = ?;
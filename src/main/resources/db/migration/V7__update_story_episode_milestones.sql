UPDATE story_episodes
SET required_streak = CASE episode_number
    WHEN 1 THEN 7
    WHEN 2 THEN 14
    WHEN 3 THEN 21
    WHEN 4 THEN 28
    WHEN 5 THEN 35
    ELSE required_streak
END
WHERE episode_number IN (1, 2, 3, 4, 5);

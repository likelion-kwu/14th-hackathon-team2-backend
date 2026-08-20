\set ON_ERROR_STOP on

BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $complete$
DECLARE
    v_clock                 TIMESTAMPTZ := clock_timestamp();
    v_now_utc               TIMESTAMP;
    v_today                 DATE;
    v_user_id               BIGINT;
    v_final_daily_id        BIGINT;
    v_ep1_id                BIGINT;
    v_ep1_required          INTEGER;
    v_count                 BIGINT;
    v_completed             BIGINT;
    v_points                BIGINT;
BEGIN
    v_now_utc := v_clock AT TIME ZONE 'UTC';
    v_today := (v_clock AT TIME ZONE 'Asia/Seoul')::date;

    SELECT COUNT(*)
      INTO v_count
      FROM users u
      JOIN guest_sessions gs ON gs.user_id = u.id
     WHERE u.nickname = '데모사자'
       AND (gs.expires_at IS NULL OR gs.expires_at > v_now_utc);

    IF v_count <> 1 THEN
        RAISE EXCEPTION
            'ABORT: expected exactly one valid 데모사자 guest, got %',
            v_count;
    END IF;

    SELECT u.id
      INTO v_user_id
      FROM users u
      JOIN guest_sessions gs ON gs.user_id = u.id
     WHERE u.nickname = '데모사자'
       AND (gs.expires_at IS NULL OR gs.expires_at > v_now_utc)
       FOR UPDATE OF u, gs;

    SELECT id, required_streak
      INTO v_ep1_id, v_ep1_required
      FROM story_episodes
     WHERE episode_number = 1
       AND active = TRUE
       FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ABORT: active EP1 does not exist';
    END IF;

    PERFORM id
      FROM daily_routines
     WHERE user_id = v_user_id
       AND service_date = v_today
     FOR UPDATE;

    SELECT COUNT(*), COUNT(rv.id)
      INTO v_count, v_completed
      FROM daily_routines dr
      LEFT JOIN routine_verifications rv
        ON rv.daily_routine_id = dr.id
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.category_snapshot <> 'TO_DO';

    IF v_count <> 3 OR v_completed <> 2 THEN
        RAISE EXCEPTION
            'ABORT: pre-completion progress must be 2/3, got %/%',
            v_completed, v_count;
    END IF;

    SELECT COALESCE(SUM(amount), 0)
      INTO v_points
      FROM routine_point_claims
     WHERE user_id = v_user_id;

    IF v_points <> 90 THEN
        RAISE EXCEPTION
            'ABORT: pre-completion points must be 90, got %',
            v_points;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM routine_point_claims pc
      JOIN daily_routines dr ON dr.id = pc.daily_routine_id
     WHERE pc.user_id = v_user_id
       AND dr.service_date = v_today;

    IF v_count <> 2 THEN
        RAISE EXCEPTION
            'ABORT: pre-completion today claim count must be 2, got %',
            v_count;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM generate_series(1, v_ep1_required - 1) g(day_offset)
     WHERE EXISTS (
           SELECT 1
             FROM daily_success_records ds
            WHERE ds.user_id = v_user_id
              AND ds.service_date = v_today - g.day_offset
     );

    IF v_count <> v_ep1_required - 1 THEN
        RAISE EXCEPTION
            'ABORT: EP1 pre-streak must be %, got %',
            v_ep1_required - 1, v_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM daily_success_records
         WHERE user_id = v_user_id
           AND service_date = v_today
    ) THEN
        RAISE EXCEPTION 'ABORT: today is already marked successful';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM user_story_unlocks
         WHERE user_id = v_user_id
           AND episode_id = v_ep1_id
    ) THEN
        RAISE EXCEPTION 'ABORT: EP1 is already unlocked';
    END IF;

    SELECT dr.id
      INTO v_final_daily_id
      FROM daily_routines dr
      JOIN photo_mission_templates pm
        ON pm.id = dr.mission_template_id
      LEFT JOIN routine_verifications rv
        ON rv.daily_routine_id = dr.id
      LEFT JOIN routine_point_claims pc
        ON pc.daily_routine_id = dr.id
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.content_snapshot = '물 한 잔 마시기'
       AND dr.verification_object_snapshot = '물병'
       AND dr.start_time_snapshot <= (v_clock AT TIME ZONE 'Asia/Seoul')::time
       AND dr.end_time_snapshot > (v_clock AT TIME ZONE 'Asia/Seoul')::time
       AND pm.active = TRUE
       AND rv.id IS NULL
       AND pc.id IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ABORT: final PHOTO routine is not claimable now';
    END IF;

    INSERT INTO routine_verifications (
        daily_routine_id,
        verification_type,
        verified_at,
        created_at
    )
    VALUES (
        v_final_daily_id,
        'PHOTO',
        v_now_utc,
        v_now_utc
    );

    INSERT INTO daily_success_records (
        user_id,
        service_date,
        completed_at,
        created_at
    )
    VALUES (
        v_user_id,
        v_today,
        v_now_utc,
        v_now_utc
    );

    INSERT INTO user_story_unlocks (
        user_id,
        episode_id,
        unlocked_at
    )
    VALUES (
        v_user_id,
        v_ep1_id,
        v_now_utc
    );

    SELECT COUNT(*), COUNT(rv.id)
      INTO v_count, v_completed
      FROM daily_routines dr
      LEFT JOIN routine_verifications rv
        ON rv.daily_routine_id = dr.id
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.category_snapshot <> 'TO_DO';

    IF v_count <> 3 OR v_completed <> 3 THEN
        RAISE EXCEPTION
            'ABORT: post-completion progress must be 3/3, got %/%',
            v_completed, v_count;
    END IF;

    SELECT COALESCE(SUM(amount), 0)
      INTO v_points
      FROM routine_point_claims
     WHERE user_id = v_user_id;

    IF v_points <> 90 THEN
        RAISE EXCEPTION
            'ABORT: points changed before UI claim, got %',
            v_points;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM routine_point_claims pc
      JOIN daily_routines dr ON dr.id = pc.daily_routine_id
     WHERE pc.user_id = v_user_id
       AND dr.service_date = v_today;

    IF v_count <> 2 THEN
        RAISE EXCEPTION
            'ABORT: today claim count changed before UI claim, got %',
            v_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM daily_success_records
         WHERE user_id = v_user_id
           AND service_date = v_today
    ) OR NOT EXISTS (
        SELECT 1
          FROM user_story_unlocks
         WHERE user_id = v_user_id
           AND episode_id = v_ep1_id
    ) THEN
        RAISE EXCEPTION 'ABORT: completion milestones were not persisted';
    END IF;

    IF EXISTS (
        SELECT 1 FROM user_items WHERE user_id = v_user_id
    ) OR EXISTS (
        SELECT 1 FROM item_unlock_records WHERE user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'ABORT: item state changed before the 100P claim';
    END IF;

    RAISE NOTICE
        'COMPLETED nickname=데모사자 date=% final_daily_routine_id=% points=90 today_claims=2 EP1=%',
        v_today,
        v_final_daily_id,
        v_ep1_required;
END
$complete$;

COMMIT;

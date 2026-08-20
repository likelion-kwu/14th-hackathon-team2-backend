\set ON_ERROR_STOP on

BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $rewind$
DECLARE
    v_clock                 TIMESTAMPTZ := clock_timestamp();
    v_now_utc               TIMESTAMP;
    v_today                 DATE;
    v_user_id               BIGINT;
    v_final_daily_id        BIGINT;
    v_ep1_id                BIGINT;
    v_item_id               BIGINT;
    v_count                 BIGINT;
    v_completed             BIGINT;
    v_points                BIGINT;
    v_rows                  BIGINT;
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

    SELECT id
      INTO v_ep1_id
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

    SELECT dr.id
      INTO v_final_daily_id
      FROM daily_routines dr
      JOIN routine_verifications rv
        ON rv.daily_routine_id = dr.id
       AND rv.verification_type = 'PHOTO'
      JOIN routine_point_claims pc
        ON pc.daily_routine_id = dr.id
       AND pc.user_id = v_user_id
       AND pc.amount = 10
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.content_snapshot = '물 한 잔 마시기'
       AND dr.verification_object_snapshot = '물병';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ABORT: completed final PHOTO routine does not exist';
    END IF;

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
            'ABORT: final progress must be 3/3, got %/%',
            v_completed, v_count;
    END IF;

    SELECT COALESCE(SUM(amount), 0)
      INTO v_points
      FROM routine_point_claims
     WHERE user_id = v_user_id;

    IF v_points <> 100 THEN
        RAISE EXCEPTION
            'ABORT: final points must be 100, got %',
            v_points;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM routine_point_claims pc
      JOIN daily_routines dr ON dr.id = pc.daily_routine_id
     WHERE pc.user_id = v_user_id
       AND dr.service_date = v_today;

    IF v_count <> 3 THEN
        RAISE EXCEPTION
            'ABORT: final today claim count must be 3, got %',
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
        RAISE EXCEPTION 'ABORT: final success/story state is incomplete';
    END IF;

    SELECT item_id
      INTO v_item_id
      FROM item_unlock_records
     WHERE user_id = v_user_id
       AND required_points = 100
       AND item_id IS NOT NULL
       FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ABORT: 100P item milestone was not processed';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_items
     WHERE user_id = v_user_id
       AND item_id = v_item_id;

    IF v_count <> 1 THEN
        RAISE EXCEPTION
            'ABORT: expected one matching owned item, got %',
            v_count;
    END IF;

    SELECT
        (SELECT COUNT(*) FROM item_unlock_records
          WHERE user_id = v_user_id)
      + (SELECT COUNT(*) FROM user_items
          WHERE user_id = v_user_id)
      INTO v_count;

    IF v_count <> 2 THEN
        RAISE EXCEPTION
            'ABORT: unexpected extra item rows exist for demo user';
    END IF;

    DELETE FROM item_unlock_records
     WHERE user_id = v_user_id
       AND required_points = 100
       AND item_id = v_item_id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % item milestone rows', v_rows;
    END IF;

    DELETE FROM user_items
     WHERE user_id = v_user_id
       AND item_id = v_item_id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % owned item rows', v_rows;
    END IF;

    DELETE FROM routine_point_claims
     WHERE user_id = v_user_id
       AND daily_routine_id = v_final_daily_id
       AND amount = 10;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % final point claims', v_rows;
    END IF;

    DELETE FROM user_story_unlocks
     WHERE user_id = v_user_id
       AND episode_id = v_ep1_id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % EP1 unlock rows', v_rows;
    END IF;

    DELETE FROM daily_success_records
     WHERE user_id = v_user_id
       AND service_date = v_today;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % today success rows', v_rows;
    END IF;

    DELETE FROM routine_verifications
     WHERE daily_routine_id = v_final_daily_id
       AND verification_type = 'PHOTO';
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 1 THEN
        RAISE EXCEPTION 'ABORT: deleted % final verifications', v_rows;
    END IF;

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
            'ABORT: rewound progress must be 2/3, got %/%',
            v_completed, v_count;
    END IF;

    SELECT COALESCE(SUM(amount), 0)
      INTO v_points
      FROM routine_point_claims
     WHERE user_id = v_user_id;

    IF v_points <> 90 THEN
        RAISE EXCEPTION
            'ABORT: rewound points must be 90, got %',
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
            'ABORT: rewound today claim count must be 2, got %',
            v_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM daily_success_records
         WHERE user_id = v_user_id AND service_date = v_today
    ) OR EXISTS (
        SELECT 1 FROM user_story_unlocks
         WHERE user_id = v_user_id AND episode_id = v_ep1_id
    ) OR EXISTS (
        SELECT 1 FROM user_items WHERE user_id = v_user_id
    ) OR EXISTS (
        SELECT 1 FROM item_unlock_records WHERE user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'ABORT: rewound milestone state is not clean';
    END IF;

    RAISE NOTICE
        'REWOUND nickname=데모사자 date=% final_daily_routine_id=% progress=2/3 points=90 today_claims=2',
        v_today,
        v_final_daily_id;
END
$rewind$;

COMMIT;

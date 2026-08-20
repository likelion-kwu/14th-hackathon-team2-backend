\set ON_ERROR_STOP on

BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

LOCK TABLE users, guest_sessions IN SHARE MODE NOWAIT;

DO $seed$
DECLARE
    v_clock                    TIMESTAMPTZ;
    v_now_utc                  TIMESTAMP;
    v_today                    DATE;
    v_candidate_count          BIGINT;
    v_user_id                  BIGINT;
    v_session_id               UUID;
    v_expires_at               TIMESTAMP;
    v_existing_rows            BIGINT;
    v_avatar_count             BIGINT;
    v_ep1_id                   BIGINT;
    v_ep1_required             INTEGER;
    v_prior_success_days       INTEGER;
    v_mission_template_id      BIGINT;
    v_profile_id               BIGINT;
    v_routine_1                BIGINT;
    v_routine_2                BIGINT;
    v_routine_3                BIGINT;
    v_rows                     BIGINT;
    v_count                    BIGINT;
    v_completed                BIGINT;
    v_points                   BIGINT;
BEGIN
    v_clock := clock_timestamp();
    v_now_utc := v_clock AT TIME ZONE 'UTC';
    v_today := (v_clock AT TIME ZONE 'Asia/Seoul')::date;

    SELECT COUNT(*)
      INTO v_candidate_count
      FROM users u
      JOIN guest_sessions gs ON gs.user_id = u.id
     WHERE u.nickname = '데모사자';

    IF v_candidate_count <> 1 THEN
        RAISE EXCEPTION
            'ABORT: nickname=데모사자 guest candidate count must be 1, got %',
            v_candidate_count;
    END IF;

    SELECT u.id, gs.id, gs.expires_at
      INTO v_user_id, v_session_id, v_expires_at
      FROM users u
      JOIN guest_sessions gs ON gs.user_id = u.id
     WHERE u.nickname = '데모사자'
     ORDER BY gs.created_at DESC, u.id DESC
     LIMIT 1
       FOR UPDATE OF u, gs;

    IF v_expires_at IS NOT NULL AND v_expires_at <= v_now_utc THEN
        RAISE EXCEPTION 'ABORT: demo guest session is expired';
    END IF;

    SELECT COUNT(*)
      INTO v_avatar_count
      FROM avatars
     WHERE user_id = v_user_id
       AND growth_track = 'HEALTH_FIT'
       AND asset_set_key IS NOT NULL
       AND asset_set_key <> '';

    IF v_avatar_count <> 1 THEN
        RAISE EXCEPTION
            'ABORT: expected one configured HEALTH_FIT avatar, got %',
            v_avatar_count;
    END IF;

    SELECT COALESCE(SUM(x.n), 0)
      INTO v_existing_rows
      FROM (
            SELECT COUNT(*)::BIGINT AS n FROM speech_style_profiles
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM speech_analysis_jobs
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM speech_style_examples e
             JOIN speech_style_profiles p ON p.id = e.profile_id
             WHERE p.user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM avatar_dialogues d
             JOIN speech_style_profiles p ON p.id = d.profile_id
             WHERE p.user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM routines
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM routine_repeat_days rd
             JOIN routines r ON r.id = rd.routine_id
             WHERE r.user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM daily_routines
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM routine_verifications rv
             JOIN daily_routines dr ON dr.id = rv.daily_routine_id
             WHERE dr.user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM routine_point_claims
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM daily_success_records
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM user_story_unlocks
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM user_items
             WHERE user_id = v_user_id
            UNION ALL
            SELECT COUNT(*) FROM item_unlock_records
             WHERE user_id = v_user_id
      ) x;

    IF v_existing_rows <> 0 THEN
        RAISE EXCEPTION
            'ABORT: demo user already has % seeded domain rows',
            v_existing_rows;
    END IF;

    SELECT id, required_streak
      INTO v_ep1_id, v_ep1_required
      FROM story_episodes
     WHERE episode_number = 1
       AND active = TRUE;

    IF NOT FOUND OR v_ep1_required < 2 THEN
        RAISE EXCEPTION 'ABORT: active EP1 milestone is invalid';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM story_episodes
     WHERE active = TRUE
       AND id <> v_ep1_id
       AND required_streak <= v_ep1_required;

    IF v_count <> 0 THEN
        RAISE EXCEPTION
            'ABORT: % additional episodes would unlock with EP1',
            v_count;
    END IF;

    v_prior_success_days := v_ep1_required - 1;

    SELECT id
      INTO v_mission_template_id
      FROM photo_mission_templates
     WHERE active = TRUE
     ORDER BY CASE WHEN gesture_code = 'THUMBS_UP' THEN 0 ELSE 1 END, id
     LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ABORT: no active photo mission template';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM items
     WHERE active = TRUE;

    IF v_count < 1 THEN
        INSERT INTO items (
            name, item_type, asset_key, active, created_at
        )
        VALUES (
            '데모 별 모자', 'HEAD', 'test-item', TRUE, v_now_utc
        );
        RAISE NOTICE 'No active Item existed; seeded test-item master data';
    END IF;

    INSERT INTO speech_style_profiles (
        user_id, source_type, preset_code, speech_level, sentence_length,
        directness, warmth, playfulness, emotional_intensity, style_json,
        profanity_detected, profanity_enabled, valid_message_count,
        created_at, updated_at
    )
    VALUES (
        v_user_id, 'PRESET', 'CALM', 'BANMAL', 'SHORT',
        'MEDIUM', 'MEDIUM', 'LOW', 'MEDIUM',
        '{"profanity":{"detected":false,"enabledByUser":false,"allowedExpressions":[]},"personalInsultAllowed":false}'::jsonb,
        FALSE, FALSE, NULL, v_now_utc, v_now_utc
    )
    RETURNING id INTO v_profile_id;

    INSERT INTO avatar_dialogues (
        profile_id, situation, content, contains_user_name,
        contains_profanity, last_used_at, use_count, created_at
    )
    SELECT v_profile_id, d.situation, d.content, FALSE, FALSE, NULL, 0, v_now_utc
    FROM (
        VALUES
        ('ROUTINE_UPCOMING', '곧 시작할 시간이야.'),
        ('ROUTINE_UPCOMING', '미리 가볍게 준비해 두자.'),
        ('ROUTINE_UPCOMING', '서두르지 말고 하나씩 가자.'),
        ('ROUTINE_UPCOMING', '할 일만 잠깐 확인해 보자.'),
        ('ROUTINE_UPCOMING', '시작 전 숨 한번 고르자.'),
        ('ROUTINE_AVAILABLE', '이제 시작해도 좋아.'),
        ('ROUTINE_AVAILABLE', '지금 하나 해볼까?'),
        ('ROUTINE_AVAILABLE', '준비됐으면 천천히 시작하자.'),
        ('ROUTINE_AVAILABLE', '지금이 딱 좋은 시간이야.'),
        ('ROUTINE_AVAILABLE', '작게 시작해도 충분해.'),
        ('ROUTINE_REMINDER', '아직 시간이 남아 있어.'),
        ('ROUTINE_REMINDER', '잊기 전에 하나 해두자.'),
        ('ROUTINE_REMINDER', '지금 잠깐이면 할 수 있어.'),
        ('ROUTINE_REMINDER', '무리 말고 가능한 만큼 하자.'),
        ('ROUTINE_REMINDER', '한 번만 가볍게 움직여 보자.'),
        ('ROUTINE_COMPLETED', '좋아, 하나 끝냈어.'),
        ('ROUTINE_COMPLETED', '차분하게 잘 해냈어.'),
        ('ROUTINE_COMPLETED', '오늘도 한 걸음 나아갔네.'),
        ('ROUTINE_COMPLETED', '작은 실천이 잘 쌓였어.'),
        ('ROUTINE_COMPLETED', '지금 흐름 아주 좋아.'),
        ('ALL_COMPLETED', '오늘 할 일을 모두 마쳤어.'),
        ('ALL_COMPLETED', '오늘도 충분히 잘했어.'),
        ('ALL_COMPLETED', '깔끔하게 하루를 채웠네.'),
        ('ALL_COMPLETED', '이제 편하게 쉬어도 좋아.'),
        ('ALL_COMPLETED', '오늘의 약속을 다 지켰어.'),
        ('STREAK_CONTINUED', '좋은 흐름이 계속되고 있어.'),
        ('STREAK_CONTINUED', '꾸준함이 멋지게 쌓였어.'),
        ('STREAK_CONTINUED', '오늘도 기록을 이어갔네.'),
        ('STREAK_CONTINUED', '차근차근 잘 이어가는 중이야.'),
        ('STREAK_CONTINUED', '이 리듬 그대로 가보자.'),
        ('STREAK_BROKEN', '괜찮아, 오늘부터 다시 가자.'),
        ('STREAK_BROKEN', '한 번 쉰 건 실패가 아니야.'),
        ('STREAK_BROKEN', '다시 시작하면 충분해.'),
        ('STREAK_BROKEN', '작게 하나부터 이어가자.'),
        ('STREAK_BROKEN', '지금의 한 걸음에 집중하자.'),
        ('RETURN_AFTER_ABSENCE', '다시 와줘서 반가워.'),
        ('RETURN_AFTER_ABSENCE', '돌아온 것만으로도 충분해.'),
        ('RETURN_AFTER_ABSENCE', '오늘부터 천천히 다시 가자.'),
        ('RETURN_AFTER_ABSENCE', '쉬었으니 가볍게 시작하자.'),
        ('RETURN_AFTER_ABSENCE', '부담 없이 하나만 해보자.')
    ) AS d(situation, content);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 40 THEN
        RAISE EXCEPTION 'ABORT: expected 40 avatar dialogues, inserted %', v_rows;
    END IF;

    INSERT INTO routines (
        user_id, category, content, start_time, end_time, repeat_type,
        verification_object, effective_from, deleted_at, created_at, updated_at
    )
    VALUES (
        v_user_id, 'HEALTH_FIT', '아침 스트레칭 10분', TIME '00:00', TIME '23:59',
        'DAILY', '요가 매트', v_today - (v_prior_success_days + 4), NULL,
        v_now_utc, v_now_utc
    )
    RETURNING id INTO v_routine_1;

    INSERT INTO routines (
        user_id, category, content, start_time, end_time, repeat_type,
        verification_object, effective_from, deleted_at, created_at, updated_at
    )
    VALUES (
        v_user_id, 'WELL_BEING', '영양제 챙겨 먹기', TIME '00:00', TIME '23:59',
        'DAILY', '영양제', v_today - (v_prior_success_days + 4), NULL,
        v_now_utc, v_now_utc
    )
    RETURNING id INTO v_routine_2;

    INSERT INTO routines (
        user_id, category, content, start_time, end_time, repeat_type,
        verification_object, effective_from, deleted_at, created_at, updated_at
    )
    VALUES (
        v_user_id, 'WELL_BEING', '물 한 잔 마시기', TIME '00:00', TIME '23:59',
        'DAILY', '물병', v_today - (v_prior_success_days + 4), NULL,
        v_now_utc, v_now_utc
    )
    RETURNING id INTO v_routine_3;

    INSERT INTO daily_routines (
        routine_id, user_id, service_date, category_snapshot, content_snapshot,
        start_time_snapshot, end_time_snapshot, verification_object_snapshot,
        mission_template_id, created_at, updated_at
    )
    SELECT
        r.id, v_user_id, v_today - g.day_offset, r.category, r.content,
        r.start_time, r.end_time, r.verification_object,
        CASE
            WHEN g.day_offset > 0 AND r.id IN (v_routine_1, v_routine_3)
                THEN v_mission_template_id
            WHEN g.day_offset = 0 AND r.id = v_routine_3
                THEN v_mission_template_id
            ELSE NULL
        END,
        v_now_utc, v_now_utc
    FROM routines r
    CROSS JOIN generate_series(0, v_prior_success_days + 4) AS g(day_offset)
    WHERE r.id IN (v_routine_1, v_routine_2, v_routine_3);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 3 * (v_prior_success_days + 5) THEN
        RAISE EXCEPTION 'ABORT: unexpected daily_routine row count %', v_rows;
    END IF;

    INSERT INTO routine_verifications (
        daily_routine_id, verification_type, verified_at, created_at
    )
    SELECT
        dr.id, 'PHOTO',
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC',
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC'
    FROM daily_routines dr
    WHERE dr.user_id = v_user_id
      AND dr.service_date BETWEEN
          v_today - (v_prior_success_days + 4)
          AND v_today - (v_prior_success_days + 1)
      AND dr.routine_id IN (v_routine_1, v_routine_3);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 8 THEN
        RAISE EXCEPTION 'ABORT: expected 8 point-bank verifications, inserted %', v_rows;
    END IF;

    INSERT INTO routine_point_claims (
        user_id, daily_routine_id, amount, claimed_at, created_at
    )
    SELECT
        v_user_id, dr.id, 10,
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC',
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC'
    FROM daily_routines dr
    WHERE dr.user_id = v_user_id
      AND dr.service_date BETWEEN
          v_today - (v_prior_success_days + 4)
          AND v_today - (v_prior_success_days + 1)
      AND dr.routine_id IN (v_routine_1, v_routine_3);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 8 THEN
        RAISE EXCEPTION 'ABORT: expected 8 historical claims, inserted %', v_rows;
    END IF;

    INSERT INTO routine_verifications (
        daily_routine_id, verification_type, verified_at, created_at
    )
    SELECT
        dr.id,
        CASE WHEN dr.routine_id IN (v_routine_1, v_routine_3) THEN 'PHOTO' ELSE 'CHECK' END,
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC',
        ((dr.service_date + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC'
    FROM daily_routines dr
    WHERE dr.user_id = v_user_id
      AND dr.service_date BETWEEN v_today - v_prior_success_days AND v_today - 1;

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 3 * v_prior_success_days THEN
        RAISE EXCEPTION
            'ABORT: expected % streak verifications, inserted %',
            3 * v_prior_success_days, v_rows;
    END IF;

    INSERT INTO daily_success_records (
        user_id, service_date, completed_at, created_at
    )
    SELECT
        v_user_id, v_today - g.day_offset,
        (((v_today - g.day_offset) + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC',
        (((v_today - g.day_offset) + TIME '12:00') AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC'
    FROM generate_series(1, v_prior_success_days) AS g(day_offset);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> v_prior_success_days THEN
        RAISE EXCEPTION 'ABORT: expected % success days, inserted %', v_prior_success_days, v_rows;
    END IF;

    INSERT INTO routine_verifications (
        daily_routine_id, verification_type, verified_at, created_at
    )
    SELECT dr.id, 'CHECK', v_now_utc, v_now_utc
    FROM daily_routines dr
    WHERE dr.user_id = v_user_id
      AND dr.service_date = v_today
      AND dr.routine_id IN (v_routine_1, v_routine_2);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 2 THEN
        RAISE EXCEPTION 'ABORT: expected 2 today verifications, inserted %', v_rows;
    END IF;

    INSERT INTO routine_point_claims (
        user_id, daily_routine_id, amount, claimed_at, created_at
    )
    SELECT v_user_id, dr.id, 5, v_now_utc, v_now_utc
    FROM daily_routines dr
    WHERE dr.user_id = v_user_id
      AND dr.service_date = v_today
      AND dr.routine_id IN (v_routine_1, v_routine_2);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows <> 2 THEN
        RAISE EXCEPTION 'ABORT: expected 2 today claims, inserted %', v_rows;
    END IF;

    SELECT COUNT(*), COUNT(rv.id)
      INTO v_count, v_completed
      FROM daily_routines dr
      LEFT JOIN routine_verifications rv ON rv.daily_routine_id = dr.id
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.category_snapshot <> 'TO_DO';

    IF v_count <> 3 OR v_completed <> 2 THEN
        RAISE EXCEPTION
            'ABORT: today progress expected 2/3, got %/%',
            v_completed, v_count;
    END IF;

    SELECT COUNT(*), COALESCE(SUM(amount), 0)
      INTO v_count, v_points
      FROM routine_point_claims
     WHERE user_id = v_user_id;

    IF v_count <> 10 OR v_points <> 90 THEN
        RAISE EXCEPTION
            'ABORT: point state expected 10 claims/90P, got % claims/%P',
            v_count, v_points;
    END IF;

    SELECT COUNT(*), COALESCE(SUM(rpc.amount), 0)
      INTO v_count, v_points
      FROM routine_point_claims rpc
      JOIN daily_routines dr ON dr.id = rpc.daily_routine_id
     WHERE rpc.user_id = v_user_id
       AND dr.service_date = v_today;

    IF v_count <> 2 OR v_points <> 10 THEN
        RAISE EXCEPTION
            'ABORT: today claims expected 2 claims/10P, got %/%',
            v_count, v_points;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM daily_success_records
     WHERE user_id = v_user_id;

    IF v_count <> v_prior_success_days THEN
        RAISE EXCEPTION
            'ABORT: success day count expected %, got %',
            v_prior_success_days, v_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM daily_success_records
         WHERE user_id = v_user_id AND service_date = v_today
    ) THEN
        RAISE EXCEPTION 'ABORT: today must not yet be a DailySuccess';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM daily_routines dr
      JOIN photo_mission_templates pm ON pm.id = dr.mission_template_id
      LEFT JOIN routine_verifications rv ON rv.daily_routine_id = dr.id
     WHERE dr.user_id = v_user_id
       AND dr.service_date = v_today
       AND dr.routine_id = v_routine_3
       AND dr.verification_object_snapshot = '물병'
       AND pm.active = TRUE
       AND rv.id IS NULL;

    IF v_count <> 1 THEN
        RAISE EXCEPTION 'ABORT: final PHOTO routine is not ready';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM avatar_dialogues
     WHERE profile_id = v_profile_id;

    IF v_count <> 40 THEN
        RAISE EXCEPTION 'ABORT: expected 40 dialogues after insert, got %', v_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM routine_point_claims pc
          JOIN routine_verifications rv ON rv.daily_routine_id = pc.daily_routine_id
         WHERE pc.user_id = v_user_id
           AND pc.amount <> CASE rv.verification_type WHEN 'PHOTO' THEN 10 ELSE 5 END
    ) THEN
        RAISE EXCEPTION 'ABORT: point amount does not match verification type';
    END IF;

    SELECT
        (SELECT COUNT(*) FROM user_story_unlocks WHERE user_id = v_user_id)
      + (SELECT COUNT(*) FROM user_items WHERE user_id = v_user_id)
      + (SELECT COUNT(*) FROM item_unlock_records WHERE user_id = v_user_id)
      INTO v_count;

    IF v_count <> 0 THEN
        RAISE EXCEPTION 'ABORT: story/item must remain locked at 90P';
    END IF;

    RAISE NOTICE
        'SEEDED nickname=데모사자 date=% EP1=% prior_streak=% final_daily_routine_id=%',
        v_today,
        v_ep1_required,
        v_prior_success_days,
        (
          SELECT id FROM daily_routines
           WHERE user_id = v_user_id
             AND service_date = v_today
             AND routine_id = v_routine_3
        );
END
$seed$;

COMMIT;

WITH target AS (
    SELECT u.id
      FROM users u
      JOIN guest_sessions gs ON gs.user_id = u.id
     WHERE u.nickname = '데모사자'
), service_day AS (
    SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date AS d
)
SELECT
    '데모사자' AS nickname,
    (SELECT COUNT(*) FROM avatars a WHERE a.user_id = t.id) AS avatar_count,
    (SELECT COUNT(*) FROM speech_style_profiles p WHERE p.user_id = t.id) AS speech_profile_count,
    (SELECT COUNT(*) FROM avatar_dialogues ad JOIN speech_style_profiles p ON p.id = ad.profile_id WHERE p.user_id = t.id) AS dialogue_count,
    (SELECT COUNT(*) FROM daily_routines dr WHERE dr.user_id = t.id AND dr.service_date = sd.d AND dr.category_snapshot <> 'TO_DO') AS today_total,
    (SELECT COUNT(*) FROM daily_routines dr JOIN routine_verifications rv ON rv.daily_routine_id = dr.id WHERE dr.user_id = t.id AND dr.service_date = sd.d AND dr.category_snapshot <> 'TO_DO') AS today_completed,
    (SELECT COALESCE(SUM(amount), 0) FROM routine_point_claims WHERE user_id = t.id) AS total_points,
    (SELECT COUNT(*) FROM routine_point_claims pc JOIN daily_routines dr ON dr.id = pc.daily_routine_id WHERE pc.user_id = t.id AND dr.service_date = sd.d) AS today_claim_count,
    (SELECT COUNT(*) FROM daily_success_records ds WHERE ds.user_id = t.id) AS prior_success_days,
    (SELECT required_streak FROM story_episodes WHERE episode_number = 1 AND active = TRUE) AS ep1_required_streak,
    (SELECT COUNT(*) FROM user_story_unlocks WHERE user_id = t.id) AS story_unlock_count,
    (SELECT COUNT(*) FROM user_items WHERE user_id = t.id) AS owned_item_count,
    (SELECT COUNT(*) FROM item_unlock_records WHERE user_id = t.id) AS item_milestone_count
FROM target t
CROSS JOIN service_day sd;

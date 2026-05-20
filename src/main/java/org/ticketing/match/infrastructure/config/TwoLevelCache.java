package org.ticketing.match.infrastructure.config;

import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

/**
 * L1(Caffeine) → L2(Redis) 2계층 읽기 투명(read-through) {@link Cache} 구현체.
 *
 * <h3>읽기 전략</h3>
 * <pre>
 *   get(key)
 *     ├─ L1 히트 → 즉시 반환 (~0ms)
 *     ├─ L1 미스 + L2 히트 → L1 역적재 후 반환 (~1ms)
 *     └─ L1·L2 모두 미스 → null 반환 → {@code @Cacheable} 이 DB 조회 후 put() 호출
 * </pre>
 *
 * <h3>get(key, callable) — {@code sync=true} 경로</h3>
 * <p>L1(Caffeine) 의 {@code computeIfAbsent} 로 JVM 내 단일 로더를 보장한다.
 * 로더 내부에서 L2(Redis) 를 먼저 확인하여 L2 히트 시 DB 호출을 생략한다.
 *
 * <h3>쓰기·무효화</h3>
 * <p>{@code put} / {@code evict} / {@code clear} 는 L1·L2 양쪽에 동시 적용된다.
 *
 * <h3>Redis 장애 내성</h3>
 * <p>L2 작업 중 예외 발생 시 WARN 로그 후 L1 만으로 서비스를 계속 유지한다.
 */
@Slf4j
public class TwoLevelCache implements Cache {

    private final String name;
    private final Cache l1;   // Caffeine
    private final Cache l2;   // Redis

    public TwoLevelCache(String name, Cache l1, Cache l2) {
        this.name = name;
        this.l1   = l1;
        this.l2   = l2;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /**
     * L1 → L2 순서로 조회한다.
     * L2 히트 시 L1 에 역적재(back-fill)하여 이후 요청은 L1 에서 처리된다.
     */
    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper l1Val = l1.get(key);
        if (l1Val != null) {
            return l1Val;
        }

        ValueWrapper l2Val = getFromL2(key);
        if (l2Val != null) {
            l1.put(key, l2Val.get());   // L1 역적재
        }
        return l2Val;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T l1Val = l1.get(key, type);
        if (l1Val != null) {
            return l1Val;
        }

        T l2Val = getFromL2(key, type);
        if (l2Val != null) {
            l1.put(key, l2Val);         // L1 역적재
        }
        return l2Val;
    }

    /**
     * {@code @Cacheable(sync = true)} 경로.
     *
     * <p>L1(Caffeine) 의 {@code computeIfAbsent} 에 위임하여 JVM 내 단일 로더를 보장한다.
     * 로더 내부에서 L2 를 먼저 확인하고, L2 히트 시 {@code valueLoader}(DB) 를 건너뛴다.
     * L2 미스 시에만 DB 를 조회하고 L2 에 적재한다.
     *
     * <pre>
     *   단일 로더(L1.computeIfAbsent)
     *     ├─ L2 히트 → L2 값 반환 (DB 호출 없음)
     *     └─ L2 미스 → DB 조회 → L2 저장 → L1 저장
     * </pre>
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        return (T) l1.get(key, () -> {
            // 단일 로더 내부: L2 먼저 확인
            ValueWrapper l2Wrapper = getFromL2(key);
            if (l2Wrapper != null) {
                return l2Wrapper.get();          // DB 호출 생략
            }
            // L2 미스 → DB 조회 → L2 저장
            T value = valueLoader.call();
            putToL2(key, value);
            return value;
        });
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────────────

    @Override
    public void put(Object key, Object value) {
        l1.put(key, value);
        putToL2(key, value);
    }

    @Override
    public void evict(Object key) {
        l1.evict(key);
        evictFromL2(key);
    }

    @Override
    public void clear() {
        l1.clear();
        try {
            l2.clear();
        } catch (RuntimeException e) {
            log.warn("[TwoLevelCache] L2 CLEAR 실패 — cache={}, 원인={}", name, e.getMessage());
        }
    }

    // ── L2 헬퍼 (Redis 장애 내성) ─────────────────────────────────────────────

    private ValueWrapper getFromL2(Object key) {
        try {
            return l2.get(key);
        } catch (RuntimeException e) {
            log.warn("[TwoLevelCache] L2 GET 실패 — cache={}, key={}, 원인={}", name, key, e.getMessage());
            return null;
        }
    }

    private <T> T getFromL2(Object key, Class<T> type) {
        try {
            return l2.get(key, type);
        } catch (RuntimeException e) {
            log.warn("[TwoLevelCache] L2 GET 실패 — cache={}, key={}, 원인={}", name, key, e.getMessage());
            return null;
        }
    }

    private void putToL2(Object key, Object value) {
        try {
            l2.put(key, value);
        } catch (RuntimeException e) {
            log.warn("[TwoLevelCache] L2 PUT 실패 — cache={}, key={}, 원인={}", name, key, e.getMessage());
        }
    }

    private void evictFromL2(Object key) {
        try {
            l2.evict(key);
        } catch (RuntimeException e) {
            log.warn("[TwoLevelCache] L2 EVICT 실패 — cache={}, key={}, 원인={}", name, key, e.getMessage());
        }
    }
}

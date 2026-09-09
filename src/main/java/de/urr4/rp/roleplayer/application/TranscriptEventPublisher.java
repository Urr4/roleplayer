package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out hub for live transcript updates. Recording processing pushes newly
 * persisted {@link TranscriptSegment}s here as they're produced, and any
 * number of browser clients can subscribe (per session) to receive them over
 * Server-Sent Events while a recording is in progress.
 */
@Component
public class TranscriptEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TranscriptEventPublisher.class);

    // 0L disables the emitter's own timeout — subscriptions live until the
    // client disconnects (tab closed) or the emitter completes/errors.
    private static final long NO_TIMEOUT = 0L;

    private final Map<String, List<SseEmitter>> emittersBySessionId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String chronicleId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        List<SseEmitter> emitters = emittersBySessionId.computeIfAbsent(chronicleId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void publish(String chronicleId, TranscriptSegment segment) {
        List<SseEmitter> emitters = emittersBySessionId.get(chronicleId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("segment").data(segment));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                // Best-effort only: the emitter's underlying AsyncContext may
                // already be in an errored/completed state (e.g. the browser
                // tab was closed or the connection dropped), in which case
                // Tomcat itself throws from completeWithError() ("a
                // non-container thread attempted to use the AsyncContext
                // after an error had occurred..."). Publishing a live
                // transcript update is a non-essential side effect of
                // recording processing, so any failure here - including from
                // this cleanup call - must never propagate and fail the
                // caller's transcription/finalization work.
                try {
                    emitter.completeWithError(e);
                } catch (RuntimeException cleanupError) {
                    log.debug("Failed to complete already-broken SSE emitter for chronicle {}", chronicleId, cleanupError);
                }
            }
        }
    }
}

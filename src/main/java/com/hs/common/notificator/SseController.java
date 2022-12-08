package com.hs.common.notificator;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.infinispan.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class SseController {
    
    private static final String EVENT_CONNECT = "connect";
    private static final String EVENT_SSE = "sse";
    
    /** 생성된 EventStream 목록을 저장하기 위한 로컬 캐시 */
    @Autowired Cache<String, SseEmitter> sseEmitterCache;
    
    /** 전송된 Event 목록을 임시 저장하기 위한 분산 캐시 */
    @Autowired Cache<String, String> sseEventCache;

    /**
     * 클라이언트의 요청으로 특정 userId에 대한 EventStream 생성
     * 
     * @param userId
     * @param lastEventId 클라이언트가 마지막으로 수신한 Last-Event-ID
     * @param response
     * @return
     */
    @GetMapping("/create-event-stream-by-user-id")
    public SseEmitter createEventStream(@RequestParam String userId, @RequestHeader(name = "Last-Event-ID", required = false, defaultValue = "") String lastEventId, HttpServletResponse response) {
        log.info(">> Accepted {}, Last-Event-ID={}", userId, lastEventId);

        // 리버스 프록시에서의 오동작을 방지
        response.addHeader("X-Accel-Buffering", "no");
        
        // EventStream 생성 후 10분 경과시 제거
        // 클라이언트는 연결 종료 인지 후 EventStream 자동 재생성 요청
        SseEmitter sseEmitter = new SseEmitter(CacheConfig.TIMEOUT * 60 * 1000l);
        
        // 로컬 캐시에 생성된 EventStream 저장
        sseEmitterCache.put(userId + "_" + System.currentTimeMillis(), sseEmitter);
        
        // 첫 생성시 더미 Event 전송. 503 Service Unavailable 오류 응답 예방
        final String eventId = userId + "_" + System.currentTimeMillis();
        final String eventData = "EventStream Created. userId=" + userId;
        SseEventBuilder event = SseEmitter.event().name(EVENT_CONNECT).id(eventId).data(eventData);
        try {
            sseEmitter.send(event);
            log.debug("<< sent [첫 생성시 더미 Event] {} : {}", eventId, eventData);
        } catch (IOException e) {
            new RuntimeException("첫 생성시 더미 Event 전송 에러", e);
        }

        // 클라이언트가 미수신한 Event 목록이 존재할 경우 전송하여 Event 유실을 예방
        if (!lastEventId.isEmpty()) {
            sseEventCache.forEach((id, data) -> {
                if (id.startsWith(userId) && id.compareTo(lastEventId) > 0) {
                    SseEventBuilder unsentEvent = SseEmitter.event().name(EVENT_SSE).id(id).data(data);
                    try {
                        sseEmitter.send(unsentEvent);
                        log.debug("<< sent [클라이언트가 미수신한 Event] {} : {}", id, data);
                    } catch (IOException e) {
                        new RuntimeException("클라이언트가 미수신한 Event 목록이 존재할 경우 전송 에러", e);
                    }
                }
            });
        }
     
        return sseEmitter;
    }
    
    /**
     * 특정 userId로 생성된 모든 EventStream에 Event 전송
     * 
     * @param userId
     * @return
     */
    @GetMapping("/create-event-by-user-id")
    public String createEvent(@RequestParam String userId) {
        log.info(">> received userId={}", userId);
        
        final String eventId = userId + "_" + System.currentTimeMillis();
        final String eventData = "Event Pushed. userId=" + userId;
        
        // 분산 캐시에 Event 저장
        sseEventCache.put(eventId, eventData);
        
        // 로컬 캐시에서 특정 userId에 해당하는 SseEmitter 객체를 획득
        sseEmitterCache.forEach((key, sseEmitter) -> {
            if (key.startsWith(userId)) {
                SseEventBuilder event = SseEmitter.event().name(EVENT_SSE).id(eventId).data(eventData);
                
                try {
                    sseEmitter.send(event);
                    log.debug("<< sent {} : {}", eventId, eventData);
                } catch (IOException e) {
                    // 로컬 캐시에서 연결 종료된 SseEmitter 제거
                    sseEmitterCache.remove(key);
                    log.warn("   로컬 캐시에서 연결 종료된 SseEmitter 제거 {}", key);
                } catch (IllegalStateException e) {
                    // 로컬 캐시에서 기간 만료된 SseEmitter 제거
                    sseEmitterCache.remove(key);
                    log.warn("   로컬 캐시에서 기간 만료된 SseEmitter 제거 {}", key);
                }
            }
        });
        
        return "OK";
    }
    
}

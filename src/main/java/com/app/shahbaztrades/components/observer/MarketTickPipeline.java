package com.app.shahbaztrades.components.observer;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTickPipeline {

    private static final int RING_BUFFER_SIZE = 16384;
    private static final int SHARD_COUNT = 4;
    private final TradeWatchdog tradeWatchdog;
    private Disruptor<TickEvent> disruptor;
    private volatile RingBuffer<TickEvent> ringBuffer;

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void start() {
        disruptor = new Disruptor<>(
                TickEvent::new,
                RING_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new BlockingWaitStrategy());

        EventHandler<TickEvent>[] handlers = new EventHandler[SHARD_COUNT];
        for (int i = 0; i < SHARD_COUNT; i++) {
            final int shard = i;
            handlers[i] = (event, _, _) -> {
                String token = event.getToken();
                if (token != null && Math.floorMod(token.hashCode(), SHARD_COUNT) == shard) {
                    try {
                        tradeWatchdog.onTick(token, event.getLtp());
                    } catch (Exception e) {
                        log.error("Tick processing failed for token {}", token, e);
                    }
                }
            };
        }

        disruptor.handleEventsWith(handlers);
        ringBuffer = disruptor.start();
        log.info("Market tick disruptor started: {} shards, ring size {}", SHARD_COUNT, RING_BUFFER_SIZE);
    }

    public void publish(String token, double ltp) {
        RingBuffer<TickEvent> rb = this.ringBuffer;
        if (rb == null) {
            return;
        }

        rb.publishEvent((event, _, t, l) -> {
            event.setToken(t);
            event.setLtp(l);
        }, token, ltp);
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
            log.info("Market tick disruptor shut down");
        }
    }
}

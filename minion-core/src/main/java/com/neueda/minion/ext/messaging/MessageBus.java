package com.neueda.minion.ext.messaging;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.neueda.minion.ext.result.ExtensionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class MessageBus {

    private final Logger logger = LoggerFactory.getLogger(MessageBus.class);
    private final Multimap<String, Consumer<Map<String, Object>>> subscriptions;
    private final BlockingQueue<ExtensionResult> results;

    public MessageBus() {
        subscriptions = HashMultimap.create();
        results = new LinkedBlockingQueue<>();
    }

    public void subscribe(String event,
                          final Function<Map<String, Object>, ExtensionResult> processor) {
        subscribe(event, processor, new DefaultMessageBusReader());
    }

    public <T> void subscribe(String event,
                              final Function<? super T, ExtensionResult> processor,
                              final MessageBusReader<T> reader) {
        subscriptions.put(event, data -> {
            try {
                T object = reader.read(data);
                ExtensionResult result = processor.apply(object);
                results.add(result);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to read event \"{}\" message: {}", event, data, e);
            }
        });
    }

    public void publish(String event, final Map<String, Object> data) {
        subscriptions.get(event).parallelStream()
                .forEach(consumer -> consumer.accept(data));
    }

    public Collection<ExtensionResult> getResults() {
        List<ExtensionResult> list = new ArrayList<>();
        results.drainTo(list);
        return list;
    }

}
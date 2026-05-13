package com.onionmcc.client.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight annotation-based event bus for dispatching events to registered
 * listeners.
 */
public class EventBus {

    private final Map<Class<? extends Event>, List<EventSubscriber>> subscribers = new ConcurrentHashMap<>();

    /**
     * Register all @EventHandler methods in the given listener object.
     */
    public void register(Object listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class))
                continue;
            if (method.getParameterCount() != 1)
                continue;

            Class<?> paramType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(paramType))
                continue;

            method.setAccessible(true);

            EventHandler annotation = method.getAnnotation(EventHandler.class);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) paramType;

            subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                    .add(new EventSubscriber(listener, method, annotation.priority()));

            // Sort by priority
            subscribers.get(eventType).sort(Comparator.comparingInt(s -> s.priority.getValue()));
        }
    }

    /**
     * Unregister all event handlers from the given listener.
     */
    public void unregister(Object listener) {
        subscribers.values().forEach(list -> list.removeIf(sub -> sub.listener == listener));
    }

    /**
     * Post an event to all registered handlers.
     */
    public <T extends Event> T post(T event) {
        List<EventSubscriber> subs = subscribers.get(event.getClass());
        if (subs == null)
            return event;

        for (EventSubscriber sub : subs) {
            try {
                sub.method.invoke(sub.listener, event);
            } catch (Exception e) {
                System.err.println("[OnionMCC] Error dispatching event " + event.getClass().getSimpleName()
                        + " to " + sub.listener.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return event;
    }

    private static class EventSubscriber {
        final Object listener;
        final Method method;
        final Event.Priority priority;

        EventSubscriber(Object listener, Method method, Event.Priority priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }
    }
}

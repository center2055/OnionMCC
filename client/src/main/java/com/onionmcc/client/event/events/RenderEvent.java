package com.onionmcc.client.event.events;

import com.onionmcc.client.event.Event;

/**
 * Fired on render frames. Contains partial tick for smooth interpolation.
 */
public class RenderEvent extends Event {
    private final float partialTicks;

    public RenderEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}

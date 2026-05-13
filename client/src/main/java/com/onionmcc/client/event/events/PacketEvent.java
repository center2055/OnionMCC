package com.onionmcc.client.event.events;

import com.onionmcc.client.event.Event;

/**
 * Fired when a network packet is sent or received. Can be cancelled to block
 * packets.
 */
public class PacketEvent extends Event {

    public enum Direction {
        INCOMING,
        OUTGOING
    }

    private final Object packet;
    private final Direction direction;

    public PacketEvent(Object packet, Direction direction) {
        this.packet = packet;
        this.direction = direction;
    }

    public Object getPacket() {
        return packet;
    }

    public Direction getDirection() {
        return direction;
    }
}

package com.prabhix.platform.common.event;

/**
 * Marker for cross-module domain events.
 *
 * <p>Events in {@code common.event} are the only types two feature modules may share. A
 * module that needs something from another module publishes one of these rather than
 * importing the other module's services, which is what keeps the monolith modular enough to
 * split later.
 */
public interface PlatformEvent {
}

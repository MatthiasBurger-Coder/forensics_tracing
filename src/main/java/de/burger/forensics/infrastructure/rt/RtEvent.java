package de.burger.forensics.infrastructure.rt;

public enum RtEvent {
    METHOD_ENTER,
    METHOD_EXIT,
    BRANCH_TAKEN,
    VAR_SET,
    THREAD_FORK,
    THREAD_JOIN,
    EXCEPTION_THROWN,
    LOCK_ACQUIRE,
    LOCK_RELEASE,
    TIMER_START,
    TIMER_END,
    IO_BEGIN,
    IO_END,
    CUSTOM
}

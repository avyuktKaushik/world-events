package dev.arc2.worldevents.event;

public enum StopReason {
    /** Timer ran out. Rewards are handed out. */
    EXPIRED,
    /** The event finished on its own (bounty claimed, all questions answered...). Rewards are handed out. */
    COMPLETED,
    /** An admin stopped it. */
    ADMIN,
    /** Server shut down — non-resumable events clean up. */
    SHUTDOWN,
    /** Found in data.yml on startup but expired / not resumable — clean up only. */
    RESTART
}

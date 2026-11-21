package com.slideindex.app.shizuku;

// Queried immediately before injecting DOWN; revocation never requires a UI-thread round trip.
interface ITouchInterruptionGuard {
    boolean isCurrent() = 0;
}

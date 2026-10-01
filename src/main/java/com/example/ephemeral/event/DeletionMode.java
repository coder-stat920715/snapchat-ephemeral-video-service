package com.example.ephemeral.event;

public enum DeletionMode {
    /** Keep the object, only record that playback finished (STORE_24HR). */
    SOFT,
    /** Remove the object from S3 and mark the row DELETED (VIEW_ONCE_5MIN). */
    HARD
}

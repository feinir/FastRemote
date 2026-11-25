// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ai;

/** Fast, non-blocking callbacks for one assistant request. Callbacks run on the AI worker thread. */
public interface AiStreamListener {
    AiStreamListener NONE = new AiStreamListener() { };

    /** Segment boundaries preserve chronological assistant messages across model/tool steps. */
    default void onSegmentStarted(String id) { }
    default void onToolStarted(String id, String name) { }
    default void onToolProgress(String id, String message) { }
    default void onToolFinished(String id, AiToolExecution execution) { onToolExecution(execution); }
    default void onSteeringAccepted(AiSteeringInput input) { }
    default void onReasoningDelta(String delta) { }
    default void onTextDelta(String delta) { }
    default void onToolExecution(AiToolExecution execution) { }
}

package com.android.server.wm.traces.common.tags

import com.android.server.wm.traces.common.ITraceEntry
import com.android.server.wm.traces.common.prettyTimestamp

/**
 * Holds the list of tags corresponding to a particular state at a particular time in trace.
 *
 * The timestamp constructor must be a string due to lack of Kotlin/KotlinJS Long compatibility
 *
 * @param _timestamp Timestamp of the state
 * @param tags Array of tags contained in the state
 * @param isFallback False indicate if the tag timestamp was found or true if a default tag is made
 */
class TagState(
    _timestamp: String,
    val tags: Array<Tag>,
    val isFallback: Boolean = false
) : ITraceEntry {
    override val timestamp: Long = _timestamp.toLong()
    override fun toString(): String = "FlickerTagState(timestamp=${prettyTimestamp(timestamp)}, " +
            "tags=$tags)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagState) return false
        if (timestamp != other.timestamp) return false
        if (!tags.contentEquals(other.tags)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + tags.contentDeepHashCode()
        return result
    }
}

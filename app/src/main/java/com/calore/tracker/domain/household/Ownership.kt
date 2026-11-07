package com.calore.tracker.domain.household

import com.calore.tracker.domain.Visibility

/** What a person may do with a box or pantry item that has an owner and a label (M11.6). */
object Ownership {

    /** "Mine" for the purposes of the household: the phone's user, or someone they cook for. */
    private fun isMine(owner: String?, me: String, managed: Set<String>): Boolean = owner == me || owner in managed

    /**
     * True when the item can simply be taken. An item with someone else's "Mine, don't touch"
     * label starts a "Can I have it?" request instead.
     */
    fun canTake(owner: String?, visibility: Visibility, me: String, managed: Set<String> = emptySet()): Boolean =
        owner == null || visibility != Visibility.MINE || isMine(owner, me, managed)

    /** Only the owner changes the label; an item without an owner belongs to the whole household. */
    fun canChangeLabel(owner: String?, me: String, managed: Set<String> = emptySet()): Boolean =
        owner == null || isMine(owner, me, managed)

    /** Up-for-grabs boxes that belong to someone else show on Today (M11.6). */
    fun showsAsUpForGrabs(owner: String?, visibility: Visibility, me: String, managed: Set<String> = emptySet()): Boolean =
        visibility == Visibility.UP_FOR_GRABS && !isMine(owner, me, managed)
}

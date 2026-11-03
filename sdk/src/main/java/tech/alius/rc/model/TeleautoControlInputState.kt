package tech.alius.rc.model

/** Physical and screen sources must not release each other's held buttons. */
class TeleautoControlInputState {
    private val physical = mutableSetOf<TeleautoPhysicalKey>()
    private val touch = mutableSetOf<TeleautoPhysicalKey>()
    @Synchronized fun physicalIsDown(key: TeleautoPhysicalKey): Boolean = key in physical
    @Synchronized fun physicalEvent(key: TeleautoPhysicalKey, down: Boolean): Boolean {
        if (down) physical.add(key) else physical.remove(key)
        return key in physical || key in touch
    }
    @Synchronized fun touchEvent(key: TeleautoPhysicalKey, down: Boolean): Boolean {
        if (down) touch.add(key) else touch.remove(key)
        return key in physical || key in touch
    }
    @Synchronized fun physicalSnapshot(keys: Set<TeleautoPhysicalKey>): Set<TeleautoPhysicalKey> {
        physical.clear(); physical.addAll(keys); return physical + touch
    }
    @Synchronized fun touchKeys(): Set<TeleautoPhysicalKey> = touch.toSet()
    @Synchronized fun held(): Set<TeleautoPhysicalKey> = physical + touch
    @Synchronized fun clear() { physical.clear(); touch.clear() }
}

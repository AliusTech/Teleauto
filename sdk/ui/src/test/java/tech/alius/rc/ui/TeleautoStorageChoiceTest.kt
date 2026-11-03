package tech.alius.rc.ui

import org.junit.Assert.*
import org.junit.Test

class TeleautoStorageChoiceTest {
    @Test fun noCardNeverOffersAnAlternativeEvenAfterRemoval() {
        for (selected in listOf(false, true)) assertTrue(TeleautoStorageChoice.alternatives(selected, false, true, false).isEmpty())
    }
    @Test fun menuContainsOnlyTheOtherStorageIdentity() {
        assertEquals(listOf(TeleautoStorageChoice(true, "存储卡", true)), TeleautoStorageChoice.alternatives(false, true, true, true))
        assertEquals(listOf(TeleautoStorageChoice(false, "内部存储", true)), TeleautoStorageChoice.alternatives(true, true, true, true))
    }
    @Test fun presentButUnwritableStorageIsVisibleAndDisabled() {
        val card = TeleautoStorageChoice.alternatives(false, true, true, false).single()
        assertTrue(card.external); assertFalse(card.enabled); assertTrue(card.label.contains("不可用"))
        val internal = TeleautoStorageChoice.alternatives(true, true, false, true).single()
        assertFalse(internal.external); assertFalse(internal.enabled)
    }
}

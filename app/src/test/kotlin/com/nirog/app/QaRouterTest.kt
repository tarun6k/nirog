package com.nirog.app

import kotlin.test.Test
import kotlin.test.assertEquals

class QaRouterTest {

    @Test
    fun `harvest questions route to harvest`() {
        assertEquals(QaTopic.HARVEST, routeQuestion("कटाई कब कर सकते हैं"))
        assertEquals(QaTopic.HARVEST, routeQuestion("गेहूं कब काट सकते हैं"))
    }

    @Test
    fun `spend questions route to diary`() {
        assertEquals(QaTopic.SPEND, routeQuestion("इस साल कितना खर्च हुआ"))
        assertEquals(QaTopic.SPEND, routeQuestion("दवा पर कितने पैसे लगे"))
    }

    @Test
    fun `neighbourhood questions route to radar`() {
        assertEquals(QaTopic.NEARBY, routeQuestion("आस-पास रतुआ फैला है क्या"))
        assertEquals(QaTopic.NEARBY, routeQuestion("गांव में कोई रोग है"))
    }

    @Test
    fun `symptom or unknown questions route to scan - never a guessed answer`() {
        assertEquals(QaTopic.SCAN, routeQuestion("गेहूं के पत्तों पर पीली धारियां हैं"))
        assertEquals(QaTopic.SCAN, routeQuestion("कौन सी दवा डालूं"))
        assertEquals(QaTopic.SCAN, routeQuestion(""))
    }
}

package com.weenas.castbay.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogTest {
    @Test
    fun mergesTwoLogsInTimeOrder() {
        val app = listOf(
            "10-05 13:31:50.100  3514  3514 I AirPlayManager: started",
            "10-05 13:31:57.000  3514  3600 W Player: first",
            "10-05 13:31:57.000  3514  3600 W Player: second, same time",
        )
        val protocol = listOf(
            "10-05 13:31:55.500  3514  5635 I UxPlay: Accepted client",
            "10-05 13:31:57.900  3514  5635 I UxPlay: Connection closed",
        )
        assertEquals(
            listOf(
                "10-05 13:31:50.100  3514  3514 I AirPlayManager: started",
                "10-05 13:31:55.500  3514  5635 I UxPlay: Accepted client",
                "10-05 13:31:57.000  3514  3600 W Player: first",
                "10-05 13:31:57.000  3514  3600 W Player: second, same time",
                "10-05 13:31:57.900  3514  5635 I UxPlay: Connection closed",
            ),
            Log.mergeByTime(app, protocol, 10)
        )
        assertEquals(2, Log.mergeByTime(app, protocol, 2).size)
    }
}

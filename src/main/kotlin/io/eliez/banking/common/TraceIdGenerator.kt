package io.eliez.banking.common

import kotlin.random.Random

object TraceIdGenerator {
    fun newRequestId(): String = "%016x".format(Random.nextLong())
}

package io.eliez.banking.common

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

fun ObjectMapper.serializeAsString(kclass: KClass<out Any>) {
    configOverride(kclass.java).format =
        JsonFormat.Value.forShape(JsonFormat.Shape.STRING)
}

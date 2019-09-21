package io.eliez.banking.web

fun <T, R> List<T>.mapWithNext(transform: (T, T) -> R): List<R> =
    this.mapIndexed { index, item -> transform(item, this[(index + 1) % size]) }

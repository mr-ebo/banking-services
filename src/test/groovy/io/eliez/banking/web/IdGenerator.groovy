package io.eliez.banking.web

@Singleton
class IdGenerator {
    final Random random = new Random()

    String generate() {
        return String.format("%016x", random.nextLong())
    }
}

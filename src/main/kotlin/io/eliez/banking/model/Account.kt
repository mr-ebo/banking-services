package io.eliez.banking.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import java.math.BigDecimal

object Accounts : IntIdTable() {
    val iban = varchar("iban", 34).uniqueIndex()
    val balance = decimal("balance", 20, 2)
}

class Account(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Account>(Accounts)

    var iban by Accounts.iban
    var balance by Accounts.balance
}

data class NewAccount(val iban: String, val balance: BigDecimal) {
    override fun toString(): String {
        return "[iban=$iban, balance=$balance]"
    }
}

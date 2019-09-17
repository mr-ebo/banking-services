package io.eliez.banking.model

import org.jetbrains.exposed.dao.LongIdTable
import java.math.BigDecimal

object Transfers : LongIdTable() {
    val fromAccount = reference("from_account", Accounts)
    val toAccount = reference("to_account", Accounts)
    val amount = decimal("amount", 20, 2)
}

data class NewTransfer(val fromAccount: String, val toAccount: String, val amount: BigDecimal)

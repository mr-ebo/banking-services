package io.eliez.banking.service

import io.eliez.banking.model.*
import io.eliez.banking.service.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

class BankService {

    suspend fun getAccount(iban: String): NewAccount? = dbQuery {
        Account.find { Accounts.iban eq iban }
            .singleOrNull()
            ?.run {
                NewAccount(iban, balance)
            }
    }

    suspend fun createAccount(account: NewAccount): Unit = dbQuery {
        Accounts.insert {
            it[iban] = account.iban
            it[balance] = account.balance
        }
    }

    suspend fun createTransfer(transfer: NewTransfer): Unit = dbQuery {
        require(transfer.fromAccount != transfer.toAccount) { "Source and destination accounts are the same" }
        require(transfer.amount > BigDecimal.ZERO) { "Invalid amount" }

        val fromAcc: Account? = findAccountForUpdate(transfer.fromAccount)
        val toAcc: Account? = findAccountForUpdate(transfer.toAccount)
        requireNotNull(fromAcc) { "Unknown source account" }
        requireNotNull(toAcc) { "Unknown destination account" }
        check(fromAcc.balance >= transfer.amount) { "Insufficient funds" }

        Transfers.insert {
            it[fromAccount] = fromAcc.id
            it[toAccount] = toAcc.id
            it[amount] = transfer.amount
        }
        Accounts.update({ Accounts.id eq fromAcc.id }) {
            it[balance] = fromAcc.balance - transfer.amount
        }
        Accounts.update({ Accounts.id eq toAcc.id }) {
            it[balance] = toAcc.balance + transfer.amount
        }
    }

    private fun findAccountForUpdate(iban: String) =
        Account.find { Accounts.iban eq iban }
            .forUpdate()
            .singleOrNull()
}

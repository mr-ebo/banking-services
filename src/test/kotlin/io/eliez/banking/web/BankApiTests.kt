package io.eliez.banking.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.eliez.banking.common.serializeAsString
import io.eliez.banking.model.NewAccount
import io.eliez.banking.model.NewTransfer
import io.eliez.banking.module
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.any
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.net.HttpURLConnection.*
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BankApiTests {

    companion object {

        // TODO: should be random to avoid clashing with any server running on local machine.
        //  See https://github.com/ktorio/ktor/issues/909
        private const val SERVER_PORT = 55555

        // IBANs generated using http://www.randomiban.com
        private val TestAccounts = listOf(
            NewAccount("BR7399674773964894418786327F5", "106.93".toBigDecimal()),
            NewAccount("IE77BOFI900017175564", "838.15".toBigDecimal()),
            NewAccount("GB17BARC20035344264673", "447.59".toBigDecimal()),
            NewAccount("ES4501828365021385996981", "338.89".toBigDecimal())
        )
        private const val UNKNOWN_IBAN = "DK3650511641344966"

        lateinit var mapper: ObjectMapper

        val client = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    mapper = this
                    serializeAsString(BigDecimal::class)
                }
            }
        }

        @BeforeAll
        @JvmStatic
        fun startServer() {
            val server = embeddedServer(Netty, SERVER_PORT, module = Application::module)
            server.start()

            RestAssured.port = SERVER_PORT
            Runtime.getRuntime().addShutdownHook(Thread {
                server.stop(0, 0, TimeUnit.SECONDS)
            })
        }

        @JvmStatic
        @Suppress("unused")
        fun testAccountsProvider(): Stream<Arguments> =
            TestAccounts
                .map { arguments(it) }
                .stream()

        @JvmStatic
        @Suppress("unused")
        fun invalidTransferProvider(): Stream<Arguments> =
            Stream.of(
                arguments("same account", TestAccounts[0].iban, TestAccounts[0].iban, BigDecimal.ONE),
                arguments("unknown source account", UNKNOWN_IBAN, TestAccounts[1].iban, BigDecimal.ONE),
                arguments("unknown destination account", TestAccounts[0].iban, UNKNOWN_IBAN, BigDecimal.ONE),
                arguments("negative amount", TestAccounts[0].iban, TestAccounts[1].iban, (-1).toBigDecimal()),
                arguments("zero amount", TestAccounts[0].iban, TestAccounts[1].iban, BigDecimal.ZERO)
            )

        @JvmStatic
        @Suppress("unused")
        fun cyclicTransfersProvider(): Stream<Arguments> {
            var previousAcc = TestAccounts[TestAccounts.size - 1]
            val amount = previousAcc.balance
            return TestAccounts.map { acc ->
                arguments(previousAcc.iban, acc.iban, amount).also {
                    previousAcc = acc
                }
            }.stream()
        }

        @JvmStatic
        @Suppress("unused")
        fun incompleteCreateAccountJsonProvider(): Stream<String> {
            val account = NewAccount(UNKNOWN_IBAN, BigDecimal.ONE)
            return incompleteJsonGenerator(account)
        }

        @JvmStatic
        @Suppress("unused")
        fun invalidTransferJsonProvider(): Stream<String> {
            val transfer = NewTransfer(TestAccounts[0].iban, TestAccounts[1].iban, BigDecimal.ONE)
            return incompleteJsonGenerator(transfer)
        }

        private fun incompleteJsonGenerator(any: Any): Stream<String> {
            val templateNode: ObjectNode = mapper.valueToTree(any)
            return Iterable { templateNode.fieldNames() }
                .map { name ->
                    val copy = templateNode.deepCopy().apply { remove(name) }
                    mapper.writeValueAsString(copy)
                }.stream()
        }
    }

    @ParameterizedTest(name = "create account {0}")
    @MethodSource("testAccountsProvider")
    @Order(1)
    fun `create account`(newAccount: NewAccount) {
        Given {
            contentType(ContentType.JSON)
            body(newAccount)
        } When {
            post("/api/v1/accounts")
        } Then {
            statusCode(HTTP_CREATED)
        }
    }

    @ParameterizedTest(name = "fail to create account due to incomplete request: {0}")
    @MethodSource("incompleteCreateAccountJsonProvider")
    @Order(2)
    fun `fail to create account due to incomplete request`(json: String) {
        Given {
            contentType(ContentType.JSON)
            body(json)
        } When {
            post("/api/v1/accounts")
        } Then {
            statusCode(HTTP_BAD_REQUEST)
            body("message", any(String::class.java))
        }
    }

    @ParameterizedTest(name = "fail to retrieve account [{0}]")
    @ValueSource(strings = ["", UNKNOWN_IBAN])
    @Order(2)
    fun `attempt to retrieve unknown or undefined account yields 404`(iban: String) {
        Given {
            this
        } When {
            get("/api/v1/accounts/$iban")
        } Then {
            statusCode(HTTP_NOT_FOUND)
        }
    }

    @ParameterizedTest(name = "fail to transfer due to {0}")
    @MethodSource("invalidTransferProvider")
    @Order(2)
    fun `fail to transfer due to invalid parameter`(
        @Suppress("UNUSED_PARAMETER") reason: String,
        iban1: String, iban2: String, amount: BigDecimal
    ) = testTransfer(iban1, iban2, amount, HTTP_BAD_REQUEST)

    @ParameterizedTest(name = "fail to transfer due to incomplete request: {0}")
    @MethodSource("incompleteTransferJsonProvider")
    @Order(2)
    fun `fail to transfer due to incomplete request`(json: String) =
        testTransfer(json, HTTP_BAD_REQUEST)

    @Test
    @Order(2)
    fun `fail to transfer 200 from account due to insufficient funds`() {
        val acc1 = TestAccounts[0]
        val acc2 = TestAccounts[1]
        val amount = 200.toBigDecimal()
        testTransfer(acc1.iban, acc2.iban, amount, HTTP_CONFLICT)
        checkBalance(acc1.iban, acc1.balance)
        checkBalance(acc2.iban, acc2.balance)
    }

    @ParameterizedTest(name = "transfer {2} from {0} to {1}")
    @MethodSource("cyclicTransfersProvider")
    @Order(3)
    fun `transfer from account with sufficient funds`(iban1: String, iban2: String, amount: BigDecimal) =
        runBlocking {
            val oldBalance1 = getBalance(iban1)
            val oldBalance2 = getBalance(iban2)
            testTransfer(iban1, iban2, amount, HTTP_CREATED)
            checkBalance(iban1, oldBalance1 - amount)
            checkBalance(iban2, oldBalance2 + amount)
        }

    @Test
    @Order(4)
    fun `transfers are ACID`() = runBlocking {
        val ibans = TestAccounts.map(NewAccount::iban)
        val numIterations = 1_000
        val maxAmountInCents = 100
        val requests: MutableList<Deferred<Unit>> = ArrayList(numIterations * ibans.size)
        // pre-computed to simplify and reduce latency on submitting parallel requests
        val nextIban = ibans.indices
            .map { idx -> ibans[(idx + 1) % ibans.size] }
        coroutineScope {
            for (iter in 1..numIterations) {
                val amountInCents = (iter % maxAmountInCents) + 1
                val amount = amountInCents.toBigDecimal().scaleByPowerOfTen(-2)
                ibans.forEachIndexed { index, iban ->
                    requests.add(
                        async {
                            makeTransfer(iban, nextIban[index], amount)
                        }
                    )
                }
            }
            requests.forEach { req ->
                req.await()
            }
        }
        // since the transfers are cyclic, the accounts should have the same balance at the end of the iterations
        TestAccounts.forEach { acc ->
            checkBalance(acc.iban, acc.balance)
        }
    }

    private suspend fun getBalance(iban: String) =
        client.get<NewAccount>("http://localhost:$SERVER_PORT/api/v1/accounts/$iban").balance

    private suspend inline fun makeTransfer(iban1: String, iban2: String, amount: BigDecimal) =
        client.post<Unit>("http://localhost:$SERVER_PORT/api/v1/transfers") {
            contentType(io.ktor.http.ContentType.Application.Json)
            body = NewTransfer(iban1, iban2, amount)
        }

    private fun checkBalance(iban: String, expectedBalance: BigDecimal) {
        Given {
            accept(ContentType.JSON)
        } When {
            get("/api/v1/accounts/$iban")
        } Then {
            statusCode(HTTP_OK)
            body("balance", equalTo(expectedBalance.toString()))
        }
    }

    private fun testTransfer(iban1: String, iban2: String, amount: BigDecimal, expectedStatusCode: Int) =
        testTransfer(NewTransfer(iban1, iban2, amount), expectedStatusCode)

    private fun testTransfer(requestBody: Any, expectedStatusCode: Int) {
        Given {
            contentType(ContentType.JSON)
            body(requestBody)
        } When {
            post("/api/v1/transfers")
        } Then {
            statusCode(expectedStatusCode)
            if (expectedStatusCode !in (200 until 300)) {
                body("message", any(String::class.java))
            }
        }
    }
}

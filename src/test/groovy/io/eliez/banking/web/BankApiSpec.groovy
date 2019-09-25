package io.eliez.banking.web

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import groovyx.net.http.AsyncHTTPBuilder
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import io.eliez.banking.model.NewAccount
import io.eliez.banking.model.NewTransfer
import io.ktor.server.netty.NettyApplicationEngine
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import static groovyx.net.http.ContentType.JSON
import static io.ktor.server.engine.CommandLineKt.commandLineEnvironment

class BankApiSpec extends Specification {

    // TODO: should be random to avoid clashing with any server running on local machine.
    //  See https://github.com/ktorio/ktor/issues/909
    static final int SERVER_PORT = 55555

    static final List<NewAccount> KNOWN_ACCOUNTS = [
        new NewAccount("BR7399674773964894418786327F5", "106.93".toBigDecimal()),
        new NewAccount("IE77BOFI900017175564", "838.15".toBigDecimal()),
        new NewAccount("GB17BARC20035344264673", "447.59".toBigDecimal()),
        new NewAccount("ES4501828365021385996981", "338.89".toBigDecimal())
    ]
    static final List<String> KNOWN_IBANS = KNOWN_ACCOUNTS.collect { it.iban }
    static final List<String> NEXT_KNOWN_IBANS = KNOWN_IBANS.indices
        .collect { KNOWN_IBANS[(it + 1) % KNOWN_IBANS.size()] }
    static final String UNKNOWN_IBAN = "DK3650511641344966"

    @Shared
    def mapper = new ObjectMapper().tap {
        configOverride(BigDecimal).format = JsonFormat.Value.forShape(JsonFormat.Shape.STRING)
    }

    def client = new RESTClient("http://localhost:$SERVER_PORT")

    def setupSpec() {
        serverStart(SERVER_PORT)
    }

    }

    @Unroll
    def 'create account #newAccount'() {
        when:
            def response = client.post(
                path: '/api/v1/accounts',
                body: newAccount,
                requestContentType: JSON
            ) as HttpResponseDecorator
        then:
            response.status == 201
        where:
            newAccount << KNOWN_ACCOUNTS
    }

    @Unroll
    def 'retrieve created account #iban'() {
        expect:
            getBalance(iban) == newAccount.balance
        where:
            iban << KNOWN_IBANS
            newAccount << KNOWN_ACCOUNTS
    }

    @Unroll
    def 'fail to create account due to incomplete request: #json'() {
        when:
            client.post(
                path: '/api/v1/accounts',
                body: json,
                requestContentType: JSON
            )
        then:
            def e = thrown(HttpResponseException)
            e.response.status == 400
            !e.response.data.message.empty
        where:
            json << incompleteJsonGenerator(new NewAccount(UNKNOWN_IBAN, BigDecimal.ONE))
    }

    @Unroll
    def 'fail to retrieve account "#iban"'() {
        when:
            client.get(
                path: "/api/v1/accounts/$iban",
                contentType: JSON
            )
        then:
            def e = thrown(HttpResponseException)
            e.response.status == 404
        where:
            iban << ['', UNKNOWN_IBAN]
    }

    @Unroll
    def 'fail to transfer due to #reason'() {
        given:
            def newTransfer = new NewTransfer(iban1, iban2, amount)
        when:
            client.post(
                path: '/api/v1/transfers',
                body: newTransfer,
                requestContentType: JSON
            )
        then:
            def e = thrown(HttpResponseException)
            e.response.status == 400
            !e.response.data.message.empty
        where:
            reason                        | iban1                  | iban2                  | amount
            'same account'                | KNOWN_ACCOUNTS[0].iban | KNOWN_ACCOUNTS[0].iban | BigDecimal.ONE
            'unknown source account'      | UNKNOWN_IBAN           | KNOWN_ACCOUNTS[1].iban | BigDecimal.ONE
            'unknown destination account' | KNOWN_ACCOUNTS[0].iban | UNKNOWN_IBAN           | BigDecimal.ONE
            'negative amount'             | KNOWN_ACCOUNTS[0].iban | KNOWN_ACCOUNTS[1].iban | (-1) as BigDecimal
            'zero amount'                 | KNOWN_ACCOUNTS[0].iban | KNOWN_ACCOUNTS[1].iban | BigDecimal.ZERO
    }

    @Unroll
    def 'fail to transfer due to incomplete request: #json'() {
        when:
            client.post(
                path: '/api/v1/transfers',
                body: json,
                requestContentType: JSON
            )
        then:
            def e = thrown(HttpResponseException)
            e.response.status == 400
            !e.response.data.message.empty
        where:
            json << incompleteJsonGenerator(new NewTransfer(KNOWN_ACCOUNTS[0].iban, KNOWN_ACCOUNTS[1].iban, BigDecimal.ONE))
    }

    def 'fail to transfer 200 from account due to insufficient funds'() {
        given:
            def acc1 = KNOWN_ACCOUNTS[0]
            def acc2 = KNOWN_ACCOUNTS[1]
            def amount = 200 as BigDecimal
            def transfer = new NewTransfer(acc1.iban, acc2.iban, amount)
        when:
            client.post(
                path: '/api/v1/transfers',
                body: transfer,
                requestContentType: JSON
            )
        then:
            def e = thrown(HttpResponseException)
            e.response.status == 409
            !e.response.data.message.empty
        and:
            getBalance(acc1.iban) == acc1.balance
            getBalance(acc2.iban) == acc2.balance
    }

    @Unroll
    def 'transfer #amount from account with sufficient funds: #iban1 -> #iban2'() {
        given:
            def transfer = new NewTransfer(iban1, iban2, amount)
        when:
            def response = client.post(
                path: '/api/v1/transfers',
                body: transfer,
                requestContentType: JSON
            ) as HttpResponseDecorator
        then:
            response.status == 201
        and:
            getBalance(iban1) == old(getBalance(iban1)) - amount
            getBalance(iban2) == old(getBalance(iban2)) + amount
        where:
            iban1 << KNOWN_IBANS
            iban2 << NEXT_KNOWN_IBANS
            amount = KNOWN_ACCOUNTS[0].balance
    }

    def 'transfers are ACID'() {
        given:
            def numIterations = 1_000
            def maxAmountInCents = 100
            def amounts = (0..<numIterations).collect { iter ->
                def amountInCents = (iter % maxAmountInCents) + 1
                amountInCents.toBigDecimal().scaleByPowerOfTen(-2)
            }
            def asyncClient = new AsyncHTTPBuilder(
                poolSize: Runtime.getRuntime().availableProcessors(),
                uri: client.uri
            )
        when:
            List<Future> futures = GroovyCollections.combinations(KNOWN_IBANS.indices, amounts)
                .collect { int index, BigDecimal amount ->
                    def iban1 = KNOWN_IBANS[index]
                    def iban2 = NEXT_KNOWN_IBANS[index]
                    asyncClient.post(
                        path: '/api/v1/transfers',
                        body: [fromAccount: iban1, toAccount: iban2, amount: amount],
                        requestContentType: JSON
                    ) as Future
                }
        then:
            futures.forEach { it.get() }
        and:
            // since the transfers are cyclic, the accounts should have the same balance at the end of the iterations
            KNOWN_ACCOUNTS.forEach { acc ->
                assert getBalance(acc.iban) == acc.balance
            }
    }

    static void serverStart(int port) {
        def applicationEnvironment = commandLineEnvironment(["-port=$port"] as String[])
        new NettyApplicationEngine(applicationEnvironment, {}).with {
            start(false)
            addShutdownHook {
                stop(0, 0, TimeUnit.SECONDS)
            }
        }
    }

    def incompleteJsonGenerator(Object obj) {
        ObjectNode templateNode = mapper.valueToTree(obj)
        return templateNode.fieldNames().collect { name ->
            def copy = templateNode.deepCopy()
            copy.remove(name)
            mapper.writeValueAsString(copy)
        }
    }

    def getBalance(String iban) {
        def response = client.get(
            path: "/api/v1/accounts/$iban",
            contentType: JSON
        ) as HttpResponseDecorator
        assert response.status == 200
        assert response.data.iban == iban
        return response.data.balance as BigDecimal
    }
}

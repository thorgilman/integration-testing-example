package com.example.integrationtest

import com.example.server.MainController
import com.example.server.NodeRPCConnection
import com.example.state.IOUState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import org.junit.Test
import sun.applet.Main

class WebserverIntegrationTests {

    private val partyAIdentity = CordaX500Name("PartyA", "London", "GB")
    private val partyBIdentity = CordaX500Name("PartyB", "New York", "US")
    private val partyCIdentity = CordaX500Name("PartyC", "Paris", "FR")

    @Test
    fun `whoami() get mapping works properly`() = withDriver {
        val (partyA) = startNodes(partyAIdentity)
        val (partyARpcConnection) = getRpcConnections(partyA)
        val (partyAController) = getControllers(partyARpcConnection)

        val myX500Name = partyAController.whoami().getValue("me").toString()
        assert(myX500Name == partyAIdentity.toString())
    }

    @Test
    fun `getPeers() post mapping works properly`() = withDriver {
        val (partyA, partyB, partyC) = startNodes(partyAIdentity, partyBIdentity, partyCIdentity)
        val (partyARpcConnection, partyBRpcConnection, partyCRpcConnection) = getRpcConnections(partyA, partyB, partyC)
        val (partyAController, partyBController, partyCController) = getControllers(partyARpcConnection, partyBRpcConnection, partyCRpcConnection)

        val peersList = partyAController.getPeers().getValue("peers")
        assert(peersList == listOf(partyBIdentity, partyCIdentity))
    }

    @Test
    fun `createIOU() post mapping works properly`() = withDriver {

        val (partyA, partyB, partyC) = startNodes(partyAIdentity, partyBIdentity, partyCIdentity)
        val (partyARpcConnection, partyBRpcConnection, partyCRpcConnection) = getRpcConnections(partyA, partyB, partyC)
        val (partyAController, partyBController, partyCController) = getControllers(partyARpcConnection, partyBRpcConnection, partyCRpcConnection)

        partyAController.createIOU(10, partyBIdentity.toString())

        val iouStates = partyBRpcConnection.proxy.vaultQueryBy<IOUState>().states
        assert(iouStates.size == 1)
    }





    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(DriverParameters(
            isDebug = true,
            startNodesInProcess = true,
            cordappsForAllNodes = listOf(TestCordapp.findCordapp("com.example.contract"), TestCordapp.findCordapp("com.example.state"), TestCordapp.findCordapp("com.example.flow"))
    )) { test() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: CordaX500Name): List<NodeHandle> {
        return identities.map {
            val user = User(it.organisation, "password", setOf("ALL")) // give node a generic user
            startNode(providedName = it, rpcUsers = listOf(user))
        }.map { it.getOrThrow() }
    }

    // Returns a list of initialized RPC Connections
    private fun getRpcConnections(vararg nodes: NodeHandle): List<NodeRPCConnection> {
        val rpcConnectionList = nodes.map {
            val user = it.rpcUsers.single()
            NodeRPCConnection(host = it.rpcAddress.host, username = user.username, password = user.password, rpcPort = it.rpcAddress.port)
        }
        rpcConnectionList.forEach { it.initialiseNodeRPCConnection() }
        return rpcConnectionList
    }

    private fun getControllers(vararg rpcConnections: NodeRPCConnection): List<MainController> = rpcConnections.map { MainController(it) }


}
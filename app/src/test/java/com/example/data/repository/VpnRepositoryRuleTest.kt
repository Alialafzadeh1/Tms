package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VpnRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addConfig_authenticatedUser_createsDocumentAndReturnsId() {
        runBlocking {
            signInTestUser(ALICE_EMAIL)
            val repository = VpnRepository(firestore)

            val uniqueServer = "server-${UUID.randomUUID()}.tmsvpn.net"
            val config = VpnConfig(
                name = "Frankfurt VLESS 1",
                protocol = "vless",
                server = uniqueServer,
                port = 443,
                uuidOrPassword = "e9f73f1c-7204-4b5b-9d4f-3760bf0344d2",
                rawConfig = "vless://e9f73f1c@$uniqueServer:443"
            )

            val configId = withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.addConfig(config)
            }
            assertTrue(configId.isNotBlank())
        }
    }

    @Test
    fun observeConfigs_authenticatedOwner_emitsCreatedConfig() {
        runBlocking {
            signInTestUser(ALICE_EMAIL)
            val repository = VpnRepository(firestore)

            val uniqueServer = "server-${UUID.randomUUID()}.tmsvpn.net"
            val config = VpnConfig(
                name = "Trojan Edge",
                protocol = "trojan",
                server = uniqueServer,
                port = 443,
                uuidOrPassword = "secret-password",
                rawConfig = "trojan://secret-password@$uniqueServer:443"
            )

            val configId = withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.addConfig(config)
            }

            val configs = withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeConfigs().first { list -> list.any { it.id == configId } }
            }
            assertTrue(configs.any { it.id == configId })
        }
    }

    @Test
    fun addServer_andObserve_returnsServer() {
        runBlocking {
            signInTestUser(ALICE_EMAIL)
            val repository = VpnRepository(firestore)

            val server = VpnServer(
                name = "Helsinki Fast Node",
                country = "Finland",
                countryCode = "FI",
                host = "95.216.12.34",
                port = 443,
                protocol = "VLESS Reality",
                status = "online",
                latencyMs = 45,
                uptimePercentage = 99.95,
                stabilityScore = 98
            )

            val serverId = withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.addServer(server)
            }
            assertTrue(serverId.isNotBlank())

            val servers = withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeServers().first { list -> list.any { it.id == serverId } }
            }
            assertTrue(servers.any { it.id == serverId })
        }
    }

    @Test
    fun observeConfigs_unauthenticatedUser_failsWithPermissionDenied() {
        runBlocking {
            auth.signOut()
            val repository = VpnRepository(firestore)

            var caughtException: Throwable? = null
            try {
                withTimeout(FLOW_TIMEOUT_MS) {
                    repository.observeConfigs().first()
                }
            } catch (t: Throwable) {
                caughtException = t
            }

            assertNotNull("Expected exception when unauthenticated", caughtException)
            val isAuthOrSecurityError = caughtException is IllegalStateException ||
                caughtException?.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            assertTrue(isAuthOrSecurityError)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_vpn@tmsvpn.local"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 6000L
    }
}

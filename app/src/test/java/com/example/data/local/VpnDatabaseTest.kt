package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnDatabaseTest {

    private lateinit var database: VpnDatabase
    private lateinit var dao: VpnConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VpnDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.vpnConfigDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveVpnConfig() = runBlocking {
        val config = VpnConfig(
            id = "vpn-test-1",
            name = "Frankfurt VLESS Node",
            protocol = "VLESS",
            serverAddress = "de.tmsvpn.net",
            port = 443,
            configJson = """{"protocol":"vless","server":"de.tmsvpn.net","port":443,"uuid":"1234-abcd"}""",
            isFavorite = true
        )

        dao.insertConfig(config)

        val retrieved = dao.getConfigById("vpn-test-1")
        assertNotNull(retrieved)
        assertEquals("Frankfurt VLESS Node", retrieved?.name)
        assertEquals("VLESS", retrieved?.protocol)
        assertEquals("de.tmsvpn.net", retrieved?.serverAddress)
        assertEquals(443, retrieved?.port)
        assertTrue(retrieved?.isFavorite == true)
        assertTrue(retrieved?.configJson?.contains("vless") == true)
    }

    @Test
    fun filterByProtocols() = runBlocking {
        val configs = listOf(
            VpnConfig(id = "1", name = "Node 1", protocol = "VLESS", serverAddress = "1.1.1.1", port = 443, configJson = "{}"),
            VpnConfig(id = "2", name = "Node 2", protocol = "VMess", serverAddress = "2.2.2.2", port = 8443, configJson = "{}"),
            VpnConfig(id = "3", name = "Node 3", protocol = "Trojan", serverAddress = "3.3.3.3", port = 443, configJson = "{}"),
            VpnConfig(id = "4", name = "Node 4", protocol = "Shadowsocks", serverAddress = "4.4.4.4", port = 8388, configJson = "{}")
        )
        dao.insertAll(configs)

        val vlessConfigs = dao.getConfigsByProtocol("VLESS").first()
        assertEquals(1, vlessConfigs.size)
        assertEquals("Node 1", vlessConfigs[0].name)

        val trojanConfigs = dao.getConfigsByProtocol("Trojan").first()
        assertEquals(1, trojanConfigs.size)
        assertEquals("Node 3", trojanConfigs[0].name)
    }

    @Test
    fun updateFavoriteAndDelete() = runBlocking {
        val config = VpnConfig(
            id = "fav-test",
            name = "Fav Node",
            protocol = "VMess",
            serverAddress = "vmess.tms.net",
            port = 443,
            configJson = "{}",
            isFavorite = false
        )
        dao.insertConfig(config)

        dao.updateFavorite("fav-test", true)
        val updated = dao.getConfigById("fav-test")
        assertTrue(updated?.isFavorite == true)

        dao.deleteById("fav-test")
        val deleted = dao.getConfigById("fav-test")
        assertNull(deleted)
    }
}

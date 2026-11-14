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
class AppDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VpnConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.vpnConfigDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testVpnConfigEntityAndCrudOperations() = runBlocking {
        // 1. CREATE
        val config = VpnConfig(
            id = "cfg-001",
            name = "Germany VLESS Reality",
            protocol = "VLESS",
            serverAddress = "de.tmsvpn.com",
            port = 443,
            configJson = """{"protocol":"VLESS","serverAddress":"de.tmsvpn.com","port":443}""",
            isFavorite = false
        )
        dao.insertConfig(config)

        // 2. READ
        val retrieved = dao.getConfigById("cfg-001")
        assertNotNull(retrieved)
        assertEquals("cfg-001", retrieved?.id)
        assertEquals("Germany VLESS Reality", retrieved?.name)
        assertEquals("VLESS", retrieved?.protocol)
        assertEquals("de.tmsvpn.com", retrieved?.serverAddress)
        assertEquals(443, retrieved?.port)
        assertEquals(false, retrieved?.isFavorite)

        // READ ALL LIST
        val allList = dao.getAllConfigsList()
        assertEquals(1, allList.size)

        // 3. UPDATE
        val updatedConfig = config.copy(name = "Germany VLESS Super", isFavorite = true)
        dao.updateConfig(updatedConfig)

        val updatedRetrieved = dao.getConfigById("cfg-001")
        assertEquals("Germany VLESS Super", updatedRetrieved?.name)
        assertEquals(true, updatedRetrieved?.isFavorite)

        // 4. DELETE
        dao.deleteById("cfg-001")
        val deletedRetrieved = dao.getConfigById("cfg-001")
        assertNull(deletedRetrieved)
    }

    @Test
    fun testAllSupportedProtocols() = runBlocking {
        val protocols = listOf("VLESS", "VMess", "Trojan", "Shadowsocks")
        val configs = protocols.mapIndexed { index, proto ->
            VpnConfig(
                id = "proto-$index",
                name = "$proto Node",
                protocol = proto,
                serverAddress = "$proto.server.net",
                port = 443 + index,
                configJson = "{}",
                isFavorite = false
            )
        }
        dao.insertAll(configs)

        val total = dao.getAllConfigs().first()
        assertEquals(4, total.size)

        for (proto in protocols) {
            val filtered = dao.getConfigsByProtocol(proto).first()
            assertEquals(1, filtered.size)
            assertEquals(proto, filtered.first().protocol)
        }
    }
}

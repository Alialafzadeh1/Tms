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
class LocalVpnConfigDatabaseTest {

    private lateinit var database: TmsVpnRoomDatabase
    private lateinit var dao: LocalVpnConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.vpnConfigDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveVlessConfig() = runBlocking {
        val entity = LocalVpnConfigEntity(
            id = "cfg-vless-1",
            userId = "user_123",
            name = "Frankfurt VLESS Reality",
            protocol = "VLESS",
            address = "de1.tmsvpn.net",
            port = 443,
            uuidOrPassword = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            transport = "tcp",
            security = "reality",
            sni = "gateway.de1.tmsvpn.net",
            pathOrServiceName = "",
            rawConfig = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@de1.tmsvpn.net:443?security=reality&sni=gateway.de1.tmsvpn.net#Frankfurt+VLESS+Reality",
            pingMs = 45,
            status = "online",
            isFavorite = true
        )

        dao.insert(entity)

        val retrieved = dao.getById("cfg-vless-1")
        assertNotNull(retrieved)
        assertEquals("Frankfurt VLESS Reality", retrieved?.name)
        assertEquals("VLESS", retrieved?.protocol)
        assertEquals("de1.tmsvpn.net", retrieved?.address)
        assertEquals(443, retrieved?.port)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", retrieved?.uuidOrPassword)
        assertEquals("reality", retrieved?.security)
        assertTrue(retrieved?.isFavorite == true)
    }

    @Test
    fun insertMultipleProtocolsAndFilter() = runBlocking {
        val configs = listOf(
            LocalVpnConfigEntity(
                id = "vless-1",
                userId = "u1",
                name = "VLESS Node",
                protocol = "VLESS",
                address = "1.1.1.1",
                port = 443
            ),
            LocalVpnConfigEntity(
                id = "vmess-1",
                userId = "u1",
                name = "VMess Node",
                protocol = "VMESS",
                address = "2.2.2.2",
                port = 8443
            ),
            LocalVpnConfigEntity(
                id = "trojan-1",
                userId = "u1",
                name = "Trojan Node",
                protocol = "TROJAN",
                address = "3.3.3.3",
                port = 443
            ),
            LocalVpnConfigEntity(
                id = "ss-1",
                userId = "u1",
                name = "Shadowsocks Node",
                protocol = "SHADOWSOCKS",
                address = "4.4.4.4",
                port = 8388
            )
        )

        dao.insertAll(configs)

        val all = dao.observeAll().first()
        assertEquals(4, all.size)

        val vlessOnly = dao.observeByProtocol("VLESS").first()
        assertEquals(1, vlessOnly.size)
        assertEquals("VLESS Node", vlessOnly[0].name)

        val trojanOnly = dao.observeByProtocol("TROJAN").first()
        assertEquals(1, trojanOnly.size)
        assertEquals("Trojan Node", trojanOnly[0].name)
    }

    @Test
    fun updatePingStatusAndFavorite() = runBlocking {
        val entity = LocalVpnConfigEntity(
            id = "cfg-update-1",
            userId = "u1",
            name = "Test Ping Node",
            protocol = "VMESS",
            address = "5.5.5.5",
            port = 443,
            pingMs = -1,
            status = "untested",
            isFavorite = false
        )
        dao.insert(entity)

        dao.updatePingStatus("cfg-update-1", pingMs = 62, status = "online")
        val updatedPing = dao.getById("cfg-update-1")
        assertEquals(62, updatedPing?.pingMs)
        assertEquals("online", updatedPing?.status)

        dao.updateFavorite("cfg-update-1", isFavorite = true)
        val updatedFav = dao.getById("cfg-update-1")
        assertEquals(true, updatedFav?.isFavorite)

        dao.deleteById("cfg-update-1")
        val deleted = dao.getById("cfg-update-1")
        assertNull(deleted)
    }

    @Test
    fun domainModelMapping() {
        val entity = LocalVpnConfigEntity(
            id = "cfg-map-1",
            userId = "u_map",
            name = "Mapped Trojan",
            protocol = "TROJAN",
            address = "trojan.tmsvpn.net",
            port = 443,
            uuidOrPassword = "securePassword123",
            transport = "tcp",
            security = "tls",
            sni = "trojan.tmsvpn.net",
            pathOrServiceName = "",
            rawConfig = "trojan://securePassword123@trojan.tmsvpn.net:443#Mapped+Trojan",
            pingMs = 55,
            status = "online",
            isFavorite = true
        )

        val domain = entity.toDomainModel()
        assertEquals("cfg-map-1", domain.id)
        assertEquals("trojan", domain.protocol)
        assertEquals("trojan.tmsvpn.net", domain.server)
        assertEquals(443, domain.port)
        assertEquals("securePassword123", domain.uuidOrPassword)

        val entityBack = LocalVpnConfigEntity.fromDomainModel(domain, notes = "local note")
        assertEquals("TROJAN", entityBack.protocol)
        assertEquals("trojan.tmsvpn.net", entityBack.address)
        assertEquals("local note", entityBack.notes)
    }
}

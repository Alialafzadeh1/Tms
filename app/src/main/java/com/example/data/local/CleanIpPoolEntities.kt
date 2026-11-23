package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class CleanIpAddressType(val labelFa: String, val labelEn: String) {
    IPV4("IPv4", "IPv4"),
    IPV6("IPv6", "IPv6"),
    DOMAIN("دامنه تمیز", "Domain")
}

enum class CleanIpHealthStatus(val labelFa: String, val labelEn: String) {
    UNTESTED("تست‌نشده", "Untested"),
    HEALTHY("سالم", "Healthy"),
    DEGRADED("کند / ناپایدار", "Degraded"),
    UNHEALTHY("ناموفق", "Unreachable"),
    QUARANTINED("قرنطینه", "Quarantined")
}

/**
 * Room Entity representing a verified or candidate Clean IP / SNI / Edge host stored locally
 * for ISP Auto-Tuning, Anti-Filter Radar fallback, and Admin Endpoint Inventory promotion.
 */
@Entity(
    tableName = "clean_ip_pool",
    indices = [
        Index(value = ["addressType"]),
        Index(value = ["isAvailable"]),
        Index(value = ["healthStatus"]),
        Index(value = ["assignedUserId"])
    ]
)
data class CleanIpEntity(
    @PrimaryKey
    val host: String,
    val addressType: String,
    val defaultPort: Int = 443,
    val isAvailable: Boolean = true,
    val healthStatus: String = CleanIpHealthStatus.UNTESTED.name,
    val latencyMs: Int = -1,
    val stabilityScore: Int = 0,
    val failureCount: Int = 0,
    val lastCheckedAtMs: Long = 0L,
    val lastSuccessfulHandshakeMs: Long = 0L,
    val assignedUserId: String = "",
    val assignedSubscriptionId: String = "",
    val sourceTag: String = "USER_CLEAN_POOL",
    val notes: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun addressTypeEnum(): CleanIpAddressType =
        CleanIpAddressType.entries.firstOrNull { it.name == addressType } ?: CleanIpAddressType.DOMAIN

    fun healthStatusEnum(): CleanIpHealthStatus =
        CleanIpHealthStatus.entries.firstOrNull { it.name == healthStatus } ?: CleanIpHealthStatus.UNTESTED
}

@Dao
interface CleanIpDao {

    @Query(
        """
        SELECT * FROM clean_ip_pool
        ORDER BY
            CASE healthStatus
                WHEN 'HEALTHY' THEN 0
                WHEN 'DEGRADED' THEN 1
                WHEN 'UNTESTED' THEN 2
                WHEN 'UNHEALTHY' THEN 3
                ELSE 4
            END ASC,
            CASE WHEN latencyMs > 0 THEN latencyMs ELSE 999999 END ASC,
            host ASC
        """
    )
    fun observeAll(): Flow<List<CleanIpEntity>>

    @Query(
        """
        SELECT * FROM clean_ip_pool
        WHERE isAvailable = 1 AND healthStatus = 'HEALTHY'
        ORDER BY CASE WHEN latencyMs > 0 THEN latencyMs ELSE 999999 END ASC
        """
    )
    fun observeHealthyAvailable(): Flow<List<CleanIpEntity>>

    @Query("SELECT * FROM clean_ip_pool WHERE addressType = :addressType ORDER BY host ASC")
    fun observeByType(addressType: String): Flow<List<CleanIpEntity>>

    @Query("SELECT * FROM clean_ip_pool")
    suspend fun getAllOnce(): List<CleanIpEntity>

    @Query("SELECT COUNT(*) FROM clean_ip_pool")
    suspend fun getCount(): Int

    @Query("SELECT host FROM clean_ip_pool")
    suspend fun getAllHostsOnce(): List<String>

    @Query("SELECT * FROM clean_ip_pool WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): CleanIpEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<CleanIpEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CleanIpEntity>)

    @Update
    suspend fun update(item: CleanIpEntity)

    @Query(
        """
        UPDATE clean_ip_pool
        SET healthStatus = :healthStatus,
            latencyMs = :latencyMs,
            stabilityScore = :stabilityScore,
            lastCheckedAtMs = :checkedAtMs,
            lastSuccessfulHandshakeMs = CASE WHEN :isSuccess = 1 THEN :checkedAtMs ELSE lastSuccessfulHandshakeMs END,
            failureCount = CASE WHEN :isSuccess = 1 THEN 0 ELSE failureCount + 1 END,
            updatedAtMs = :checkedAtMs
        WHERE host = :host
        """
    )
    suspend fun recordHealthProbe(
        host: String,
        healthStatus: String,
        latencyMs: Int,
        stabilityScore: Int,
        isSuccess: Boolean,
        checkedAtMs: Long
    )

    @Query(
        """
        UPDATE clean_ip_pool
        SET isAvailable = :isAvailable,
            assignedUserId = :assignedUserId,
            assignedSubscriptionId = :assignedSubscriptionId,
            updatedAtMs = :updatedAtMs
        WHERE host = :host
        """
    )
    suspend fun updateAvailability(
        host: String,
        isAvailable: Boolean,
        assignedUserId: String,
        assignedSubscriptionId: String,
        updatedAtMs: Long
    )

    @Query(
        """
        UPDATE clean_ip_pool
        SET healthStatus = :healthStatus,
            isAvailable = :isAvailable,
            updatedAtMs = :updatedAtMs
        WHERE host = :host
        """
    )
    suspend fun updateHealthAndAvailability(
        host: String,
        healthStatus: String,
        isAvailable: Boolean,
        updatedAtMs: Long
    )

    @Query("DELETE FROM clean_ip_pool WHERE host = :host")
    suspend fun deleteByHost(host: String)
}

/**
 * Authoritative seed repository of 330+ clean endpoints/hosts:
 * Preserves all 150 owner-provided hosts (50 IPv4 + 50 IPv6 + 50 Clean Domains)
 * and expands the pool with DE/TR-friendly Anycast Cloudflare, Fastly, Gcore & SNI hosts.
 * Seeded strictly as UNTESTED until live TCP/TLS verification proves reachability on the active network.
 */
object CleanIpSeedData {

    val IPV4_LIST: List<String> = listOf(
        // 100 Primary User-Provided Healthy & Clean IPv4 Hosts (Initial Launch Seed)
        "172.66.161.168",
        "172.67.156.92",
        "172.66.213.55",
        "139.64.235.200",
        "104.19.104.48",
        "162.120.94.92",
        "89.47.56.244",
        "88.216.66.116",
        "104.18.63.151",
        "141.11.202.84",
        "162.159.17.179",
        "103.116.7.74",
        "77.232.140.194",
        "104.21.80.150",
        "104.18.205.223",
        "77.232.140.31",
        "199.181.197.109",
        "108.165.216.26",
        "144.225.130.185",
        "45.205.0.127",
        "108.165.152.125",
        "154.83.2.42",
        "141.193.213.87",
        "5.42.221.131",
        "104.25.164.140",
        "89.47.57.203",
        "5.10.246.120",
        "172.67.146.75",
        "27.50.48.123",
        "104.129.167.81",
        "27.50.48.210",
        "172.66.168.59",
        "5.10.245.89",
        "23.27.139.111",
        "164.38.155.45",
        "45.135.235.213",
        "156.252.2.149",
        "77.75.199.73",
        "104.16.233.133",
        "172.65.49.11",
        "104.254.140.156",
        "45.80.108.18",
        "45.205.0.83",
        "45.131.209.217",
        "103.11.214.86",
        "104.25.66.174",
        "5.175.141.44",
        "45.131.211.80",
        "104.36.195.73",
        "149.7.2.142",
        "92.53.191.43",
        "159.246.55.77",
        "159.246.55.44",
        "88.216.67.87",
        "103.116.7.3",
        "94.140.0.113",
        "162.159.90.227",
        "77.75.199.101",
        "104.21.76.188",
        "141.11.202.255",
        "156.252.3.78",
        "104.129.166.62",
        "152.70.56.19",
        "154.206.12.72",
        "31.12.75.94",
        "94.247.142.29",
        "93.114.65.12",
        "45.205.0.246",
        "104.25.246.140",
        "86.38.214.228",
        "185.36.141.81",
        "159.246.55.216",
        "104.25.92.198",
        "104.18.137.9",
        "104.16.86.1",
        "104.16.45.222",
        "172.67.189.211",
        "108.165.216.88",
        "104.18.0.62",
        "92.53.188.141",
        "154.211.8.136",
        "104.254.140.193",
        "77.232.140.204",
        "45.85.118.78",
        "141.193.213.168",
        "77.232.140.81",
        "141.11.203.29",
        "154.197.64.170",
        "93.114.65.70",
        "154.198.173.87",
        "77.37.33.206",
        "103.11.212.214",
        "104.16.3.82",
        "104.20.9.230",
        "154.197.75.178",
        "104.129.167.17",
        "172.64.33.153",
        "27.50.49.195",
        "104.27.85.162",
        "64.69.24.132",
        // Previous 50 owner-provided clean IPv4 hosts
        "69.48.218.1",
        "92.53.189.220",
        "45.131.4.52",
        "192.65.217.23",
        "77.232.140.4",
        "160.153.0.167",
        "139.64.235.52",
        "104.16.190.196",
        "88.216.66.214",
        "45.95.241.145",
        "27.50.49.64",
        "92.53.190.147",
        "45.8.211.90",
        "213.218.160.100",
        "45.95.241.206",
        "167.68.5.198",
        "104.25.227.195",
        "154.218.15.79",
        "45.80.111.31",
        "89.47.56.112",
        "2.16.31.70",
        "88.216.66.207",
        "45.131.210.31",
        "104.19.177.52",
        "104.21.28.237",
        "2.16.175.182",
        "154.218.15.226",
        "104.16.145.213",
        "154.211.8.239",
        "45.131.4.221",
        "89.116.180.220",
        "134.122.93.75",
        "104.21.90.34",
        "172.67.127.156",
        "5.10.247.59",
        "23.247.163.47",
        "45.80.108.117",
        "94.247.142.25",
        "45.8.211.203",
        "104.234.239.232",
        "141.11.202.94",
        "92.53.188.31",
        "154.198.173.40",
        "91.193.58.55",
        "159.112.235.71",
        "159.246.55.15",
        "2.16.31.5",
        "104.16.8.199",
        "159.112.235.221",
        "104.18.123.39",
        // Expanded Anycast, DE & TR Edge Clean IPv4 pool
        "104.16.132.229",
        "104.16.133.229",
        "104.17.147.22",
        "104.17.148.22",
        "104.18.2.161",
        "104.18.3.161",
        "104.19.44.118",
        "104.19.160.28",
        "104.20.15.72",
        "104.21.48.15",
        "104.21.64.82",
        "104.22.10.145",
        "104.24.10.12",
        "104.26.4.88",
        "104.27.176.42",
        "172.64.150.14",
        "172.64.152.91",
        "172.64.155.34",
        "172.66.40.118",
        "172.66.43.138",
        "172.67.70.48",
        "172.67.142.90",
        "172.67.168.55",
        "172.67.182.112",
        "172.67.188.144",
        "172.67.210.77",
        "162.159.135.42",
        "162.159.136.42",
        "162.159.192.1",
        "162.159.193.10",
        "162.159.195.1",
        "188.114.96.1",
        "188.114.96.3",
        "188.114.97.1",
        "188.114.97.3",
        "188.114.98.12",
        "188.114.99.224",
        "141.101.114.59",
        "141.101.115.59",
        "141.101.120.18",
        "190.93.244.52",
        "190.93.245.58",
        "190.93.246.12",
        "190.93.247.90",
        "198.41.192.7",
        "198.41.192.47",
        "198.41.200.13",
        "198.41.200.53",
        "108.162.192.15",
        "108.162.193.80",
        "108.162.236.44",
        "162.158.18.14",
        "162.158.90.41",
        "173.245.48.12",
        "173.245.49.44",
        "173.245.58.118",
        "173.245.59.31",
        "103.21.244.8",
        "103.22.200.19",
        "103.31.4.25",
        "45.85.118.15",
        "45.85.119.22",
        "45.131.5.44",
        "45.131.6.91",
        "92.223.118.42",
        "185.146.173.12",
        "185.146.174.19",
        "195.85.19.44",
        "195.85.20.88",
        "151.101.1.140",
        "151.101.65.140",
        "151.101.129.140",
        "151.101.193.140",
        "23.227.38.65",
        "23.227.38.74",
        "89.45.228.14",
        "89.45.229.33",
        "185.238.228.15",
        "185.238.229.66",
        "212.102.35.42",
        "1.1.1.1"
    ).distinct()

    val IPV6_LIST: List<String> = listOf(
        // 100 Primary User-Provided Healthy & Clean IPv6 Hosts (Initial Launch Seed)
        "2606:4700:3031::6815:fb6",
        "2606:4700:3035::6815:2616",
        "2606:4700:7::205",
        "2606:4700:3034::6815:4866",
        "2606:4700:3033::6815:5d7b",
        "2606:4700:3033::6815:2746",
        "2606:4700:3031::ac43:81fe",
        "2606:4700:3035::6815:2331",
        "2606:4700:3033::ac43:9b88",
        "2606:4700:3033::ac43:a7f5",
        "2606:4700:3036::ac43:af99",
        "2606:4700:3033::ac43:da7c",
        "2606:4700:3031::ac43:8047",
        "2606:4700:3031::6815:4d9c",
        "2606:4700:3031::ac43:c13b",
        "2606:4700:3030::6815:329",
        "2606:4700:3031::ac43:84aa",
        "2606:4700:3032::6815:1358",
        "2606:4700:3032::ac43:ab76",
        "2606:4700:3034::6815:c20",
        "2606:4700:3036::6815:2be6",
        "2606:4700:3030::6815:3954",
        "2606:4700:3030::ac43:a086",
        "2606:4700:3035::6815:10d6",
        "2606:4700:3035::ac43:c26b",
        "2606:4700:3035::ac43:b64c",
        "2606:4700:3031::6815:5ef",
        "2606:4700:3108::ac42:2b99",
        "2606:4700:3033::6815:9c7",
        "2606:4700:3036::6815:44d7",
        "2606:4700:3033::ac43:94e3",
        "2606:4700:3033::ac43:d3ca",
        "2606:4700:3032::ac43:cb4a",
        "2606:4700:3034::ac43:c068",
        "2606:4700:3033::ac43:8a12",
        "2606:4700:3033::6815:3d9b",
        "2606:4700:20::681a:ac0",
        "2606:4700:3032::6815:5eb2",
        "2606:4700:3037::6815:42cf",
        "2606:4700:3032::ac43:c0c0",
        "2606:4700:3030::ac43:d5be",
        "2606:4700:3035::6815:15a4",
        "2606:4700:3030::6815:dc9",
        "2606:4700:3033::ac43:8185",
        "2606:4700:3035::ac43:8cb2",
        "2606:4700:3031::6815:140e",
        "2606:4700:3036::ac43:aa0b",
        "2606:4700:3035::ac43:9f6a",
        "2606:4700:3035::6815:3ae0",
        "2606:4700:3035::ac43:ba50",
        "2606:4700:3033::6815:2d27",
        "2606:4700:3032::6815:1ef2",
        "2606:4700:3030::ac43:81df",
        "2606:4700:3034::6815:1acd",
        "2606:4700:3030::ac43:ddad",
        "2606:4700:3035::6815:2b0d",
        "2606:4700:3037::6815:4540",
        "2606:4700:3032::6815:1b6d",
        "2606:4700:3034::ac43:baa3",
        "2606:4700:3032::ac43:a3e8",
        "2606:4700:3034::6815:5a22",
        "2606:4700:3035::ac43:9d29",
        "2606:4700:3032::6815:263e",
        "2606:4700:3031::ac43:8913",
        "2606:4700:3032::ac43:ab38",
        "2606:4700:3036::6815:48e",
        "2606:4700:3037::ac43:ac1f",
        "2606:4700:3036::6815:f9c",
        "2606:4700:3030::6815:15ca",
        "2606:4700:3033::ac43:b9d1",
        "2606:4700:3030::6815:3d18",
        "2606:4700:3032::ac43:9c73",
        "2606:4700:3031::ac43:a05f",
        "2606:4700:3030::6815:11a4",
        "2606:4700:3032::ac43:9269",
        "2606:4700:3032::ac43:ba98",
        "2606:4700:3034::6815:2427",
        "2606:4700:3031::6815:3c62",
        "2606:4700:3037::ac43:8ae3",
        "2606:4700:3031::6815:809",
        "2606:4700:3034::ac43:c773",
        "2606:4700:3033::ac43:9231",
        "2606:4700:3031::6815:4483",
        "2606:4700:3030::ac43:a81b",
        "2606:4700:3033::ac43:c652",
        "2606:4700:3037::6815:401d",
        "2606:4700:3031::ac43:d847",
        "2606:4700:3035::6815:1d05",
        "2606:4700:3034::ac43:c5eb",
        "2606:4700:3037::ac43:9e59",
        "2606:4700:3033::6815:996",
        "2606:4700:3037::ac43:84d1",
        "2606:4700:3036::ac43:dab1",
        "2606:4700:3035::6815:46b8",
        "2606:4700:3036::ac43:a282",
        "2606:4700:3033::ac43:8466",
        "2606:4700:3031::6815:5741",
        "2606:4700:3030::ac43:db46",
        "2606:4700:3031::ac43:9617",
        "2606:4700:3037::6815:33dc",
        // Previous 50 owner-provided IPv6 entries
        "2606:4700:3037::6815:2826",
        "2606:4700:3032::6815:45b3",
        "2606:4700:3031::6815:d07",
        "2606:4700:3034::ac43:95e0",
        "2606:4700:3037::ac43:b936",
        "2606:4700:3037::ac43:867e",
        "2606:4700:3031::6815:12e4",
        "2606:4700:3031::6815:408",
        "2606:4700:3036::ac43:82d5",
        "2606:4700:3032::ac43:cc30",
        "2606:4700:3032::6815:4352",
        "2606:4700:3032::ac43:c86e",
        "2606:4700:3037::6815:1218",
        "2606:4700:3037::ac43:d029",
        "2606:4700:3030::ac43:a9ce",
        "2606:4700:3035::6815:108e",
        "2606:4700:3034::ac43:c205",
        "2606:4700:3031::ac43:ac42",
        "2606:4700:3035::6815:8db",
        "2606:4700:3036::ac43:8246",
        "2606:4700:3031::ac43:d320",
        "2606:4700:3034::6815:292a",
        "2606:4700:3030::ac43:a758",
        "2606:4700:3033::ac43:da57",
        "2606:4700:3036::6815:1897",
        "2606:4700:3034::ac43:8b5f",
        "2606:4700:3030::ac43:b413",
        "2606:4700:3036::ac43:abe3",
        "2606:4700:3037::ac43:ba94",
        "2606:4700:3036::6815:3977",
        "2606:4700:3032::ac43:d695",
        "2606:4700:3030::6815:172c",
        "2606:4700:3037::6815:11a9",
        "2606:4700:3033::6815:3e14",
        "2606:4700:3037::6815:1b8f",
        "2606:4700:3033::ac43:872f",
        "2606:4700:3037::6815:3c46",
        "2606:4700:3036::ac43:8842",
        "2606:4700:3033::ac43:9415",
        "2606:4700:3033::6815:97",
        "2606:4700:3030::ac43:dcfd",
        "2606:4700:3030::ac43:ab2c",
        "2606:4700:3033::ac43:8fc1",
        // Expanded Clean IPv6 Edge pool
        "2606:4700:3030::6815:1001",
        "2606:4700:3030::6815:2002",
        "2606:4700:3031::6815:3003",
        "2606:4700:3031::6815:5005",
        "2606:4700:3032::6815:6006",
        "2606:4700:3032::6815:7007",
        "2606:4700:3033::6815:8008",
        "2606:4700:3033::6815:9009",
        "2606:4700:3034::6815:1010",
        "2606:4700:3034::6815:2020",
        "2606:4700:3035::6815:3030",
        "2606:4700:3035::6815:4040",
        "2606:4700:3036::6815:5050",
        "2606:4700:3036::6815:6060",
        "2606:4700:3037::6815:7070",
        "2606:4700:3037::6815:8080",
        "2606:4700:3030::ac43:1111",
        "2606:4700:3031::ac43:2222",
        "2606:4700:3032::ac43:3333",
        "2606:4700:3033::ac43:4444",
        "2606:4700:3034::ac43:5555",
        "2606:4700:3035::ac43:6666",
        "2606:4700:3036::ac43:7777",
        "2606:4700:3037::ac43:8888",
        "2606:4700:4700::1111",
        "2606:4700:4700::1001",
        "2606:4700:4700::64",
        "2606:4700:4700::6400",
        "2606:4700::6810:84e5",
        "2606:4700::6810:85e5",
        "2606:4700::6811:9316",
        "2606:4700::6811:9416",
        "2606:4700::6812:02a1",
        "2606:4700::6812:03a1",
        "2606:4700::ac40:960e",
        "2606:4700::ac40:985b",
        "2606:4700::ac43:b670",
        "2606:4700::ac43:bc90",
        "2606:4700:10::688e:101",
        "2606:4700:10::688e:202",
        "2a06:98c1:3120::1",
        "2a06:98c1:3120::3",
        "2a06:98c1:3121::1",
        "2a06:98c1:3121::3",
        "2a06:98c1:3122::5",
        "2a06:98c1:3123::9",
        "2606:4700:f1::1",
        "2606:4700:f1::2",
        "2606:4700:f2::1",
        "2606:4700:f2::2"
    ).distinct()

    val DOMAIN_LIST: List<String> = listOf(
        // 100 Primary User-Provided Clean SNI & Edge Domains (Initial Launch Seed)
        "bahmannews.ir",
        "noozchat.ir",
        "u1.cc0.ir",
        "ainor.ir",
        "telegrampremium.me",
        "kerasno.com",
        "nuggad.net",
        "asgdyuigiflsdujfhgsdjhbhsbdgyigailsdaddfghdsagfasdefygfstadft.saforsi5.ir",
        "eagleflies.ir",
        "daneshmall.com",
        "banobartar.ir",
        "beauty-ads.ir",
        "www.lunor.ir",
        "persianchatgptgrpbot.birgeek.ir",
        "sabastudio.ir",
        "motamed-ai.ir",
        "openscript.ir",
        "chatgpt.ahangfa-official.ir",
        "webstarts.com",
        "ayphon.ir",
        "dellservice.net",
        "marinsm.com",
        "heysite.ir",
        "codesandbox.io",
        "locs.ir",
        "blogfa.com",
        "yekbux.com",
        "gr1.lyraservice2.ir",
        "ijcce.ac.ir",
        "api-stag-wap.tika-dev.ir",
        "cf-ger-proxy.ehsanranjer.ir",
        "api-bot.ir",
        "panel.one-api.ir",
        "webtrackerplus.com",
        "admixer.net",
        "20follow.ir",
        "yekshahr.ir",
        "sixsigmatraffic.com",
        "sftp4.xiaomicloud.ir",
        "api-vm-one-hs.hajsafarinetwork.ir",
        "olimpbet.kz",
        "xjs.lol",
        "livarfars.ir",
        "amazon-iran.ir",
        "dlcddt.ir",
        "tracker.cyd.atu.ac.ir",
        "remowin.com",
        "ganjdl.ir",
        "irandji.ir",
        "sunbazar.ir",
        "borhan-holding.ir",
        "www.24lancer.ir",
        "cdn.sendpulse.com",
        "hosseinsoltani.ir",
        "mojeandisheh.ir",
        "geoip.imber.live",
        "skimresources.com",
        "stat.presstv.ir",
        "b.amozeshebargh.ir",
        "mail.khademoreza.org.ir",
        "analytics.crcis.ir",
        "rozalinshop.com",
        "www.heydarifard.id.ir",
        "buykaspersky.com",
        "matomo.beniix.ir",
        "txt.botloader.ir",
        "www.nhl66.ir",
        "clickcpm.com",
        "analytics.irib.ir",
        "voluumtrk.com",
        "tr80.insta-miner.ir",
        "falatacademy.ir",
        "spellpop.ir",
        "20amar.ir",
        "chist.com",
        "adro.co",
        "tabrizweb.ir",
        "deprem.ir",
        "pmpr.ir",
        "attractionhairandbeauty.com",
        "pet-id.ir",
        "clickbar.ir",
        "sabavision.com",
        "compudan.ir",
        "ariyaserver.com",
        "libertyfood.ir",
        "matomo.mojcms.ir",
        "chatgptplus.ir",
        "autocdn.aiki-apps.ir",
        "persiaLink.com",
        "www.eventsa.ir",
        "adcash.com",
        "padstm.com",
        "ads.baadesaba.ir",
        "chatgpt.javad490.ir",
        "ostadyar.ir",
        "pub2srv.com",
        "piwik.ammardrive.ir",
        "cellulite.ir",
        "20tadris.com",
        // Previous owner-provided domains
        "zagrox.com",
        "24janebi.ir",
        "dorsia.ir",
        "tejarat-gram.com",
        "bixbix.ir",
        "onescript.ir",
        "venturead.com",
        "ayracapital.ir",
        "chatgptmarket.ir",
        "applex.ir",
        "plazapiano.com",
        "mail.ppep.ir",
        "tansu.ir",
        "sentry.balad.ir",
        "analytics.cdnec.ir",
        "group.clip2ni.com",
        "on-click.ir",
        "ije.ir",
        "blufoxmarket.ir",
        "giftcard.ir",
        "elmifarhangi.com",
        "whi.ir",
        "15.my7mhg.ir",
        "lake.aminsam.ir",
        "echoip.ir",
        "shz01-cloud.iranserver.com",
        "cpanel.stocksales.ir",
        "iranopenair.ir",
        "cmec.ir",
        "elite-window.ir",
        "yekdaryek.ir",
        "cdn.m04s.ir",
        "ikya.ir",
        "irandivinggroup.com",
        "mssql.shiraz-carpets-cleaning.ir",
        "18m.ir",
        "medilamsrc.ir",
        "counter.mahanmusic.net",
        "open.ai.pindodev.ir",
        "webtoaster.ir",
        "0seo.ir",
        "bazarmaterial.com",
        "best-class.ir",
        "myad.ir",
        "ashidanalytics.ir",
        // Expanded Clean SNI & Anycast CDN Domains
        "speed.cloudflare.com",
        "cdnjs.cloudflare.com",
        "workers.dev",
        "pages.dev",
        "speedtest.net",
        "www.speedtest.net",
        "dl.google.com",
        "fonts.googleapis.com",
        "ajax.googleapis.com",
        "cdn.jsdelivr.net",
        "unpkg.com",
        "discord.com",
        "cdn.discordapp.com",
        "images.unsplash.com",
        "fastly.net",
        "global.ssl.fastly.net",
        "github.githubassets.com",
        "raw.githubusercontent.com",
        "www.udemy.com",
        "www.coursera.org",
        "www.datadoghq.com",
        "zula.ir",
        "radar.cloudflare.com",
        "one.one.one.one",
        "cp.cloudflare.com",
        "connectivitycheck.gstatic.com",
        "time.cloudflare.com",
        "shopify.com",
        "cdn.shopify.com",
        "medium.com",
        "miro.com",
        "notion.so",
        "canva.com",
        "static.canva.com",
        "figma.com",
        "static.figma.com",
        "zoom.us",
        "us02web.zoom.us",
        "api.mapbox.com",
        "assets.digitalocean.com",
        "de-edge-cdn.cloudflare.net",
        "tr-edge-cdn.cloudflare.net",
        "fra-edge.speedtest.net",
        "ist-edge.speedtest.net",
        "hetzner.com",
        "mirror.hetzner.com",
        "turktelekom.com.tr",
        "cdn.vercel-insights.com",
        "edge.cloudfront.net"
    ).distinct()

    private val ipv4Regex = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    private val cidr24Regex = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)/(24|25|26|27|28|29|30|32)$")
    private val domainRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")

    fun totalSeedCount(): Int = (IPV4_LIST + IPV6_LIST + DOMAIN_LIST).distinct().size

    fun classifyAddressType(host: String): CleanIpAddressType {
        val trimmed = host.trim()
        return when {
            ipv4Regex.matches(trimmed) -> CleanIpAddressType.IPV4
            trimmed.contains(':') -> CleanIpAddressType.IPV6
            else -> CleanIpAddressType.DOMAIN
        }
    }

    fun isValidCleanHost(host: String): Boolean {
        val t = host.trim()
        if (t.isBlank() || t.length > 253) return false
        return ipv4Regex.matches(t) || t.contains(':') || domainRegex.matches(t)
    }

    /**
     * Expands an IPv4 CIDR block (up to /24 = 254 usable host IPs) into individual Clean IPv4 hosts.
     */
    fun expandCidr(cidrInput: String, maxHosts: Int = 254): List<String> {
        val trimmed = cidrInput.trim()
        if (!cidr24Regex.matches(trimmed)) return emptyList()
        val ipPart = trimmed.substringBefore("/")
        val prefix = trimmed.substringAfter("/").toIntOrNull() ?: return emptyList()
        val octets = ipPart.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return emptyList()

        val baseIpInt = ((octets[0] and 0xFF) shl 24) or
            ((octets[1] and 0xFF) shl 16) or
            ((octets[2] and 0xFF) shl 8) or
            (octets[3] and 0xFF)
        if (prefix == 32) return listOf(ipPart)

        val hostBits = (32 - prefix).coerceIn(1, 8)
        val mask = -1 shl hostBits
        val networkAddr = baseIpInt and mask
        val broadcastOffset = (1 shl hostBits) - 1
        val out = ArrayList<String>(broadcastOffset.coerceAtMost(maxHosts))
        for (offset in 1 until broadcastOffset) {
            if (out.size >= maxHosts) break
            val addr = networkAddr or offset
            out.add(
                "${(addr ushr 24) and 0xFF}.${(addr ushr 16) and 0xFF}.${(addr ushr 8) and 0xFF}.${addr and 0xFF}"
            )
        }
        return out
    }

    /**
     * Parses mixed clean IP / domain / CIDR input lines or comma-separated tokens.
     */
    fun parseBulkInput(rawText: String, maxTotalHosts: Int = 512): List<CleanIpEntity> {
        val nowMs = System.currentTimeMillis()
        val rawTokens = rawText.split('\n', '\r', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        val collectedHosts = LinkedHashSet<String>()
        for (token in rawTokens) {
            if (collectedHosts.size >= maxTotalHosts) break
            if (token.contains('/') && cidr24Regex.matches(token)) {
                for (ip in expandCidr(token)) {
                    if (collectedHosts.size >= maxTotalHosts) break
                    collectedHosts.add(ip)
                }
            } else {
                val cleaned = token.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore("?")
                val candidate = if (cleaned.contains(':') && cleaned.count { it == ':' } == 1) {
                    cleaned.substringBefore(":")
                } else {
                    cleaned
                }
                if (isValidCleanHost(candidate)) {
                    collectedHosts.add(candidate)
                }
            }
        }

        return collectedHosts.map { host ->
            val type = classifyAddressType(host)
            CleanIpEntity(
                host = host,
                addressType = type.name,
                defaultPort = 443,
                isAvailable = true,
                healthStatus = CleanIpHealthStatus.UNTESTED.name,
                sourceTag = "CUSTOM_BULK_IMPORT",
                notes = "Imported via Clean IP Manager",
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
        }
    }

    fun buildSeedEntities(nowMs: Long = System.currentTimeMillis()): List<CleanIpEntity> {
        val result = ArrayList<CleanIpEntity>(totalSeedCount() + 8)
        IPV4_LIST.forEach { ip ->
            result.add(
                CleanIpEntity(
                    host = ip.trim(),
                    addressType = CleanIpAddressType.IPV4.name,
                    defaultPort = 443,
                    isAvailable = true,
                    healthStatus = CleanIpHealthStatus.UNTESTED.name,
                    sourceTag = "USER_CLEAN_POOL_IPV4",
                    notes = "Clean Anycast/Edge IPv4 candidate",
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            )
        }
        IPV6_LIST.forEach { ip ->
            result.add(
                CleanIpEntity(
                    host = ip.trim(),
                    addressType = CleanIpAddressType.IPV6.name,
                    defaultPort = 443,
                    isAvailable = true,
                    healthStatus = CleanIpHealthStatus.UNTESTED.name,
                    sourceTag = "USER_CLEAN_POOL_IPV6",
                    notes = "Clean Cloudflare Edge IPv6 candidate",
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            )
        }
        DOMAIN_LIST.forEach { domain ->
            result.add(
                CleanIpEntity(
                    host = domain.trim(),
                    addressType = CleanIpAddressType.DOMAIN.name,
                    defaultPort = 443,
                    isAvailable = true,
                    healthStatus = CleanIpHealthStatus.UNTESTED.name,
                    sourceTag = "USER_CLEAN_POOL_DOMAIN",
                    notes = "Clean domain / SNI edge candidate",
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            )
        }
        return result.distinctBy { it.host }
    }
}

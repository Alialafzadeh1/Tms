const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read vpn configs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vpn_configs").get());
});

test("Alice can create and read her own valid VPN config", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const configDoc = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("config_1");
  
  await assertSucceeds(
    configDoc.set({
      userId: ALICE_UID,
      name: "Germany High-Speed VLESS",
      protocol: "vless",
      server: "de1.tmsvpn.net",
      port: 443,
      uuidOrPassword: "e9f73f1c-7204-4b5b-9d4f-3760bf0344d2",
      transport: "ws",
      security: "tls",
      sni: "de1.tmsvpn.net",
      pathOrServiceName: "/vless-ws",
      rawConfig: "vless://e9f73f1c-7204-4b5b-9d4f-3760bf0344d2@de1.tmsvpn.net:443?security=tls&type=ws#Germany",
      pingMs: 42,
      status: "online",
      isFavorite: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(configDoc.get());
});

test("Bob cannot read Alice's VPN config", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("vpn_configs").doc("config_1").set({
      userId: ALICE_UID,
      name: "Alice Secret Config",
      protocol: "trojan",
      server: "secret.net",
      port: 443,
      uuidOrPassword: "pass",
      rawConfig: "trojan://pass@secret.net:443",
      status: "online",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("config_1").get());
});

test("Alice cannot create VPN config with invalid protocol or missing required fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const configDoc = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("bad_config");
  
  await assertFails(
    configDoc.set({
      userId: ALICE_UID,
      name: "Bad Config",
      protocol: "openvpn_invalid",
      server: "bad.net",
      port: 443,
      uuidOrPassword: "pass",
      rawConfig: "raw",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Alice can create and read a VPN server", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const serverDoc = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("server_1");
  
  await assertSucceeds(
    serverDoc.set({
      userId: ALICE_UID,
      name: "Frankfurt Edge Node",
      country: "Germany",
      countryCode: "DE",
      host: "45.12.34.56",
      port: 443,
      protocol: "VLESS Reality",
      status: "online",
      latencyMs: 38,
      uptimePercentage: 99.9,
      bandwidthUsageGb: 450.5,
      bandwidthLimitGb: 2000.0,
      activeUsers: 84,
      stabilityScore: 96,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  
  await assertSucceeds(serverDoc.get());
});

test("Audit logs: Alice can append audit log, but cannot update or delete it (tamper-resistant)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logDoc = aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1");

  await assertSucceeds(
    logDoc.set({
      userId: ALICE_UID,
      action: "ADMIN_LOGIN_SUCCESS",
      role: "SUPER_ADMIN",
      module: "Overview",
      status: "SUCCESS",
      ipOrDevice: "192.168.1.100",
      details: "Session established with Zero-Trust",
      createdAt: new Date(),
    })
  );

  // Update should fail (audit logs are append-only)
  await assertFails(
    logDoc.update({
      details: "Tampered content",
    })
  );

  // Delete should fail
  await assertFails(logDoc.delete());

  // Bob cannot read Alice's audit log
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1").get());
});

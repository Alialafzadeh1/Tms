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
const ADMIN_UID = "admin_super_1";
const ADMIN_EMAIL = "alafzadehali@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
    admin: true,
    role: "SUPER_ADMIN",
  });
}

function unverifiedSpoofedAdminContext() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

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

test("Unauthenticated user: denied all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vpn_configs").get());
  await assertFails(unauthDb.collection("subscriptions").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("endpoint_inventory").where("assignedUserId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("payment_requests").where("userId", "==", ALICE_UID).get());
});

test("UserProfile: prevents role or subscription self-escalation on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceRef = aliceDb.collection("users").doc(ALICE_UID);

  // Valid initial profile
  await assertSucceeds(
    aliceRef.set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "user",
      subscriptionStatus: "expired",
      deviceCount: 1,
      maxDevices: 2,
      dataUsageBytes: 0,
      dataLimitBytes: 53687091200,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Alice may update displayName only
  await assertSucceeds(
    aliceRef.update({
      displayName: "Alice Updated",
      updatedAt: pastDate(1000),
    })
  );

  // Alice CANNOT escalate role or subscriptionStatus
  await assertFails(
    aliceRef.update({
      role: "SUPER_ADMIN",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceRef.update({
      subscriptionStatus: "active",
      updatedAt: pastDate(500),
    })
  );

  // Cannot create profile directly with SUPER_ADMIN or active subscription
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Escalator",
      email: "bob@example.com",
      role: "SUPER_ADMIN",
      subscriptionStatus: "active",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("VPN Configs: origin permissions enforce USER_IMPORTED vs SUBSCRIPTION_ASSIGNED vs SYSTEM_MANAGED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = adminContext().firestore();
  const spoofDb = unverifiedSpoofedAdminContext().firestore();

  const userCfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("cfg_user_1");
  const subCfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sub_1_ep1");
  const sysCfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sys_default_1");

  // 1. Alice can create, read, update, delete her USER_IMPORTED config
  await assertSucceeds(
    userCfgRef.set({
      userId: ALICE_UID,
      name: "Alice Custom VLESS",
      protocol: "vless",
      server: "edge.tmsvpn.net",
      port: 443,
      uuidOrPassword: "e9f73f1c-7204-4b5b-9d4f-3760bf0344d2",
      transport: "ws",
      security: "tls",
      sni: "edge.tmsvpn.net",
      pathOrServiceName: "/ws",
      rawConfig: "vless://e9f73f1c-7204-4b5b-9d4f-3760bf0344d2@edge.tmsvpn.net:443",
      pingMs: 40,
      status: "online",
      isFavorite: true,
      origin: "USER_IMPORTED",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(userCfgRef.get());

  // 2. Alice CANNOT forge SUBSCRIPTION_ASSIGNED or SYSTEM_MANAGED config
  await assertFails(
    subCfgRef.set({
      userId: ALICE_UID,
      name: "Forged Dedicated IP",
      protocol: "vless",
      server: "vip.tmsvpn.net",
      port: 443,
      uuidOrPassword: "forged-uuid",
      rawConfig: "vless://forged-uuid@vip.tmsvpn.net:443",
      origin: "SUBSCRIPTION_ASSIGNED",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Unverified spoofed admin email is ALSO rejected
  await assertFails(
    spoofDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sub_1_ep1").set({
      userId: ALICE_UID,
      name: "Spoofed Admin IP",
      protocol: "vless",
      server: "vip.tmsvpn.net",
      port: 443,
      uuidOrPassword: "spoof-uuid",
      rawConfig: "vless://spoof-uuid@vip.tmsvpn.net:443",
      origin: "SUBSCRIPTION_ASSIGNED",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // 3. Seed SUBSCRIPTION_ASSIGNED (by admin) and SYSTEM_MANAGED (seed)
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sub_1_ep1").set({
      userId: ALICE_UID,
      name: "Dedicated FR #1",
      protocol: "vless",
      server: "fr1.tmsvpn.net",
      port: 443,
      uuidOrPassword: "real-user-token",
      rawConfig: "vless://real-user-token@fr1.tmsvpn.net:443",
      origin: "SUBSCRIPTION_ASSIGNED",
      subscriptionId: "sub_alice",
      endpointId: "ep_1",
      allocationVersion: 1,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sys_default_1").set({
      userId: ALICE_UID,
      name: "System Core Node",
      protocol: "vless",
      server: "core.tmsvpn.net",
      port: 443,
      uuidOrPassword: "core-uuid",
      rawConfig: "vless://core-uuid@core.tmsvpn.net:443",
      origin: "SYSTEM_MANAGED",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  // 4. Alice CANNOT delete SUBSCRIPTION_ASSIGNED or SYSTEM_MANAGED; Admin CANNOT delete SYSTEM_MANAGED either
  await assertFails(subCfgRef.delete());
  await assertFails(sysCfgRef.delete());
  await assertFails(
    adminDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sys_default_1").delete()
  );

  // Alice CAN delete USER_IMPORTED; Admin CAN rotate out SUBSCRIPTION_ASSIGNED
  await assertSucceeds(userCfgRef.delete());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).collection("vpn_configs").doc("sub_1_ep1").delete()
  );
});

test("Subscriptions & History: owner read-only; admin-only lifecycle transitions; immutable history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = adminContext().firestore();

  const subDoc = adminDb.collection("subscriptions").doc("sub_alice");

  // Client cannot self-activate subscription
  await assertFails(
    aliceDb.collection("subscriptions").doc("sub_alice").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      planId: "plan_1y",
      planDurationDays: 365,
      endpointAllocationLimit: 50,
      status: "ACTIVE",
      paymentStatus: "CONFIRMED",
      currentAllocationVersion: 1,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Verified Admin activates 1-Month Plan (30 days, 5 daily dedicated IPs)
  await assertSucceeds(
    subDoc.set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      planId: "plan_1m",
      planDurationDays: 30,
      endpointAllocationLimit: 5,
      startDate: pastDate(10000),
      expirationDate: new Date(Date.now() + 30 * 86400 * 1000),
      status: "ACTIVE",
      paymentStatus: "CONFIRMED",
      lastRotationCycleKey: "2026-11-18",
      currentAllocationVersion: 1,
      createdAt: pastDate(10000),
      updatedAt: pastDate(1000),
    })
  );

  // Alice can read her own filtered subscription query, Bob cannot
  await assertSucceeds(aliceDb.collection("subscriptions").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("subscriptions").doc("sub_alice").get());
  await assertFails(bobDb.collection("subscriptions").where("userId", "==", ALICE_UID).get());

  // Admin upgrades to 3 Months (90 days, 15 IPs) preserving createdAt & userId
  await assertSucceeds(
    subDoc.update({
      planId: "plan_3m",
      planDurationDays: 90,
      endpointAllocationLimit: 15,
      updatedAt: pastDate(500),
    })
  );

  // Nobody can delete a subscription record
  await assertFails(subDoc.delete());

  // Subscription History: admin append-only, immutable
  const histRef = adminDb.collection("users").doc(ALICE_UID).collection("subscription_history").doc("ev_1");
  await assertSucceeds(
    histRef.set({
      subscriptionId: "sub_alice",
      userId: ALICE_UID,
      action: "UPGRADE",
      fromPlanId: "plan_1m",
      toPlanId: "plan_3m",
      statusAfter: "ACTIVE",
      actorId: ADMIN_UID,
      details: "Upgraded to 3M plan (15 IPs/day)",
      createdAt: pastDate(500),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("subscription_history").get());
  await assertFails(histRef.update({ details: "tampered" }));
  await assertFails(histRef.delete());
});

test("Central Endpoint Inventory & Private Allocated Endpoints: strips private creds and enforces exclusivity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = adminContext().firestore();

  // Admin cannot accidentally store private credentials in central endpoint_inventory
  await assertFails(
    adminDb.collection("endpoint_inventory").doc("ep_bad").set({
      name: "Leaky Node",
      countryCode: "DE",
      host: "198.51.100.10",
      port: 443,
      protocol: "vless",
      enabled: true,
      healthy: true,
      priorityTier: 2,
      assignedUserId: "",
      credentialToken: "SECRET_SHOULD_NOT_BE_HERE",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Valid healthy central endpoint assigned to Alice
  await assertSucceeds(
    adminDb.collection("endpoint_inventory").doc("ep_1").set({
      name: "Frankfurt Dedicated #1",
      country: "Germany",
      countryCode: "DE",
      host: "198.51.100.11",
      port: 443,
      protocol: "vless",
      transport: "ws",
      security: "tls",
      sni: "cdn.tmsvpn.net",
      pathOrServiceName: "/vless",
      enabled: true,
      healthy: true,
      priorityTier: 3,
      latencyMs: 38,
      stabilityScore: 99,
      assignedUserId: ALICE_UID,
      assignedSubscriptionId: "sub_alice",
      allocationExpiresAt: new Date(Date.now() + 86400 * 1000),
      failureCount: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Unrelated Bob cannot read or list Alice's assigned inventory doc
  await assertFails(bobDb.collection("endpoint_inventory").doc("ep_1").get());
  await assertFails(bobDb.collection("endpoint_inventory").get());
  // Alice CAN read only her assigned endpoints
  await assertSucceeds(aliceDb.collection("endpoint_inventory").doc("ep_1").get());
  await assertSucceeds(aliceDb.collection("endpoint_inventory").where("assignedUserId", "==", ALICE_UID).get());

  // Private credentialed allocation written by Admin into Alice's private subcollection
  const allocRef = adminDb.collection("users").doc(ALICE_UID).collection("allocated_endpoints").doc("alloc_1");
  await assertSucceeds(
    allocRef.set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      endpointId: "ep_1",
      endpointName: "Frankfurt Dedicated #1",
      country: "Germany",
      countryCode: "DE",
      host: "198.51.100.11",
      port: 443,
      protocol: "vless",
      transport: "ws",
      security: "tls",
      sni: "cdn.tmsvpn.net",
      pathOrServiceName: "/vless",
      credentialToken: "alice-private-uuid-999",
      rawConfig: "vless://alice-private-uuid-999@198.51.100.11:443?security=tls&type=ws#DE1",
      allocationVersion: 1,
      cycleKey: "2026-11-18",
      allocationStatus: "ACTIVE",
      healthy: true,
      latencyMs: 38,
      allocatedAt: pastDate(),
      expiresAt: new Date(Date.now() + 86400 * 1000),
      updatedAt: pastDate(),
    })
  );

  // Alice can read her own allocated_endpoints; Bob cannot; Alice cannot write them
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("allocated_endpoints").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("allocated_endpoints").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("allocated_endpoints").doc("alloc_2").set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      endpointId: "ep_2",
      host: "198.51.100.12",
      port: 443,
      protocol: "vless",
      credentialToken: "self-minted",
      rawConfig: "vless://self-minted@198.51.100.12:443",
      allocationVersion: 2,
      allocationStatus: "ACTIVE",
      updatedAt: pastDate(),
    })
  );
});

test("Payment Requests: clients can only submit PENDING_PAYMENT, never self-confirm", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = adminContext().firestore();

  // Client self-confirmation MUST fail
  await assertFails(
    aliceDb.collection("payment_requests").doc("pay_spoof").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      subscriptionId: "sub_alice",
      planId: "plan_1y",
      actionType: "NEW",
      provider: "TRUSTED_BACKEND_GATEWAY",
      referenceCode: "FAKE-999",
      status: "CONFIRMED",
      verifiedBy: ALICE_UID,
      createdAt: pastDate(),
    })
  );

  // Honest PENDING_PAYMENT submission succeeds
  const payRef = aliceDb.collection("payment_requests").doc("pay_real_1");
  await assertSucceeds(
    payRef.set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      subscriptionId: "sub_alice",
      planId: "plan_3m",
      actionType: "NEW",
      provider: "TRUSTED_BACKEND_GATEWAY",
      referenceCode: "TX-88412",
      status: "PENDING_PAYMENT",
      note: "Awaiting trusted backend confirmation",
      createdAt: pastDate(),
    })
  );

  // Client cannot self-update after creation
  await assertFails(payRef.update({ status: "CONFIRMED" }));

  // Trusted Admin confirms verification
  await assertSucceeds(
    adminDb.collection("payment_requests").doc("pay_real_1").update({
      status: "CONFIRMED",
      verifiedAt: pastDate(500),
      verifiedBy: ADMIN_UID,
    })
  );
});

test("Shortage Alerts & Rotation Audit Logs: Admin-only and tamper-resistant logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = adminContext().firestore();

  const alertRef = adminDb.collection("shortage_alerts").doc("shortage_1");
  await assertSucceeds(
    alertRef.set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      planId: "plan_1y",
      requestedCount: 50,
      allocatedCount: 12,
      shortfallCount: 38,
      cycleKey: "2026-11-18",
      reason: "Insufficient healthy endpoints in central pool",
      resolved: false,
      createdAt: pastDate(),
    })
  );
  await assertFails(aliceDb.collection("shortage_alerts").doc("shortage_1").get());

  const logRef = adminDb.collection("rotation_logs").doc("rot_1");
  await assertSucceeds(
    logRef.set({
      cycleKey: "2026-11-18",
      triggerMode: "MANUAL_ALL",
      triggeredByUid: ADMIN_UID,
      targetUserCount: 1,
      rotatedUsersCount: 1,
      replacedEndpointsCount: 2,
      shortageCount: 1,
      status: "PARTIAL_SHORTAGE",
      details: "Shortage recorded without fabricating endpoints",
      createdAt: pastDate(),
    })
  );
  await assertFails(aliceDb.collection("rotation_logs").doc("rot_1").get());
  await assertFails(logRef.update({ status: "SUCCESS" }));
  await assertFails(logRef.delete());
});

test("Allocation History & Concurrent MVCC Double-Assignment Prevention: immutable per-user trail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = adminContext().firestore();

  const aliceHistRef = adminDb.collection("users").doc(ALICE_UID).collection("allocation_history").doc("hist_1");

  // 1. Admin creates immutable ALLOCATED history record for Alice
  await assertSucceeds(
    aliceHistRef.set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      planId: "plan_3m",
      endpointId: "ep_1",
      endpointName: "Frankfurt Dedicated #1",
      host: "198.51.100.11",
      port: 443,
      protocol: "vless",
      action: "ALLOCATED",
      allocationVersion: 1,
      cycleKey: "2026-11-18",
      reason: "Allocated to 3-Month plan (15 endpoints/day)",
      actorId: ADMIN_UID,
      createdAt: pastDate(2000),
    })
  );

  // 2. Alice can read her own allocation_history; Bob cannot
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("allocation_history").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("allocation_history").doc("hist_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("allocation_history").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("allocation_history").doc("hist_1").get());

  // 3. Clients cannot forge, update, or delete allocation_history
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("allocation_history").doc("hist_forged").set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      planId: "plan_1y",
      endpointId: "ep_99",
      action: "ALLOCATED",
      allocationVersion: 99,
      actorId: ALICE_UID,
      createdAt: pastDate(),
    })
  );
  await assertFails(aliceHistRef.update({ action: "ROTATED_OUT" }));
  await assertFails(aliceHistRef.delete());

  // 4. Reject invalid action types
  await assertFails(
    adminDb.collection("users").doc(ALICE_UID).collection("allocation_history").doc("hist_bad_action").set({
      userId: ALICE_UID,
      subscriptionId: "sub_alice",
      endpointId: "ep_1",
      action: "HACKED_ACTION",
      allocationVersion: 2,
      actorId: ADMIN_UID,
      createdAt: pastDate(),
    })
  );

  // 5. Transactional MVCC claim prevents double-assignment on shared endpoint_inventory
  const epRef = adminDb.collection("endpoint_inventory").doc("ep_shared_test");
  await assertSucceeds(
    epRef.set({
      name: "Shared Lock Test Node",
      country: "Finland",
      countryCode: "FI",
      host: "198.51.100.77",
      port: 443,
      protocol: "vless",
      enabled: true,
      healthy: true,
      priorityTier: 3,
      assignedUserId: "",
      assignedSubscriptionId: "",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  await assertSucceeds(
    adminDb.runTransaction(async (tx) => {
      const snap = await tx.get(epRef);
      if (snap.data().assignedUserId !== "") {
        throw new Error("Already assigned");
      }
      tx.update(epRef, {
        assignedUserId: ALICE_UID,
        assignedSubscriptionId: "sub_alice",
        updatedAt: pastDate(1000),
      });
    })
  );

  let bobBlocked = false;
  try {
    await adminDb.runTransaction(async (tx) => {
      const snap = await tx.get(epRef);
      if (snap.data().assignedUserId !== "") {
        throw new Error("Endpoint already claimed by another user");
      }
      tx.update(epRef, {
        assignedUserId: BOB_UID,
        assignedSubscriptionId: "sub_bob",
        updatedAt: pastDate(500),
      });
    });
  } catch (err) {
    bobBlocked = err.message.includes("Endpoint already claimed");
  }
  if (!bobBlocked) {
    throw new Error("Expected transactional exclusivity check to reject second assignment");
  }
});

test("Hardened admin claims & ecosystem collections (crypto, trial, referrals, app_releases)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const spoofEmailOnlyDb = testEnv.authenticatedContext("email_only_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const adminDb = adminContext().firestore();

  // Verified email WITHOUT admin custom claim MUST NOT grant admin powers
  await assertFails(
    spoofEmailOnlyDb.collection("app_releases").doc("rel_1").set({
      releaseId: "rel_1",
      versionName: "2.0.0",
      versionCode: 20,
      downloadUrl: "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/r2/app.apk",
      isPublished: true,
    })
  );

  // Admin can create unpublished and published APK releases
  await assertSucceeds(
    adminDb.collection("app_releases").doc("rel_pub").set({
      releaseId: "rel_pub",
      versionName: "2.1.0",
      versionCode: 21,
      downloadUrl: "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/r2/pub.apk",
      isPublished: true,
    })
  );
  await assertSucceeds(
    adminDb.collection("app_releases").doc("rel_draft").set({
      releaseId: "rel_draft",
      versionName: "2.2.0-rc1",
      versionCode: 22,
      downloadUrl: "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/r2/draft.apk",
      isPublished: false,
    })
  );

  // Regular user can read published release, denied unpublished draft
  await assertSucceeds(aliceDb.collection("app_releases").doc("rel_pub").get());
  await assertFails(aliceDb.collection("app_releases").doc("rel_draft").get());

  // User cannot forge crypto_invoices or trial_grants directly
  await assertFails(
    aliceDb.collection("crypto_invoices").doc("inv_fake").set({
      invoiceId: "inv_fake",
      userId: ALICE_UID,
      status: "CONFIRMED",
      createdAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("trial_grants").doc(ALICE_UID).set({
      userId: ALICE_UID,
      activatedAtMs: Date.now(),
      expiresAtMs: Date.now() + 86400000,
      nextEligibleAtMs: Date.now() + 7 * 86400000,
    })
  );

  // User can submit PENDING referral reward request for milestone 2, cannot submit pre-APPROVED
  await assertSucceeds(
    aliceDb.collection("referral_reward_requests").doc("req_alice_2").set({
      userId: ALICE_UID,
      milestoneThreshold: 2,
      rewardGb: 1.0,
      status: "PENDING",
      createdAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("referral_reward_requests").doc("req_alice_hack").set({
      userId: ALICE_UID,
      milestoneThreshold: 10,
      rewardGb: 10.0,
      status: "APPROVED",
      createdAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("referral_reward_requests").doc("req_alice_2").update({
      status: "APPROVED",
    })
  );
  await assertSucceeds(
    adminDb.collection("referral_reward_requests").doc("req_alice_2").update({
      status: "APPROVED",
      adminNote: "Verified",
    })
  );
});



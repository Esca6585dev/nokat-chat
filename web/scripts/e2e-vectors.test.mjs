// Self-test of the real secret-chat crypto code (src/lib/e2e-core.ts) against
// docs/secret-chat-test-vectors.json. Run: npm run test:e2e  (Node 22+).
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import * as e2e from "../src/lib/e2e-core.ts";

const here = dirname(fileURLToPath(import.meta.url));
const candidates = [
  process.env.E2E_VECTORS,
  resolve(here, "../../docs/secret-chat-test-vectors.json"),
  resolve(here, "../docs/secret-chat-test-vectors.json"),
].filter(Boolean);

let vectors;
for (const p of candidates) {
  try {
    vectors = JSON.parse(await readFile(p, "utf8"));
    break;
  } catch {
    /* try next */
  }
}
assert.ok(vectors, `test vectors not found (tried ${candidates.join(", ")})`);

const subtle = globalThis.crypto.subtle;
let passed = 0;
async function check(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`  ok  ${name}`);
  } catch (err) {
    console.error(`  FAIL ${name}\n       ${err.message}`);
    process.exitCode = 1;
  }
}

const v = vectors;
const A = await e2e.importPrivateKeyPkcs8(v.initiator.private_pkcs8);
const B = await e2e.importPrivateKeyPkcs8(v.peer.private_pkcs8);

console.log("Nokat secret chat v1 test vectors");

await check("public keys match private keys (pkcs8 → jwk → raw)", async () => {
  for (const side of [v.initiator, v.peer]) {
    const k = await e2e.importPrivateKeyPkcs8(side.private_pkcs8, true);
    const jwk = await subtle.exportKey("jwk", k);
    const raw = new Uint8Array(65);
    raw[0] = 4;
    raw.set(Buffer.from(jwk.x, "base64url"), 1);
    raw.set(Buffer.from(jwk.y, "base64url"), 33);
    assert.equal(e2e.toBase64(raw), side.public_raw);
    assert.equal(Buffer.from(jwk.d, "base64url").toString("hex"), side.private_d_hex);
  }
});

await check("ECDH shared secret (both sides)", async () => {
  assert.equal(e2e.toHex(await e2e.deriveSharedSecret(A, v.peer.public_raw)), v.shared_secret_hex);
  assert.equal(e2e.toHex(await e2e.deriveSharedSecret(B, v.initiator.public_raw)), v.shared_secret_hex);
});

const keyA = await e2e.deriveChatKey(A, v.peer.public_raw, v.chat_id, true);
const keyB = await e2e.deriveChatKey(B, v.initiator.public_raw, v.chat_id, true);

await check("HKDF chat key (both sides)", async () => {
  assert.equal(e2e.toHex(new Uint8Array(await subtle.exportKey("raw", keyA))), v.chat_key_hex);
  assert.equal(e2e.toHex(new Uint8Array(await subtle.exportKey("raw", keyB))), v.chat_key_hex);
});

await check("decrypt vector ciphertext on the peer side", async () => {
  const pt = await e2e.decryptMessage(keyB, v.ciphertext, v.chat_id, v.sender_id);
  assert.equal(pt, v.plaintext_utf8);
  const payload = e2e.parsePayload(pt);
  assert.equal(payload.kind, "text");
});

await check("re-encrypt with fixed IV gives identical ciphertext", async () => {
  const ct = await e2e.encryptMessage(keyA, v.plaintext_utf8, v.chat_id, v.sender_id, e2e.fromHex(v.iv_hex));
  assert.equal(ct, v.ciphertext);
});

await check("wrong AAD (sender) is rejected", async () => {
  await assert.rejects(e2e.decryptMessage(keyB, v.ciphertext, v.chat_id, "someone-else"));
});

await check("fingerprint", async () => {
  assert.equal(await e2e.fingerprint(v.initiator.public_raw, v.peer.public_raw), v.fingerprint);
});

await check("file blob decrypt", async () => {
  const pt = await e2e.decryptFile(e2e.fromBase64(v.file.blob_b64), v.file.key_b64);
  assert.equal(e2e.fromUtf8(pt), v.file.plaintext_utf8);
});

await check("file blob encrypt with fixed key + IV", async () => {
  const enc = await e2e.encryptFile(
    e2e.utf8(v.file.plaintext_utf8),
    e2e.fromBase64(v.file.key_b64),
    e2e.fromHex(v.file.iv_hex),
  );
  assert.equal(e2e.toBase64(enc.blob), v.file.blob_b64);
  assert.equal(enc.keyB64, v.file.key_b64);
});

await check("fresh key pair round-trip (non-extractable private key)", async () => {
  const a = await e2e.generateChatKeyPair();
  const b = await e2e.generateChatKeyPair();
  assert.equal(a.privateKey.extractable, false);
  assert.equal(e2e.fromBase64(a.publicKeyB64).length, 65);
  const ka = await e2e.deriveChatKey(a.privateKey, b.publicKeyB64, "chat-x");
  const kb = await e2e.deriveChatKey(b.privateKey, a.publicKeyB64, "chat-x");
  const msg = JSON.stringify({ v: 1, kind: "text", text: "Salam 👋" });
  const ct = await e2e.encryptMessage(ka, msg, "chat-x", "me");
  assert.equal(await e2e.decryptMessage(kb, ct, "chat-x", "me"), msg);
});

console.log(`${passed} passed${process.exitCode ? ", some FAILED" : ""}`);

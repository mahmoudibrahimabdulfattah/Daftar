// Run with the Firestore emulator: `firebase emulators:exec --only firestore "npm --prefix tests test"` from docs/firebase.
import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp, Bytes } from 'firebase/firestore';

const UUID = '11111111-2222-4333-8444-555555555555';
const HASH = 'a'.repeat(64);
const ciphertext = (size) => Bytes.fromUint8Array(new Uint8Array(size));

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'daftar-rules-test',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

function db(uid) {
  return uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
}

async function seedActive(uid, revision = 1) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `users/${uid}`), { schemaVersion: 1, state: 'active', revision, updatedAt: new Date() });
  });
}

function validBackup(overrides = {}) {
  return {
    backupId: UUID,
    formatVersion: 1,
    size: 200,
    sha256: HASH,
    createdAt: serverTimestamp(),
    ciphertext: ciphertext(200),
    ...overrides,
  };
}

test('an unauthenticated client reads nothing', async () => {
  await seedActive('alice');
  await assertFails(getDoc(doc(db(null), 'users/alice')));
});

test('a signed-in user cannot read another UID', async () => {
  await seedActive('alice');
  await assertFails(getDoc(doc(db('bob'), 'users/alice')));
});

test('a control document is created once, at revision zero, in the active state', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), 'users/alice'), { schemaVersion: 1, state: 'active', revision: 0, updatedAt: serverTimestamp() }));
  await env.clearFirestore();
  await assertFails(setDoc(doc(db('alice'), 'users/alice'), { schemaVersion: 1, state: 'active', revision: 3, updatedAt: serverTimestamp() }));
});

test('the control document cannot be deleted by the client', async () => {
  await seedActive('alice');
  await assertFails(deleteDoc(doc(db('alice'), 'users/alice')));
});

test('a fourth backup slot and nested documents are denied', async () => {
  await seedActive('alice');
  await assertFails(setDoc(doc(db('alice'), 'users/alice/backups/3'), validBackup()));
  await assertFails(setDoc(doc(db('alice'), 'users/alice/backups/0/extra/x'), { any: 1 }));
});

test('a valid backup write requires the matching revision increment in the same request', async () => {
  await seedActive('alice', 1);
  const batch = db('alice').batch();
  batch.set(doc(db('alice'), 'users/alice/backups/0'), validBackup());
  batch.update(doc(db('alice'), 'users/alice'), { revision: 2, updatedAt: serverTimestamp(), schemaVersion: 1, state: 'active' });
  await assertSucceeds(batch.commit());

  const withoutIncrement = db('alice').batch();
  withoutIncrement.set(doc(db('alice'), 'users/alice/backups/1'), validBackup());
  await assertFails(withoutIncrement.commit());
});

test('invalid field types, lengths and digests are rejected', async () => {
  await seedActive('alice', 1);
  const bump = async (data) => {
    const batch = db('alice').batch();
    batch.set(doc(db('alice'), 'users/alice/backups/0'), data);
    batch.update(doc(db('alice'), 'users/alice'), { revision: 2, updatedAt: serverTimestamp(), schemaVersion: 1, state: 'active' });
    return batch.commit();
  };
  await assertFails(bump(validBackup({ ciphertext: ciphertext(83) })));
  await assertFails(bump(validBackup({ ciphertext: ciphertext(786433), size: 786433 })));
  await assertFails(bump(validBackup({ size: 199 })));
  await assertFails(bump(validBackup({ sha256: 'A'.repeat(64) })));
  await assertFails(bump(validBackup({ formatVersion: 2 })));
  await assertFails(bump(validBackup({ backupId: 'not-a-uuid' })));
  await assertFails(bump(validBackup({ ciphertext: 'base64-string', size: 200 })));
});

test('the account may move to deleting once, and deleting blocks new backups', async () => {
  await seedActive('alice', 1);
  await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice'), { state: 'deleting', revision: 2, updatedAt: serverTimestamp(), schemaVersion: 1 }));
  await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { state: 'active', revision: 3, updatedAt: serverTimestamp(), schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('alice'), 'users/alice/backups/0'), validBackup()));
});

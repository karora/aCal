package org.davical.acal.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.davical.acal.database.TestDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The key the database is encrypted with.  It has to come back the same for
 * as long as the database lives, and its loss has to be told apart from a
 * failure that may pass, because a lost key means the database is thrown away.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseKeyManagerTest {

	private Context context;

	@Before
	public void setUp() throws Exception {
		context = ApplicationProvider.getApplicationContext();
		TestDatabase.startWithNothing();
	}

	@Test
	public void thereIsNoKeyUntilOneIsMade() throws Exception {
		assertNull(DatabaseKeyManager.getExistingKey(context));
		assertFalse(StoredKey.file(context).exists());
	}

	@Test
	public void aKeyIsInTheFormSqlCipherTakesAsTheKeyItself() throws Exception {
		String key = new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII");
		assertTrue(key, key.matches("x'[0-9a-f]{64}'"));
	}

	@Test
	public void theSameKeyComesBackEveryTime() throws Exception {
		String key = new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII");
		assertEquals(key, new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII"));
		assertEquals(key, new String(DatabaseKeyManager.getExistingKey(context), "US-ASCII"));
	}

	@Test
	public void theKeyIsReadBackFromStorageByANewProcess() throws Exception {
		String key = new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII");

		StoredKey.forgetCached();

		assertEquals(key, new String(DatabaseKeyManager.getExistingKey(context), "US-ASCII"));
		assertEquals(key, new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII"));
	}

	@Test
	public void theKeyIsNotStoredAsItIs() throws Exception {
		String key = new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII");
		String hex = key.substring(2, key.length() - 1);

		byte[] stored = java.nio.file.Files.readAllBytes(StoredKey.file(context).toPath());
		StringBuilder storedHex = new StringBuilder();
		for( String part : new String(stored, "US-ASCII").split(":") ) {
			for( byte b : android.util.Base64.decode(part, android.util.Base64.NO_WRAP) ) {
				storedHex.append(String.format("%02x", b));
			}
			storedHex.append(' ');
		}
		assertFalse(storedHex.toString().contains(hex));
	}

	@Test
	public void aDiscardedKeyIsGoneAndTheNextIsDifferent() throws Exception {
		String key = new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII");

		DatabaseKeyManager.discardKey(context);

		assertNull(DatabaseKeyManager.getExistingKey(context));
		assertNotEquals(key, new String(DatabaseKeyManager.getOrCreateKey(context), "US-ASCII"));
	}

	@Test
	public void aKeyWhoseWrappingKeyIsGoneIsLost() throws Exception {
		DatabaseKeyManager.getOrCreateKey(context);
		StoredKey.loseWrappingKey();
		StoredKey.forgetCached();

		try {
			DatabaseKeyManager.getExistingKey(context);
			fail("A key that cannot be unwrapped must be reported as lost");
		}
		catch( DatabaseKeyManager.KeyLostException expected ) {
		}
	}

	@Test
	public void aKeyWrappedByADifferentKeyIsLost() throws Exception {
		DatabaseKeyManager.getOrCreateKey(context);
		String wrappedByTheOldKey = new String(java.nio.file.Files.readAllBytes(StoredKey.file(context).toPath()), "US-ASCII");
		DatabaseKeyManager.discardKey(context);
		DatabaseKeyManager.getOrCreateKey(context);
		StoredKey.overwriteWith(context, wrappedByTheOldKey);
		StoredKey.forgetCached();

		try {
			DatabaseKeyManager.getExistingKey(context);
			fail("A key that cannot be unwrapped must be reported as lost");
		}
		catch( DatabaseKeyManager.KeyLostException expected ) {
		}
	}

	@Test
	public void aStoredKeyThatMakesNoSenseIsLost() throws Exception {
		DatabaseKeyManager.getOrCreateKey(context);
		StoredKey.overwriteWith(context, "nothing like a wrapped key");
		StoredKey.forgetCached();

		try {
			DatabaseKeyManager.getExistingKey(context);
			fail("A stored key that cannot be read must be reported as lost");
		}
		catch( DatabaseKeyManager.KeyLostException expected ) {
		}
	}
}

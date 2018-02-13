package com.khizhny.freepasswordkeeper;

import org.junit.Test;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;

import static com.khizhny.freepasswordkeeper.LoginActivity.generateKey;
import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
		@Test
		public void KeyCreation() throws Exception {
				try {
						SecretKey secretKey1=generateKey("Pass1".toCharArray(),"userName");
						SecretKey secretKey2=generateKey("Pass1".toCharArray(),"userName");
						assertEquals(secretKey1, secretKey2);
				} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
				} catch (InvalidKeySpecException e) {
						e.printStackTrace();
				}

		}

		@Test
		public void EncryptionTest() throws  Exception {
				String text="the quick brown fox jumps over the lazy dog";
				Decrypter decrypter = new Decrypter("ABCDEFGHIJKL");
				String encrypted = decrypter.encrypt(text);
				String decrypted = decrypter.decrypt(encrypted);/**/
				assertEquals (text, decrypted);

		}
}
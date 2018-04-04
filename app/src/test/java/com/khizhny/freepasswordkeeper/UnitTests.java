package com.khizhny.freepasswordkeeper;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * local unit test, which will execute on the development machine (host).
  * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class UnitTests {

		@Test
		public void EncryptionTest() {
				String text="the quick brown fox jumps over the lazy dog";
				Decrypter decrypter = new Decrypter("ABCDEFGHIJKL","");
				String encrypted = decrypter.encrypt(text);
				String decrypted = decrypter.decrypt(encrypted);/**/
				assertEquals (text, decrypted);

		}

		@Test
		public void pwdTest() {
				String text=Gpw.generate(12);
				assertEquals (12, text.length());

		}
}
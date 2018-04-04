package com.khizhny.freepasswordkeeper;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@SuppressWarnings("ConstantConditions")
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

		@Test
		public void useAppContext()  {
				// Context of the app under test.
				Context appContext = InstrumentationRegistry.getTargetContext();
				DbHelper db = DbHelper.getInstance(appContext);
				User user1=new User("user1",5);
				user1.decrypter=new Decrypter("pass","login");
				db.addOrEditNode(user1,false);
				User user2 = db.getUser(5,false,"pass","login");
				assertEquals(user2.name, user1.name);
		}

		@Test
		public void EncryptionTest()  {
				String text="the quick brown fox jumps over the lazy dog";
				Decrypter decrypter = new Decrypter("pass1","user1");
				String encrypted = decrypter.encrypt(text);
				assertEquals("ZpVd4G/2PUsbpFY7RCDb9D8yZTA2AMhUVM2eko6bvt/E5E0bAqUKkIsFxo81sMtO\n",encrypted);
		}

		@Test
		public void DecryptionTest()  {
				Decrypter decrypter = new Decrypter("pass1","user1");
				String decrypted = decrypter.decrypt("ZpVd4G/2PUsbpFY7RCDb9D8yZTA2AMhUVM2eko6bvt/E5E0bAqUKkIsFxo81sMtO\n");/**/
				assertEquals ("the quick brown fox jumps over the lazy dog",decrypted);
		}
}

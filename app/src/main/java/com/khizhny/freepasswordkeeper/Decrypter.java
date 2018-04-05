package com.khizhny.freepasswordkeeper;

import android.util.Base64;
import android.util.Log;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;

import static com.khizhny.freepasswordkeeper.LoginActivity.TAG;

class Decrypter {
    private static final String PBKDF_2_WITH_HMAC_SHA_1 = "PBKDF2WithHmacSHA1";
    private static final String AES_CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";
    private static final String AES_CBC_PKCS7_PADDING = "AES/CBC/PKCS7Padding";
    private Cipher cipher;
    private static final int ITERATION_COUNT = 1024;
    private static final int KEY_STRENGTH = 128;  //  Maximum allowed key length
    private SecretKey key;
    static final byte[] IV = new byte[]{3, 4, 2, 4, -55, 127, -128, 80, 99, 40, 20, 4, 34, 3, 5, 2};

    Decrypter(String passPhrase, String login) {
        SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance(PBKDF_2_WITH_HMAC_SHA_1);
            byte[] salt = (login + "1234567890123456").substring(0, 16).getBytes(); // extending salt to 16 bytes
            KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, ITERATION_COUNT, KEY_STRENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            key = new SecretKeySpec(tmp.getEncoded(), "AES");
            cipher = Cipher.getInstance(AES_CBC_PKCS5_PADDING);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException e) {
            e.printStackTrace();
            Log.e(TAG, "Decriptor Constructor Error. " + e.getMessage());
        }
    }

    Decrypter(SecretKey secretKey) {
        try {
            key = secretKey;
            cipher = Cipher.getInstance(AES_CBC_PKCS7_PADDING);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
            Log.e(TAG, "Decription Error. " + e.getMessage());
        }
    }

    String encrypt(String data) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(IV));
        } catch (InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            Log.e(TAG, "Encription Error. " + e.getMessage());
            return "";
        }

        byte[] utf8EncryptedData;

        try {
            utf8EncryptedData = cipher.doFinal(data.getBytes());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            Log.e(TAG, "Encryption Error. " + e.getMessage());
            return "";
        }

        return Base64.encodeToString(utf8EncryptedData, Base64.DEFAULT);
    }

    String decrypt(String base64EncryptedData) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(IV));
            byte[] decryptedData = Base64.decode(base64EncryptedData, Base64.DEFAULT);
            byte[] utf8 = cipher.doFinal(decryptedData);
            return new String(utf8, "UTF8");
        } catch (Exception e) {
            Log.e(TAG, "Decryption Error. " + e.getMessage());
            return "";
        }
    }

	/*String getIV(){
		return Base64.encodeToString(IV, Base64.DEFAULT);
	}
	void setIV(String base64EncryptedIV){
		IV=Base64.decode(base64EncryptedIV, Base64.DEFAULT);
	}*/

}
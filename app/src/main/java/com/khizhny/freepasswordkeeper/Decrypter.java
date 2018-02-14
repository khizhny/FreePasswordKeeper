package com.khizhny.freepasswordkeeper;

import android.util.Base64;

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

class Decrypter {

		private Cipher cipher;
		private static final int ITERATION_COUNT = 1024;
		private static final int KEY_STRENGTH = 128;  //  Maximum allowed key length
		private SecretKey key;
		private static final  byte[] IV ={3,4,2,4,-55,127,-128, 80,99,40,20,4,34,3,5,2};

		Decrypter(String passPhrase, String login) {
				SecretKeyFactory factory;
				try {
						factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
						byte[] salt = (login+"1234567890123456").substring(0,16).getBytes(); // extending salt to 16 bytes
						KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, ITERATION_COUNT, KEY_STRENGTH);
						SecretKey tmp = factory.generateSecret(spec);
						key = new SecretKeySpec(tmp.getEncoded(), "AES");
						cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
				} catch (InvalidKeySpecException e) {
						e.printStackTrace();
				} catch (NoSuchPaddingException e) {
						e.printStackTrace();
				}

		}

		String encrypt(String data) {
				try {
						cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(IV));
				} catch (InvalidKeyException e) {
						e.printStackTrace();
						return "";
				} catch (InvalidAlgorithmParameterException e) {
						e.printStackTrace();
						return "";
				}
				byte[] utf8EncryptedData;

				try {
						utf8EncryptedData = cipher.doFinal(data.getBytes());
				} catch (IllegalBlockSizeException e) {
						e.printStackTrace();
						return "";
				} catch (BadPaddingException e) {
						e.printStackTrace();
						return "";
				}
				return Base64.encodeToString(utf8EncryptedData, Base64.DEFAULT);
		}

		String decrypt(String base64EncryptedData){
				try {
						cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(IV));
						byte[] decryptedData = Base64.decode(base64EncryptedData, Base64.DEFAULT);
						byte[] utf8 = cipher.doFinal(decryptedData);
						return new String(utf8, "UTF8");
				}catch (Exception e){
						return "";
				}
		}
}
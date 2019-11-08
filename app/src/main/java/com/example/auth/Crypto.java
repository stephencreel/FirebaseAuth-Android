package com.example.auth;

import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    public Crypto() {

    }

    /*******************************************************************************
     *************************** Cryptographic Methods *****************************
     *******************************************************************************/

    /*************************** Symmetric Encryption ******************************/

    // Generates a 128-bit Symmetric Key from Input String using SHA-1
    static SecretKeySpec keyFromString(String password) {
        byte[] key = null;
        try {
            key = (password).getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // use only first 128 bit
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new SecretKeySpec(key, "AES");
    }

    // Generates a Random 128-bit Symmetric Key
    public static String generateSymmetricKey() {
        return saveSymmetricKey(keyFromString(generateRanString(20)));
    }

    public static String saveSymmetricKey(SecretKeySpec key) {
        return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
    }

    public static SecretKeySpec loadSymmetricKey(String key) {
        return new SecretKeySpec(Base64.decode(key, Base64.NO_WRAP), "AES");
    }

    // Symmetrically Encrypts via AES Using 128-bit SHA-1 Hash Generated From Input String
    public static String symmetricEncrypt(String message, SecretKeySpec key)  {
        Cipher cipher;
        byte[] cipherData = null;

        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipherData = cipher.doFinal(message.getBytes());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(cipherData, Base64.NO_WRAP);
    }


    public static String symmetricDecrypt(String message, SecretKeySpec key)  {
        Cipher cipher;
        byte[] cipherData = null;

        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            cipherData = cipher.doFinal(Base64.decode(message, Base64.NO_WRAP));
            return new String(cipherData);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encodeFile(byte[] fileData, SecretKeySpec key) throws Exception
    {

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(fileData);

        return encrypted;
    }

    public static byte[] decodeFile(byte[] fileData, SecretKeySpec key) throws Exception
    {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);

        byte[] decrypted = cipher.doFinal(fileData);

        return decrypted;
    }



    /*************************** Asymmetric Encryption *****************************/

    // Method for Generating a Public/Private Key String Pair
    public static KeyPair generateKeyPair() {
        KeyPairGenerator keys;
        PrivateKey privateKey = null;
        PublicKey publicKey = null;

        try {
            keys = KeyPairGenerator.getInstance("RSA");
            keys.initialize(2048);
            KeyPair pair = keys.generateKeyPair();
            return pair;

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String RSAEncrypt(String plain, PublicKey publicKey) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {

        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(plain.getBytes());
        return bytesToString(encryptedBytes);

    }

    public static String RSADecrypt (String result, PrivateKey privateKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException
    {

        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(stringToBytes(result));
        return new String(decryptedBytes);

    }

    public static String bytesToString(byte[] b) {
        byte[] b2 = new byte[b.length + 1];
        b2[0] = 1;
        System.arraycopy(b, 0, b2, 1, b.length);
        return new BigInteger(b2).toString(36);
    }

    public static byte[] stringToBytes(String s) {
        byte[] b2 = new BigInteger(s, 36).toByteArray();
        return Arrays.copyOfRange(b2, 1, b2.length);
    }

    // Transforms Private Key Object into String
    public static String savePrivateKey(PrivateKey priv) throws GeneralSecurityException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec spec = fact.getKeySpec(priv,
                PKCS8EncodedKeySpec.class);
        byte[] packed = spec.getEncoded();
        String key64 = Base64.encodeToString(packed, Base64.NO_WRAP);

        Arrays.fill(packed, (byte) 0);
        return key64;
    }

    public static String savePublicKey(PublicKey publ) throws GeneralSecurityException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = fact.getKeySpec(publ,
                X509EncodedKeySpec.class);
        return Base64.encodeToString(spec.getEncoded(), Base64.NO_WRAP);
    }

    // Transforms Private Key String into Object
    public static PrivateKey loadPrivateKey(String key64) throws GeneralSecurityException {
        byte[] clear = Base64.decode(key64, Base64.NO_WRAP);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PrivateKey priv = fact.generatePrivate(keySpec);
        Arrays.fill(clear, (byte) 0);
        return priv;
    }

    // Transforms Public Key String into Object
    public static PublicKey loadPublicKey(String stored) throws GeneralSecurityException {
        byte[] data = Base64.decode(stored, Base64.NO_WRAP);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        return fact.generatePublic(spec);
    }

    /********************************** Other *************************************/

    // Generate Random 20-Character String
    public static String generateRanString (Integer size) {
        final String data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append(data.charAt(random.nextInt(data.length())));
        }
        return sb.toString();

    }

    /*********************************** Tests *************************************/

    // Test the Conversion of Symmetric Key from
    public static void testSymmetricConversion(String message) {
        SecretKeySpec testkey = Crypto.keyFromString("test");
        String enc = Crypto.symmetricEncrypt("test", testkey);
        String keystring = Crypto.saveSymmetricKey(testkey);
        SecretKeySpec key = Crypto.loadSymmetricKey(keystring);
        String dec = Crypto.symmetricDecrypt(enc, key);
        if(message.equals(dec)) {
            Log.d("LOG", "Symmetric key string conversion passed.");
        } else {
            Log.d("LOG", "Symmetric key string conversion failed.");
        }
    }

}

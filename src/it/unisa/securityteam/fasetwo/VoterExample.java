/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package it.unisa.securityteam.fasetwo;

import it.unisa.securityteam.utility.SchnorrSig;
import it.unisa.securityteam.utility.*;
import static it.unisa.securityteam.utility.ElGamal.*;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author apaolillo
 */
public class VoterExample{

    /**
     * @param args the command line arguments
     */
    private static final SecureRandom sc = new SecureRandom();

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        SimpleDateFormat date = new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss");
        String Ts = date.format(new Date());
        System.out.println("Current Time Stamp: " + Ts);

        //Primo utente
        ElGamalSK SK1 = Setup(64);
        //Secondo utente
        ElGamalSK SK2 = Setup(64);

        //poi l elettore deve tenere per sè la Sk e rende pubblica la sua Pk
        //L'Ua ha a disposizione la PK dell'utente insieme al suo ID
        //ua mette a dispisizione la sua PK per poter effettuare encrypt msg
        ElGamalSK SKUA = Setup(64);

        //u1 vota
        BigInteger m1 = new BigInteger("1");
        //String msg1 = Ts + ";" + m1;
        //u2 vota
        BigInteger m2 = new BigInteger("1");
        //String msg2 = Ts + ";" + m2;

        byte[] ivBytes = new byte[16]; // create an IV for AES in CBC mode
        sc.nextBytes(ivBytes);
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        BigInteger R1; // set M to a random message in the message space of El Gamal
        R1 = new BigInteger(SKUA.getPK().getSecurityparameter(), sc);
        R1 = R1.mod(SKUA.getPK().getP());

        BigInteger R2; // set M to a random message in the message space of El Gamal
        R2 = new BigInteger(SKUA.getPK().getSecurityparameter(), sc);
        R2 = R2.mod(SKUA.getPK().getP());

        ElGamalCT C1K = ElGamal.Encrypt(SKUA.getPK(), R1);
        ElGamalCT C2K = ElGamal.Encrypt(SKUA.getPK(), R2);

//Cifro m1
        ElGamalCT c1 = EncryptInTheExponent(SKUA.getPK(), m1); // encrypt vote in CT
        //Cifro m2
        ElGamalCT c2 = EncryptInTheExponent(SKUA.getPK(), m2);

        Message mess1 = new Message(Ts, c1, R1);
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        ObjectOutputStream oos1 = new ObjectOutputStream(bos1);
        oos1.writeObject(mess1);
        oos1.flush();
        byte[] input = bos1.toByteArray();
        
        Message mess2 = new Message(Ts, c2, R2);
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(bos2);
        oos2.writeObject(mess2);
        oos2.flush();
        byte[] input2 = bos2.toByteArray();

        try {
            // hash M to a byte array and put the result in keyBytes that will be used to generate a key for AES128
            MessageDigest h = MessageDigest.getInstance("SHA256");
            h.update(R1.toByteArray());
            byte[] keyBytes = h.digest(); // keyBytes=Hash(M)
            SecretKeySpec key1 = new SecretKeySpec(keyBytes, "AES"); // use keyBytes to generate an AES key

            // convert the msg string to a byte array that will be encrypted under CBC-AES
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");  // encrypt array input under the key derived from M at previous steps
            cipher.init(Cipher.ENCRYPT_MODE, key1, ivSpec);
            byte[] cipherText = new byte[cipher.getOutputSize(input.length)];
            int ctLength = cipher.update(input, 0, input.length, cipherText, 0);
            ctLength += cipher.doFinal(cipherText, ctLength);
            // cipherText encrypts the byte array input under the key derived from M

            h.update(R2.toByteArray());
            byte[] keyBytes2 = h.digest(); // keyBytes=Hash(M)
            SecretKeySpec key2 = new SecretKeySpec(keyBytes, "AES"); // use keyBytes to generate an AES key

             // convert the msg string to a byte array that will be encrypted under CBC-AES

            Cipher cipher2 = Cipher.getInstance("AES/CBC/PKCS5Padding");  // encrypt array input under the key derived from M at previous steps
            cipher2.init(Cipher.ENCRYPT_MODE, key2, ivSpec);
            byte[] cipherText2 = new byte[cipher2.getOutputSize(input2.length)];
            int ctLength2 = cipher2.update(input2, 0, input2.length, cipherText2, 0);
            ctLength2 += cipher2.doFinal(cipherText2, ctLength2);
            // cipherText encrypts the byte array input under the key derived from M
             
            SchnorrSig s1 = Sign(SK1, toString(cipherText, ctLength));
            //Firmo utente 2
            SchnorrSig s2 = Sign(SK2, toString(cipherText2, ctLength2));

            //Memorizzo Pkv, C ed S dell'utente. Verifico che la firma sia corretta prendeno la pkv dal db.P
            System.out.println("Verification = " + Verify(SK1.getPK(), s1, toString(cipherText, ctLength)));
            System.out.println("Verification = " + Verify(SK2.getPK(), s2, toString(cipherText2, ctLength2)));

            R1 = ElGamal.Decrypt(C1K, SKUA); // decrypt M 

            h.update(R1.toByteArray()); // derive the AES key from M
            keyBytes = h.digest();
            key1 = new SecretKeySpec(keyBytes, "AES"); // key is Hash(M)

            cipher.init(Cipher.DECRYPT_MODE, key1, ivSpec);
            // decrypt the plaintext using the AES key computed before
            byte[] plainText = new byte[cipher.getOutputSize(ctLength)];
            int ptLength = cipher.update(cipherText, 0, ctLength, plainText, 0);
            ptLength += cipher.doFinal(plainText, ptLength);

            ByteArrayInputStream bis = new ByteArrayInputStream(plainText);
            ObjectInput in = null;
            in = new ObjectInputStream(bis);
            Message o = (Message) in.readObject();
            
            
            /*R2 = ElGamal.Decrypt(C2K, SKUA); // decrypt M 

            h.update(R2.toByteArray()); // derive the AES key from M
            keyBytes2 = h.digest();
            key2 = new SecretKeySpec(keyBytes, "AES"); // key is Hash(M)

            cipher2.init(Cipher.DECRYPT_MODE, key2, ivSpec);
            // decrypt the plaintext using the AES key computed before
            plainText = new byte[cipher2.getOutputSize(ctLength)];
            ptLength = cipher2.update(cipherText, 0, ctLength, plainText, 0);
            ptLength += cipher.doFinal(plainText, ptLength);

            ByteArrayInputStream bis2 = new ByteArrayInputStream(plainText);
            ObjectInput in2 = null;
            in2 = new ObjectInputStream(bis2);
            Message o2 = (Message) in2.readObject();*/
            
           
            
            ElGamalCT CTH = Homomorphism(SKUA.getPK(), o.getX(), o.getX());
            BigInteger D;
            D = DecryptInTheExponent(CTH, SKUA);
            System.out.println("decrypted plaintext with Exponential El Gamal= " + D); // it should be 38*/
 /*
            
            // decryption 
            R1 = ElGamal.Decrypt(C1K, SKUA); // decrypt M 

            h.update(R1.toByteArray()); // derive the AES key from M
            keyBytes = h.digest();
            key1 = new SecretKeySpec(keyBytes, "AES"); // key is Hash(M)

            cipher.init(Cipher.DECRYPT_MODE, key1, ivSpec);
            // decrypt the plaintext using the AES key computed before
            byte[] plainText = new byte[cipher.getOutputSize(ctLength)];
            int ptLength = cipher.update(cipherText, 0, ctLength, plainText, 0);
            ptLength += cipher.doFinal(plainText, ptLength);
            msg1 = toString(plainText, ptLength);
             */
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* MessageDigest hash = MessageDigest.getInstance("SHA-256");
        //hash c1
        hash.update(it.unisa.securityteam.faseone.Utils.toByteArray(c1.toString()));
        byte[] h1 = hash.digest();
        //hash c2
        hash.update(it.unisa.securityteam.faseone.Utils.toByteArray(c2.toString()));
        byte[] h2 = hash.digest();

        String stringHashH1 = Utils.toHex(h1);
        String stringHashH2 = Utils.toHex(h2);
         
        //Firmo utente 1
        SchnorrSig s1 = Sign(SK1, stringHashH1);
        //Firmo utente 2
        SchnorrSig s2 = Sign(SK2, stringHashH2);

        //Memorizzo Pkv, C ed S dell'utente. Verifico che la firma sia corretta prendeno la pkv dal db.P
        System.out.println("Verification = " + Verify(SK1.PK, s1, stringHashH1));
        System.out.println("Verification = " + Verify(SK2.PK, s2, stringHashH2));

        ElGamalCT CTH = Homomorphism(SKUA.PK, c1, c2);
        BigInteger D;
        D = DecryptInTheExponent(CTH, SKUA);
        System.out.println("decrypted plaintext with Exponential El Gamal= " + D); // it should be 38
         */
    }

    public static String toString(
            byte[] bytes,
            int length) {
        char[] chars = new char[length];

        for (int i = 0; i != chars.length; i++) {
            chars[i] = (char) (bytes[i] & 0xff);
        }

        return new String(chars);
    }

}
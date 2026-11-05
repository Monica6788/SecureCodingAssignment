package edu.secure.assign.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 비밀번호 저장/검증 */
public final class PasswordHasher {
	// 해시 함수 반복 계산 횟수
	private static final int ITERATIONS = 600_000;
	// 암호화 키의 길이 지정
	private static final int KEY_BITS = 256;
	// 보안용 난수 생성기
	private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }
    
    private static byte[] derive(String password, byte[] salt) {
    	try {
    		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), 
    										 salt, ITERATIONS, 
    										 KEY_BITS);
    		return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    							   .generateSecret(spec)
    							   .getEncoded();
    	} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
    		throw new IllegalStateException(e);
    	}
    }

    public static String hash(String password) {
    	byte[] salt = new byte[16];
    	RANDOM.nextBytes(salt);
    	return HexFormat.of().formatHex(salt) + ":"
    			+ HexFormat.of().formatHex(derive(password, salt));
    }
    
    public static boolean verify(String password, String stored) {
    	String[] parts = stored.split(":");
    	if (parts.length != 2) {
    		return false;
    	}
    	byte[] salt = HexFormat.of().parseHex(parts[0]);
    	byte[] expected = HexFormat.of().parseHex(parts[1]);
    	
    	return MessageDigest.isEqual(derive(password, salt), expected);
    	
    }
}

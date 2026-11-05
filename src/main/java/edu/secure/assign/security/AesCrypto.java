package edu.secure.assign.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 주민등록번호 등 복호화가 필요한 데이터의 대칭 암호화 */
@Component
public class AesCrypto {

//    private static final String KEY = "0123456789abcdef";
	private static final int IV_BYTES = 12;
	// IV: Initialization Vector, 초기화 벡터
	// 	   똑같은 평문을 여러 번 암호화 하더라도 매번 완전히 다른 암호문이 나오도록 해주는 난수
	private static final int TAG_BITS = 128;
	// TAG: 암호문의 무결성을 검증하기 위한 인증 태그(Auth Tag)
	//  	128비트가 보안/성능 면에서 가장 표준적이고 권장되는 크기
	private final SecureRandom random = new SecureRandom();

    private final String base64Key;

    public AesCrypto(@Value("${assign.aes-key:}") String base64Key) {
        this.base64Key = base64Key;
    }
    
    // KEY를 쓰지 않으므로 새로운 key를 반환하는 메서드 생성
    // SecretKeySpec
    // : 내가 만든 키를 자바 암호화 시스템이 이해할 수 있는 키 객체로 변환해주는 래퍼(Wrapper)
    private SecretKeySpec key() {
    	if (base64Key == null || base64Key.isBlank()) {
    		throw new IllegalStateException("LAB_AES_KEY environment variable is not set.");
    	}
    	// 
    	return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    public String encrypt(String plain) {
        try {
        	byte[] iv = new byte[IV_BYTES];
        	random.nextBytes(iv);			// 새로운 IV를 매번 다시 생성
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
//            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            
//            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
            return Base64.getEncoder()
            			 .encodeToString(ByteBuffer.allocate(iv.length + enc.length)
            					 				   .put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(String token) {
        try {
        	ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(token));
        	byte[] iv = new byte[IV_BYTES];
        	buf.get(iv);
        	byte[] enc = new byte[buf.remaining()];
        	buf.get(enc);
            Cipher cipher = Cipher.getInstance("AES/GCB/NoPadding");
//            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
//            return new String(cipher.doFinal(Base64.getDecoder().decode(token)), StandardCharsets.UTF_8);
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }
}

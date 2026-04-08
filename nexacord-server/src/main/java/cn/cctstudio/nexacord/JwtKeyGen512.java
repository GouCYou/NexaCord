package cn.cctstudio.nexacord;

import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import java.util.Base64;

public class JwtKeyGen512 {
    public static void main(String[] args) {
        SecretKey key = Jwts.SIG.HS512.key().build();
        System.out.println(Base64.getEncoder().encodeToString(key.getEncoded()));
    }
}

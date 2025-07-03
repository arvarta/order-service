//package org.ezon.msa.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
//import org.springframework.session.web.http.CookieSerializer;
//import org.springframework.session.web.http.DefaultCookieSerializer;
//
//@Configuration
//@EnableRedisHttpSession
//public class RedisConfig {
//
//    @Bean
//    public CookieSerializer cookieSerializer() {
//        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
//        serializer.setCookieName("JSESSIONID");
//        serializer.setCookiePath("/");
//        serializer.setDomainNamePattern("^localhost$"); // 로컬 개발환경 설정
//        serializer.setUseHttpOnlyCookie(true);
//        serializer.setSameSite("Lax"); // SameSite 정책
//        return serializer;
//    }
//}

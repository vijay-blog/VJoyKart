package com.nexamart.backend.security;

import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.repository.UserAccountRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;import jakarta.servlet.ServletException;import jakarta.servlet.http.HttpServletRequest;import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.authority.SimpleGrantedAuthority;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;import java.util.List;

@Component public class JwtAuthenticationFilter extends OncePerRequestFilter{
 private final JwtService jwt; private final UserAccountRepository users; public JwtAuthenticationFilter(JwtService jwt,UserAccountRepository users){this.jwt=jwt;this.users=users;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization"); if(h!=null&&h.startsWith("Bearer ")){String t=h.substring(7);if(jwt.validAccess(t)){try{Claims c=jwt.parse(t);Long id=((Number)c.get("uid")).longValue();String role=c.get("role",String.class);var user=users.findById(id).orElse(null);boolean deletionRetry="/api/v1/account".equals(req.getRequestURI())&&user!=null&&user.getStatus()==AccountStatus.DELETED;if(user!=null&&user.getRole().name().equals(role)&&(user.getStatus()==AccountStatus.ACTIVE||deletionRetry)){var auth=new UsernamePasswordAuthenticationToken(id.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));SecurityContextHolder.getContext().setAuthentication(auth);}}catch(Exception ignored){}}}
  chain.doFilter(req,res);
 }
}

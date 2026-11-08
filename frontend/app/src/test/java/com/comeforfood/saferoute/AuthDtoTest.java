package com.comeforfood.saferoute;

import com.comeforfood.saferoute.core.model.AuthDto;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthDtoTest {

    @Test
    public void testLoginRequest() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("test@saferoute.ai", "password123");
        assertEquals("test@saferoute.ai", request.getEmail());
        assertEquals("password123", request.getPassword());
    }

    @Test
    public void testRegisterRequest() {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest("Nguyen Van A", "a@saferoute.ai", "123456");
        assertEquals("Nguyen Van A", request.getFullName());
        assertEquals("a@saferoute.ai", request.getEmail());
        assertEquals("123456", request.getPassword());
    }
}

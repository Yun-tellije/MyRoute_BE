package com.ssafy.ws.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
	private String token;
	private String name;
	private String role;
    private String profileImage;
}

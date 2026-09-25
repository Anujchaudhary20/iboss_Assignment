package com.assingment.iboss.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
}

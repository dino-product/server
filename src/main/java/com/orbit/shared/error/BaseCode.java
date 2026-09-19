package com.orbit.shared.error;

import org.springframework.http.HttpStatus;

public interface BaseCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}

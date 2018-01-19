package com.iorga.cig.bs.FileStorageManager.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequest400Exception extends Exception {
    public BadRequest400Exception(String message) {
        super(message);
    }
}

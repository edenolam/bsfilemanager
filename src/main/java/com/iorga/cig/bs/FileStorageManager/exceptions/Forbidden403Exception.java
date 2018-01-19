package com.iorga.cig.bs.FileStorageManager.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class Forbidden403Exception extends Exception {
    public Forbidden403Exception(String message) {
        super(message);
    }
}

package com.iorga.cig.bs.FileStorageManager.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class Conflict409Exception extends Exception {

    public Conflict409Exception(String message) {
        super(message);
    }
}

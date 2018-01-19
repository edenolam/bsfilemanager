package com.iorga.cig.bs.FileStorageManager.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ServerError500Exception extends Exception {

    public ServerError500Exception(String message, Throwable cause) {
        super(message, cause);
    }
}

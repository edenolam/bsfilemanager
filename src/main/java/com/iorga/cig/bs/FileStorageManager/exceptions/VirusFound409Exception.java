package com.iorga.cig.bs.FileStorageManager.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
// MS indicates to use 409 HTTP status when virus is found https://msdn.microsoft.com/en-us/library/dd907072%28v=office.12%29.aspx?f=255&MSPPError=-2147217396
public class VirusFound409Exception extends Throwable {
    public VirusFound409Exception(String message) {
        super(message);
    }
}

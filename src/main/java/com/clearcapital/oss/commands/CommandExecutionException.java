package com.clearcapital.oss.commands;

public class CommandExecutionException extends Exception {

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandExecutionException(String message) {
        super(message);
    }

    private static final long serialVersionUID = -972212257788997227L;

}

package com.clearcapital.oss.commands;

/**
 * This interface defines an item which needs to be executed at a different time and/or place from which it is
 * generated.
 */
public interface Command {

    String getLocation();

    void execute() throws CommandExecutionException;

}

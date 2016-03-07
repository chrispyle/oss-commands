package com.clearcapital.oss.bundles;

/**
 * This interface defines an item which needs to be executed with other bundles.
 */
public interface Command {

    String getLocation();

    void execute() throws CommandExecutionException;

}

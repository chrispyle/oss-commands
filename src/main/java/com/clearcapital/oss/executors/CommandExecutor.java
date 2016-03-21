package com.clearcapital.oss.executors;

import com.clearcapital.oss.commands.Command;
import com.clearcapital.oss.commands.CommandExecutionException;

/**
 * Interface allowing a command execution strategy to be pluggable.
 * 
 * @author eehlinger
 * 
 */
public interface CommandExecutor extends AutoCloseable {

    /**
     * Add a command to be executed according to the implementing class's execution strategy.
     * 
     * @param command
     *            The command to be executed. null is acceptable; the implementing class must ignore null bundles.
     * 
     * @throws CoreException
     */
    public void addCommand(Command command) throws CommandExecutionException;

    public void execute() throws CommandExecutionException;

    public void close() throws CommandExecutionException;
}
